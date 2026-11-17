package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.AlertLogResponse;
import com.company.module.personalinfo.dto.AlertRunResponse;
import com.company.module.personalinfo.dto.AlertSendRequest;
import com.company.module.personalinfo.entity.AlertSendStatus;
import com.company.module.personalinfo.entity.AlertTriggerType;
import com.company.module.personalinfo.entity.AlertType;
import com.company.module.personalinfo.dto.FollowUpResponse;
import com.company.module.personalinfo.entity.PiInspection;
import com.company.module.personalinfo.entity.ContractFileType;
import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiAlertLog;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.repository.PiAlertLogRepository;
import com.company.module.personalinfo.repository.PiContractFileRepository;
import com.company.module.personalinfo.repository.PiContractRepository;
import com.company.module.personalinfo.repository.PiInspectionRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 계약 만료 알림 메일 서비스.
 *
 * <h2>자동 알림 단계</h2>
 * 계약별 알림 기준일(ALERT_DAYS_BEFORE, 기본 30일)과 D-7, D-1, 당일(D-DAY), 만료 후(EXPIRED) 단계로
 * 각 단계마다 1회만 발송한다. 서버가 며칠 멈췄다가 다시 떠도 그 시점에 해당하는 가장 가까운 단계 1건만 발송된다.
 *
 * <h2>중복 방지</h2>
 * 자동 발송은 {@code DEDUP_KEY = 계약ID:단계:만료일} UNIQUE 제약으로 선점(INSERT)한 뒤 발송한다.
 * WAS 가 여러 대여도 한 대만 선점에 성공한다. 만료일을 바꾸면 키가 달라지므로 새 일정으로 다시 알린다.
 *
 * <h2>메일 서버 미설정</h2>
 * {@code spring.mail.host} 가 없으면 JavaMailSender 빈이 없으므로 발송하지 않고 SKIPPED 로 기록한다.
 * (애플리케이션 기동에는 영향 없음)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class PiAlertMailService {

    static final String STAGE_EXPIRED = "EXPIRED";
    static final String STAGE_DDAY = "D-DAY";
    static final String STAGE_MANUAL = "MANUAL";
    /** 만료 후 이 기간까지만 EXPIRED 알림 대상으로 본다 */
    static final int EXPIRED_LOOKBACK_DAYS = 30;
    private static final int[] FIXED_STAGES = {7, 1};
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final PiContractRepository contractRepository;
    private final PiAlertLogRepository alertLogRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TransactionTemplate newTx;
    private final PiAccessScopeResolver accessScope;
    private final PiExpiryFormService expiryForms;
    private final PiContractFileRepository fileRepository;
    private final PiInspectionRepository inspectionRepository;
    private final PiFollowUpRules followUps;
    private final PiChecklistTemplateService checklistTemplates;

    /** 만료 서류 독촉·조치 지연 알림: 종료(또는 조치 예정일) 후 이 기간까지만 주 1회 보낸다 (오래된 데이터 대량 발송 방지) */
    private final int followUpRemindDays;
    /** 정기 점검 알림 */
    private final boolean regularEnabled;
    private final int regularNoticeMonth;
    private final int regularNoticeDay;
    private final int regularInspectionMonth;
    private final String regularAdminRecipients;

    private final String mailFrom;
    private final String defaultRecipients;
    private final String baseUrl;
    private final String subjectPrefix;

    public PiAlertMailService(PiContractRepository contractRepository,
                              PiAlertLogRepository alertLogRepository,
                              ObjectProvider<JavaMailSender> mailSenderProvider,
                              PlatformTransactionManager transactionManager,
                              PiAccessScopeResolver accessScope,
                              PiExpiryFormService expiryForms,
                              PiContractFileRepository fileRepository,
                              PiInspectionRepository inspectionRepository,
                              PiFollowUpRules followUps,
                              PiChecklistTemplateService checklistTemplates,
                              @Value("${personalinfo.alert.follow-up-remind-days:90}") int followUpRemindDays,
                              @Value("${personalinfo.regular-inspection.enabled:true}") boolean regularEnabled,
                              @Value("${personalinfo.regular-inspection.notice-date:04-10}") String regularNoticeDate,
                              @Value("${personalinfo.regular-inspection.month:5}") int regularInspectionMonth,
                              @Value("${personalinfo.regular-inspection.admin-recipients:}") String regularAdminRecipients,
                              @Value("${personalinfo.alert.mail-from:${spring.mail.username:}}") String mailFrom,
                              @Value("${personalinfo.alert.default-recipients:}") String defaultRecipients,
                              @Value("${personalinfo.alert.base-url:}") String baseUrl,
                              @Value("${personalinfo.alert.subject-prefix:[개인정보 위수탁]}") String subjectPrefix) {
        this.contractRepository = contractRepository;
        this.alertLogRepository = alertLogRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.accessScope = accessScope;
        this.expiryForms = expiryForms;
        this.fileRepository = fileRepository;
        this.inspectionRepository = inspectionRepository;
        this.followUps = followUps;
        this.checklistTemplates = checklistTemplates;
        this.followUpRemindDays = Math.max(followUpRemindDays, 1);
        this.regularEnabled = regularEnabled;
        String[] md = (regularNoticeDate == null || !regularNoticeDate.matches("\\d{1,2}-\\d{1,2}") ? "04-10" : regularNoticeDate).split("-");
        this.regularNoticeMonth = Integer.parseInt(md[0]);
        this.regularNoticeDay = Integer.parseInt(md[1]);
        this.regularInspectionMonth = Math.min(Math.max(regularInspectionMonth, 1), 12);
        this.regularAdminRecipients = regularAdminRecipients;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.mailFrom = mailFrom;
        this.defaultRecipients = defaultRecipients;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.subjectPrefix = subjectPrefix;
    }

    // ─────────────────────────────────────────────────────────────
    //  자동 알림 (스케줄러 / 관리자 수동 실행)
    // ─────────────────────────────────────────────────────────────

    /** 기준일에 해당하는 만료 알림을 일괄 발송한다. 메일 발송 중 DB 트랜잭션을 잡지 않는다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AlertRunResponse runDailyAlerts(LocalDate baseDate) {
        LocalDate today = baseDate == null ? LocalDate.now() : baseDate;
        List<PiContract> candidates = contractRepository.findAlertCandidates(
                today.minusDays(EXPIRED_LOOKBACK_DAYS), today.plusDays(365));
        int sent = 0, failed = 0, skipped = 0, already = 0, considered = 0;
        for (PiContract c : candidates) {
            Optional<String> stage = stageOf(c, today);
            if (stage.isEmpty()) {
                continue;
            }
            considered++;
            String dedupKey = c.getContractId() + ":" + stage.get() + ":" + c.getContractEndDate();
            Optional<Long> claimed = claim(c, stage.get(), dedupKey);
            if (claimed.isEmpty()) {
                already++;
                continue;
            }
            AlertSendStatus status = deliver(claimed.get(), c, stage.get(), null, today);
            switch (status) {
                case SENT -> sent++;
                case FAILED -> failed++;
                default -> skipped++;
            }
        }
        log.info("[personalinfo] 만료 알림 실행 baseDate={} 대상={} 발송={} 실패={} 미발송={} 기발송={}",
                today, considered, sent, failed, skipped, already);
        // 보안팀 확인 필요 알림 (각각 실패해도 다른 알림은 계속)
        int[] docs = safeRun("만료 서류 독촉", () -> runExpiryDocsReminders(today));
        int[] overdue = safeRun("조치 지연", () -> runActionOverdueAlerts(today));
        int[] regular = safeRun("정기 점검", () -> runRegularInspectionAlerts(today));
        int[] renew = safeRun("자동연장 확인", () -> runAutoRenewNotices(today));
        return AlertRunResponse.builder()
                .baseDate(today).candidateCount(considered)
                .sentCount(sent).failedCount(failed).skippedCount(skipped).alreadySentCount(already)
                .docsReminderCount(docs[0]).actionOverdueCount(overdue[0])
                .regularInspectionCount(regular[0]).regularSummaryCount(regular[1])
                .autoRenewNoticeCount(renew[0])
                .build();
    }

    /**
     * 계약의 현재 알림 단계. 남은 일수가 통과한 기준 중 가장 가까운(작은) 기준을 단계로 쓴다.
     * 예) 알림 기준 30일: 남은 20일 → D-30, 남은 5일 → D-7, 남은 1일 → D-1, 당일 → D-DAY, 지남 → EXPIRED
     */
    static Optional<String> stageOf(PiContract c, LocalDate today) {
        if (c.getContractEndDate() == null || c.isAutoRenewing()) {
            return Optional.empty();
        }
        long daysLeft = ChronoUnit.DAYS.between(today, c.getContractEndDate());
        if (daysLeft < 0) {
            return daysLeft >= -EXPIRED_LOOKBACK_DAYS ? Optional.of(STAGE_EXPIRED) : Optional.empty();
        }
        if (daysLeft == 0) {
            return Optional.of(STAGE_DDAY);
        }
        int first = c.getAlertDaysBefore() == null ? PiContract.DEFAULT_ALERT_DAYS : c.getAlertDaysBefore();
        TreeSet<Integer> thresholds = new TreeSet<>();
        thresholds.add(first);
        for (int s : FIXED_STAGES) {
            if (s <= first) {
                thresholds.add(s);
            }
        }
        Integer stage = thresholds.ceiling((int) daysLeft);
        return stage == null ? Optional.empty() : Optional.of("D-" + stage);
    }

    /** 발송 이력을 선점한다. 이미 발송했거나 다른 WAS 가 선점했으면 empty. 실패 건은 최대 재시도 횟수까지 다시 선점 */
    private Optional<Long> claim(PiContract c, String stage, String dedupKey) {
        try {
            return newTx.execute(status -> {
                Optional<PiAlertLog> existing = alertLogRepository.findByDedupKey(dedupKey);
                if (existing.isPresent()) {
                    PiAlertLog logRow = existing.get();
                    if (!logRow.canRetry()) {
                        return Optional.<Long>empty();
                    }
                    logRow.prepareRetry(String.join(", ", recipientsOf(c, null)), subjectOf(c, stage));
                    return Optional.of(logRow.getAlertId());
                }
                PiAlertLog saved = alertLogRepository.saveAndFlush(PiAlertLog.builder()
                        .contractId(c.getContractId())
                        .triggerType(AlertTriggerType.AUTO)
                        .alertStage(stage)
                        .targetEndDate(c.getContractEndDate())
                        .dedupKey(dedupKey)
                        .recipients(String.join(", ", recipientsOf(c, null)))
                        .subject(subjectOf(c, stage))
                        .build());
                return Optional.of(saved.getAlertId());
            });
        } catch (DataIntegrityViolationException e) {
            // 다른 WAS 가 같은 키를 먼저 선점함
            return Optional.empty();
        }
    }

    private int[] safeRun(String name, java.util.function.Supplier<int[]> job) {
        try {
            return job.get();
        } catch (RuntimeException e) {
            log.error("[personalinfo] {} 알림 실행 실패", name, e);
            return new int[]{0, 0};
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  1) 만료 서류 독촉 (계약 종료 후 미수취 서류가 있으면 주 1회)
    // ─────────────────────────────────────────────────────────────

    /**
     * 종료 기준일(종료 처리일, 없으면 원계약 종료일)이 지났는데 만료 서류(만료 점검 체크리스트·확약서·파기 확인서)를
     * 다 받지 못한 계약에 주 1회 독촉 메일을 보낸다. 종료 기준일 후 {@code follow-up-remind-days}(기본 90일)까지만 보내며,
     * 계약의 독촉 발송 여부(EXPIRY_DOCS_REMIND_YN)가 N 이면 보내지 않는다. 받지 못한 서류의 양식만 첨부한다.
     * @return [발송 건수]
     */
    int[] runExpiryDocsReminders(LocalDate today) {
        List<PiContract> targets = contractRepository.findByDeletedYnOrderByContractIdAsc("N").stream()
                .filter(c -> !"N".equals(c.getExpiryDocsRemindYn()))
                .filter(c -> !c.isAutoRenewing())
                .filter(c -> {
                    LocalDate base = PiFollowUpRules.endBase(c);
                    return base != null && base.isBefore(today) && !base.plusDays(followUpRemindDays).isBefore(today);
                }).toList();
        Map<Long, FollowUpResponse> fu = followUps.of(targets, today, false);
        String week = weekKey(today);
        int sent = 0;
        for (PiContract c : targets) {
            FollowUpResponse f = fu.get(c.getContractId());
            if (f == null || f.getMissingDocs().isEmpty()) {
                continue;
            }
            Set<String> recipients = recipientsOf(c, null);
            String subject = subjectPrefix + " 만료 서류 미수취 독촉 - " + c.getVendorName()
                    + (StringUtils.hasText(c.getContractName()) ? " / " + c.getContractName() : "")
                    + " (" + f.getMissingDocs().size() + "종 미수취)";
            Optional<Long> id = claimOther(c.getContractId(), AlertType.EXPIRY_DOCS, "DOCS-" + week, PiFollowUpRules.endBase(c),
                    "DOCS:" + c.getContractId() + ":" + week, recipients, subject);
            if (id.isEmpty()) {
                continue;
            }
            List<PiFileStorage.DownloadFile> attachments = expiryForms.mailAttachments(c).stream()
                    .filter(a -> f.getMissingDocs().stream().anyMatch(m -> a.fileName().startsWith(missingFormPrefix(m))))
                    .toList();
            if (sendAndRecord(id.get(), recipients, subject, docsReminderBody(c, f, today, attachments), attachments,
                    attachments.isEmpty() ? null : expiryForms.versionSummary()) == AlertSendStatus.SENT) {
                sent++;
            }
        }
        return new int[]{sent, 0};
    }

    /** 미수취 서류 이름 → 첨부 양식 파일명 앞부분 */
    private static String missingFormPrefix(String missingDoc) {
        return switch (missingDoc) {
            case PiFollowUpRules.DOC_CHECKLIST -> "계약만료시 점검 체크리스트";
            case PiFollowUpRules.DOC_PLEDGE -> "정보보호 및 비밀유지 확약서";
            default -> "개인정보 파기 확인서";
        };
    }

    private String docsReminderBody(PiContract c, FollowUpResponse f, LocalDate today, List<PiFileStorage.DownloadFile> attachments) {
        LocalDate base = PiFollowUpRules.endBase(c);
        StringBuilder sb = mailHead("계약 만료 서류 미수취 안내");
        sb.append("<p>아래 개인정보 처리 위탁 계약이 <b>").append(fmt(base)).append("</b>에 종료(만료)되었으나, 협력사로부터 받아야 할 서류 중 <b style=\"color:#b91c1c\">")
          .append(f.getMissingDocs().size()).append("종</b>을 아직 받지 못했습니다. 협력사에 요청해 받은 뒤 시스템 계약 상세의 <b>[계약만료시 점검]</b>에 올려 주세요.</p>");
        List<String[]> info = new ArrayList<>(List.of(
                new String[]{"수탁업체명", c.getVendorName()},
                new String[]{"계약명", StringUtils.hasText(c.getContractName()) ? c.getContractName() : nz(c.getServiceName())},
                new String[]{"담당부서 / 담당자", nz(c.getDeptName()) + " / " + nz(c.getManagerName())},
                new String[]{"종료(만료)일", fmt(base) + " (" + ChronoUnit.DAYS.between(base, today) + "일 경과)"},
                new String[]{"미수취 서류", String.join(", ", f.getMissingDocs())}));
        if (f.getMissingDocs().contains(PiFollowUpRules.DOC_DESTRUCTION) && f.getConfirmReplyDeadline() != null) {
            info.add(new String[]{"파기 기한 / 확인서 회신 기한",
                    fmt(f.getDestructionDeadline()) + " (종료 + " + f.getDestructionDays() + "일) / "
                            + fmt(f.getConfirmReplyDeadline()) + " (종료 + " + f.getConfirmReplyDays() + "일)"
                            + (f.isConfirmReplyOverdue() ? " — 회신 기한 지남" : "")});
        }
        if (StringUtils.hasText(c.getExpiryDocsNote())) {
            info.add(new String[]{"미수취 사유", c.getExpiryDocsNote()});
        }
        sb.append(infoTable(info));
        if (!attachments.isEmpty()) {
            sb.append("<p>받지 못한 서류의 양식 ").append(attachments.size()).append("종을 첨부했습니다(계약 정보가 미리 채워져 있음).</p>");
        }
        sb.append("<p style=\"color:#6b7280;font-size:12px\">이 안내는 서류를 모두 받을 때까지 주 1회, 종료일로부터 ").append(followUpRemindDays)
          .append("일까지 발송됩니다. 받을 수 없는 사유가 있으면 계약 상세에서 독촉 메일을 끌 수 있습니다.</p>");
        return mailTail(sb, c.getContractId());
    }

    // ─────────────────────────────────────────────────────────────
    //  2) 조치 지연 (점검 조치 예정일이 지났는데 조치 미완료면 주 1회)
    // ─────────────────────────────────────────────────────────────

    /** @return [발송 건수] */
    int[] runActionOverdueAlerts(LocalDate today) {
        List<PiInspection> overdue = inspectionRepository
                .findByResultAndActionCompletedYnAndActionDueDateBeforeAndDeletedYn(
                        com.company.module.personalinfo.entity.InspectionResult.NEED_ACTION, "N", today, "N").stream()
                .filter(i -> !i.getActionDueDate().plusDays(followUpRemindDays).isBefore(today))
                .toList();
        Map<Long, List<PiInspection>> byContract = overdue.stream().collect(Collectors.groupingBy(PiInspection::getContractId));
        String week = weekKey(today);
        int sent = 0;
        for (Map.Entry<Long, List<PiInspection>> e : byContract.entrySet()) {
            PiContract c = contractRepository.findByContractIdAndDeletedYn(e.getKey(), "N").orElse(null);
            if (c == null || "Y".equals(c.getTerminatedYn())) {
                continue;
            }
            Set<String> recipients = recipientsOf(c, null);
            String subject = subjectPrefix + " 점검 조치 기한 지남 - " + c.getVendorName()
                    + (StringUtils.hasText(c.getContractName()) ? " / " + c.getContractName() : "");
            Optional<Long> id = claimOther(c.getContractId(), AlertType.ACTION_OVERDUE, "OVERDUE-" + week, null,
                    "OVERDUE:" + c.getContractId() + ":" + week, recipients, subject);
            if (id.isEmpty()) {
                continue;
            }
            if (sendAndRecord(id.get(), recipients, subject, overdueBody(c, e.getValue(), today), List.of(), null) == AlertSendStatus.SENT) {
                sent++;
            }
        }
        return new int[]{sent, 0};
    }

    private String overdueBody(PiContract c, List<PiInspection> list, LocalDate today) {
        StringBuilder sb = mailHead("위수탁 점검 조치 기한 지남 안내");
        sb.append("<p>아래 협력사 점검에서 <b>조치 필요(N)</b> 항목의 조치 예정일이 지났지만 아직 <b>조치 완료</b>로 처리되지 않았습니다. ")
          .append("협력사의 조치 결과를 확인한 뒤 시스템 점검 상세에서 조치 완료로 바꾸거나 조치 예정일을 다시 정해 주세요.</p>");
        sb.append(infoTable(List.of(
                new String[]{"수탁업체명", c.getVendorName()},
                new String[]{"계약명", StringUtils.hasText(c.getContractName()) ? c.getContractName() : nz(c.getServiceName())},
                new String[]{"담당부서 / 담당자", nz(c.getDeptName()) + " / " + nz(c.getManagerName())})));
        sb.append("<table style=\"border-collapse:collapse;min-width:480px;margin-top:10px\"><tr>");
        for (String h : new String[]{"점검일", "구분", "N 항목", "조치 예정일", "지난 일수"}) {
            sb.append("<th style=\"text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f8fafc\">").append(h).append("</th>");
        }
        sb.append("</tr>");
        for (PiInspection i : list) {
            sb.append("<tr>").append(td(fmt(i.getInspectionDate()))).append(td(i.getInspectionType().getLabel()))
              .append(td(i.getNCount() + "건")).append(td(fmt(i.getActionDueDate())))
              .append("<td style=\"padding:6px 10px;border:1px solid #ddd;color:#b91c1c;font-weight:700\">")
              .append(ChronoUnit.DAYS.between(i.getActionDueDate(), today)).append("일</td></tr>");
        }
        sb.append("</table>");
        sb.append("<p style=\"color:#6b7280;font-size:12px\">이 안내는 조치 완료로 바뀔 때까지 주 1회, 조치 예정일로부터 ").append(followUpRemindDays).append("일까지 발송됩니다.</p>");
        return mailTail(sb, c.getContractId());
    }

    // ─────────────────────────────────────────────────────────────
    //  3) 정기 점검 요청 (매년 4월 10일경, 5월 정기 점검 대상)
    // ─────────────────────────────────────────────────────────────

    /**
     * 알림일(기본 4월 10일)부터 점검월(기본 5월) 말일까지, 그해 정기 점검을 아직 하지 않은 계약의 담당자에게
     * 점검 요청 메일(계약 정보를 채운 체크리스트 양식 첨부)을 1회 보내고, 관리자(보안팀 기본 수신자 + 정기 점검 관리자 수신자)에게
     * 대상 목록 요약 메일을 1회 보낸다. 서버가 알림일에 멈춰 있었으면 그 뒤 처음 실행될 때 보낸다.
     * 대상: 미삭제·미종료, 원계약 시작일 ≤ 점검월 말일, 원계약 종료일이 없거나 ≥ 점검월 1일, 그해 정기 점검 없음.
     * @return [담당자 발송 건수, 관리자 요약 발송 건수]
     */
    int[] runRegularInspectionAlerts(LocalDate today) {
        if (!regularEnabled) {
            return new int[]{0, 0};
        }
        int year = today.getYear();
        LocalDate notice = LocalDate.of(year, regularNoticeMonth, Math.min(regularNoticeDay, 28));
        notice = LocalDate.of(year, regularNoticeMonth, Math.min(regularNoticeDay, notice.lengthOfMonth()));
        LocalDate monthStart = LocalDate.of(year, regularInspectionMonth, 1);
        LocalDate deadline = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        if (today.isBefore(notice) || today.isAfter(deadline)) {
            return new int[]{0, 0};
        }
        Set<Long> inspected = inspectionRepository.findByInspectionYearAndDeletedYn(year, "N").stream()
                .filter(i -> i.getInspectionType() == InspectionType.REGULAR)
                .map(PiInspection::getContractId).collect(Collectors.toSet());
        List<PiContract> targets = contractRepository.findByDeletedYnOrderByContractIdAsc("N").stream()
                .filter(c -> !"Y".equals(c.getTerminatedYn()))
                .filter(c -> c.getContractStartDate() == null || !c.getContractStartDate().isAfter(deadline))
                .filter(c -> c.getContractEndDate() == null || c.isAutoRenewing() || !c.getContractEndDate().isBefore(monthStart))
                .filter(c -> !inspected.contains(c.getContractId()))
                .toList();
        int sent = 0;
        List<String[]> rows = new ArrayList<>();
        for (PiContract c : targets) {
            Set<String> recipients = new LinkedHashSet<>();
            addEmails(recipients, c.getManagerEmail());
            addEmails(recipients, c.getAlertExtraEmails());
            String subject = subjectPrefix + " " + year + "년 정기 점검 요청 - " + c.getVendorName()
                    + (StringUtils.hasText(c.getContractName()) ? " / " + c.getContractName() : "")
                    + " (" + regularInspectionMonth + "월 " + deadline.getDayOfMonth() + "일까지)";
            rows.add(new String[]{c.getVendorName(), StringUtils.hasText(c.getContractName()) ? c.getContractName() : nz(c.getServiceName()),
                    nz(c.getDeptName()) + " / " + nz(c.getManagerName()),
                    recipients.isEmpty() ? "담당자 이메일 없음" : String.join(", ", recipients),
                    c.getLastInspectionDate() == null ? "-" : fmt(c.getLastInspectionDate())});
            Optional<Long> id = claimOther(c.getContractId(), AlertType.REGULAR_INSPECTION, "REGULAR-" + year, c.getContractEndDate(),
                    "REGULAR:" + c.getContractId() + ":" + year, recipients, subject);
            if (id.isEmpty()) {
                continue;
            }
            List<PiFileStorage.DownloadFile> attachments = new ArrayList<>();
            try {
                attachments.add(checklistTemplates.templateFor(c));
            } catch (RuntimeException ex) {
                log.warn("[personalinfo] 정기 점검 체크리스트 양식 생성 실패 contractId={}", c.getContractId(), ex);
            }
            if (sendAndRecord(id.get(), recipients, subject, regularBody(c, year, deadline), attachments, null) == AlertSendStatus.SENT) {
                sent++;
            }
        }
        // 관리자 요약 (대상이 없어도 '대상 없음'으로 1회)
        Set<String> admins = new LinkedHashSet<>();
        addEmails(admins, defaultRecipients);
        addEmails(admins, regularAdminRecipients);
        String subject = subjectPrefix + " " + year + "년 정기 점검 대상 " + targets.size() + "건 (" + regularInspectionMonth + "월 점검)";
        Optional<Long> sid = claimOther(null, AlertType.REGULAR_SUMMARY, "REGULAR-" + year, null,
                "REGULAR_SUMMARY:" + year, admins, subject);
        int summary = 0;
        if (sid.isPresent() && sendAndRecord(sid.get(), admins, subject, regularSummaryBody(year, deadline, rows), List.of(), null)
                == AlertSendStatus.SENT) {
            summary = 1;
        }
        log.info("[personalinfo] 정기 점검 알림 year={} 대상={} 담당자 발송={} 요약={}", year, targets.size(), sent, summary);
        return new int[]{sent, summary};
    }

    // ─────────────────────────────────────────────────────────────
    //  4) 자동연장 예정 확인 (연장일 N일 전, 연장 주기마다 1회)
    // ─────────────────────────────────────────────────────────────

    /**
     * 자동연장 중인 계약(자동연장 Y + 미종료 + 만료 알림 사용)의 현재 계약 종료일이 만료 알림 기준일(기본 30일) 안에 들어오면
     * 담당자(+ 추가 수신자 + 보안팀)에게 "자동연장 예정 — 계속 유지하는지 확인" 메일을 보낸다.
     * 중복 방지 키 = RENEW:계약ID:현재 종료일 → 연장 주기마다 1회. 자동연장 계약은 만료 알림 대상이 아니므로 이 메일이 유일한 확인 창구다.
     * @return [발송 건수]
     */
    int[] runAutoRenewNotices(LocalDate today) {
        int sent = 0;
        for (PiContract c : contractRepository.findByDeletedYnOrderByContractIdAsc("N")) {
            if (!c.isAutoRenewing() || !"Y".equals(c.getAlertYn())) {
                continue;
            }
            LocalDate end = c.currentEndDate(today);
            int alertDays = c.getAlertDaysBefore() == null ? PiContract.DEFAULT_ALERT_DAYS : c.getAlertDaysBefore();
            long left = ChronoUnit.DAYS.between(today, end);
            if (left < 0 || left > alertDays) {
                continue;
            }
            LocalDate next = c.getContractEndDate().plusMonths((long) (c.autoRenewCount(today) + 1) * c.autoRenewMonths());
            Set<String> recipients = recipientsOf(c, null);
            String subject = subjectPrefix + " 자동연장 예정 확인 - " + c.getVendorName()
                    + (StringUtils.hasText(c.getContractName()) ? " / " + c.getContractName() : "")
                    + " (" + fmt(end) + " 이후 " + PiContract.renewPeriodLabel(c.autoRenewMonths()) + " 연장)";
            Optional<Long> id = claimOther(c.getContractId(), AlertType.AUTO_RENEW_NOTICE, "RENEW-" + end, end,
                    "RENEW:" + c.getContractId() + ":" + end, recipients, subject);
            if (id.isEmpty()) {
                continue;
            }
            if (sendAndRecord(id.get(), recipients, subject, autoRenewBody(c, today, end, next, left), List.of(), null) == AlertSendStatus.SENT) {
                sent++;
            }
        }
        return new int[]{sent, 0};
    }

    private String autoRenewBody(PiContract c, LocalDate today, LocalDate end, LocalDate next, long left) {
        String unit = PiContract.renewPeriodLabel(c.autoRenewMonths());
        StringBuilder sb = mailHead("위수탁 계약 자동연장 예정 안내");
        sb.append("<p>아래 개인정보 처리 위탁 계약은 자동연장 조건에 따라 <b>").append(fmt(end)).append("</b> 이후 추가 계약 없이 <b>")
          .append(esc(unit)).append("</b> 연장되어 <b>").append(fmt(next)).append("</b>까지 이어집니다. ")
          .append("<b>계약을 계속 유지하는지 확인</b>해 주세요.</p>");
        sb.append(infoTable(List.of(
                new String[]{"수탁업체명", c.getVendorName()},
                new String[]{"계약명", StringUtils.hasText(c.getContractName()) ? c.getContractName() : nz(c.getServiceName())},
                new String[]{"위탁업무 내용", c.getEntrustedTask()},
                new String[]{"담당부서 / 담당자", nz(c.getDeptName()) + " / " + nz(c.getManagerName())},
                new String[]{"원계약 기간", fmt(c.getContractStartDate()) + " ~ " + fmt(c.getContractEndDate())},
                new String[]{"자동연장", unit + " 단위" + (c.autoRenewCount(today) > 0 ? " (지금까지 " + c.autoRenewCount(today) + "회 연장)" : "")
                        + (StringUtils.hasText(c.getAutoRenewNote()) ? " · " + c.getAutoRenewNote() : "")},
                new String[]{"현재 계약 종료일", fmt(end) + (left == 0 ? " (오늘)" : " (" + left + "일 남음)")},
                new String[]{"연장 후 종료일", fmt(next)})));
        sb.append("<ul style=\"margin-top:12px;padding-left:20px\">")
          .append("<li><b>계속 유지</b>: 별도 조치가 필요 없습니다. 연장 후에도 정기 점검 대상으로 관리됩니다.</li>")
          .append("<li><b>해지·종료</b>: 협력사와 해지를 확정한 뒤 시스템 계약 상세에서 <b>[계약 종료 처리]</b>를 하고, ")
          .append("계약 만료 시 서류 3종(만료 점검 체크리스트, 정보보호 및 비밀유지 확약서, 개인정보 파기 확인서)을 받아 올려 주세요.</li>")
          .append("<li><b>조건 변경</b>: 자동연장 해제·연장 기간 변경은 계약 상세의 <b>[수정]</b>에서 고쳐 주세요.</li></ul>");
        sb.append("<p style=\"color:#6b7280;font-size:12px\">이 안내는 연장일 ").append(c.getAlertDaysBefore() == null ? PiContract.DEFAULT_ALERT_DAYS : c.getAlertDaysBefore())
          .append("일 전(계약의 만료 알림 기준일)에 연장 주기마다 1회 발송됩니다. 만료 알림을 사용하지 않는 계약에는 보내지 않습니다.</p>");
        return mailTail(sb, c.getContractId());
    }

    private String regularBody(PiContract c, int year, LocalDate deadline) {
        StringBuilder sb = mailHead(year + "년 개인정보 위수탁 정기 점검 요청");
        sb.append("<p>").append(year).append("년 개인정보 처리 위탁 협력사 <b>정기 점검(").append(regularInspectionMonth).append("월)</b> 대상입니다. ")
          .append("첨부한 <b>위수탁 점검 체크리스트</b>(계약 정보가 미리 채워져 있음)를 협력사에 전달해 '1. 관리체계'와 '2. 개인정보처리시스템' 시트를 작성받은 뒤, ")
          .append("<b>").append(fmt(deadline)).append("까지</b> 시스템 계약 상세의 [작성된 체크리스트 업로드]로 올려 주세요.</p>");
        sb.append(infoTable(List.of(
                new String[]{"수탁업체명", c.getVendorName()},
                new String[]{"계약명", StringUtils.hasText(c.getContractName()) ? c.getContractName() : nz(c.getServiceName())},
                new String[]{"위탁업무 내용", c.getEntrustedTask()},
                new String[]{"담당부서 / 담당자", nz(c.getDeptName()) + " / " + nz(c.getManagerName())},
                new String[]{"계약기간", fmt(c.getContractStartDate()) + " ~ " + fmt(c.getContractEndDate())},
                new String[]{"최근 점검일", fmt(c.getLastInspectionDate())},
                new String[]{"점검 기한", fmt(deadline)})));
        return mailTail(sb, c.getContractId());
    }

    private String regularSummaryBody(int year, LocalDate deadline, List<String[]> rows) {
        StringBuilder sb = mailHead(year + "년 개인정보 위수탁 정기 점검 대상 (관리자)");
        sb.append("<p>").append(year).append("년 정기 점검(").append(regularInspectionMonth).append("월, 기한 ").append(fmt(deadline))
          .append(") 대상 계약 <b>").append(rows.size()).append("건</b>의 담당자에게 점검 요청 메일을 보냈습니다. ")
          .append("이미 올해 정기 점검을 올린 계약과 종료된 계약은 제외했습니다.</p>");
        if (rows.isEmpty()) {
            sb.append("<p><b>점검 대상이 없습니다.</b></p>");
        } else {
            sb.append("<table style=\"border-collapse:collapse;min-width:640px\"><tr>");
            for (String h : new String[]{"No", "수탁업체명", "계약명", "담당부서 / 담당자", "발송 대상", "최근 점검일"}) {
                sb.append("<th style=\"text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f8fafc\">").append(h).append("</th>");
            }
            sb.append("</tr>");
            int n = 1;
            for (String[] r : rows) {
                sb.append("<tr>").append(td(String.valueOf(n++)));
                for (String v : r) {
                    sb.append("담당자 이메일 없음".equals(v)
                            ? "<td style=\"padding:6px 10px;border:1px solid #ddd;color:#b91c1c;font-weight:700\">" + esc(v) + "</td>"
                            : td(v));
                }
                sb.append("</tr>");
            }
            sb.append("</table>");
            sb.append("<p style=\"color:#6b7280;font-size:12px\">'담당자 이메일 없음' 계약은 메일을 보내지 못했습니다. 계약 정보에 담당자 이메일을 넣어 주세요.</p>");
        }
        if (StringUtils.hasText(baseUrl)) {
            sb.append("<p><a href=\"").append(esc(baseUrl + "/personalinfo/index.html#/contracts")).append("\">시스템에서 계약 목록 보기</a></p>");
        }
        sb.append("<p style=\"color:#6b7280;font-size:12px;margin-top:16px\">본 메일은 개인정보 위수탁 협력사 관리 시스템에서 자동 발송되었습니다.</p></div>");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    //  공통: 이력 선점 / 발송 / 본문
    // ─────────────────────────────────────────────────────────────

    /** ISO 주차 키 (예: 2027W15) — 주 1회 발송 중복 방지 */
    static String weekKey(LocalDate d) {
        return d.get(IsoFields.WEEK_BASED_YEAR) + "W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** 만료 알림 외 알림의 발송 이력 선점 (DEDUP_KEY UNIQUE). 이미 보냈거나 다른 WAS 가 선점했으면 empty */
    private Optional<Long> claimOther(Long contractId, AlertType type, String stage, LocalDate targetDate, String dedupKey,
                                      Set<String> recipients, String subject) {
        try {
            return newTx.execute(status -> {
                Optional<PiAlertLog> existing = alertLogRepository.findByDedupKey(dedupKey);
                if (existing.isPresent()) {
                    PiAlertLog row = existing.get();
                    if (!row.canRetry()) {
                        return Optional.<Long>empty();
                    }
                    row.prepareRetry(String.join(", ", recipients), subject);
                    return Optional.of(row.getAlertId());
                }
                return Optional.of(alertLogRepository.saveAndFlush(PiAlertLog.builder()
                        .contractId(contractId)
                        .alertType(type)
                        .triggerType(AlertTriggerType.AUTO)
                        .alertStage(stage.length() > 20 ? stage.substring(0, 20) : stage)
                        .targetEndDate(targetDate)
                        .dedupKey(dedupKey)
                        .recipients(String.join(", ", recipients))
                        .subject(subject)
                        .build()).getAlertId());
            });
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    /** 메일 발송 후 이력 상태 기록 (메일 발송 중 DB 트랜잭션을 잡지 않음) */
    private AlertSendStatus sendAndRecord(Long alertId, Set<String> recipients, String subject, String html,
                                          List<PiFileStorage.DownloadFile> attachments, String formVersions) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        AlertSendStatus result;
        String error = null;
        if (recipients.isEmpty()) {
            result = AlertSendStatus.SKIPPED;
            error = "수신자 없음 (담당자 이메일/추가 수신자/기본 수신자 미설정)";
        } else if (sender == null) {
            result = AlertSendStatus.SKIPPED;
            error = "메일 서버 미설정 (spring.mail.host)";
        } else {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, !attachments.isEmpty(), StandardCharsets.UTF_8.name());
                helper.setEncodeFilenames(true);
                if (StringUtils.hasText(mailFrom)) {
                    helper.setFrom(mailFrom);
                }
                helper.setTo(recipients.toArray(String[]::new));
                helper.setSubject(subject);
                helper.setText(html, true);
                for (PiFileStorage.DownloadFile f : attachments) {
                    helper.addAttachment(f.fileName(), new ByteArrayResource(f.data()), f.contentType());
                }
                sender.send(message);
                result = AlertSendStatus.SENT;
            } catch (Exception e) {
                log.warn("[personalinfo] 알림 메일 발송 실패 alertId={}", alertId, e);
                result = AlertSendStatus.FAILED;
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }
        final AlertSendStatus r = result;
        final String err = error;
        newTx.executeWithoutResult(status -> alertLogRepository.findById(alertId).ifPresent(row -> {
            row.recordFormVersions(attachments.isEmpty() ? null : formVersions);
            if (r == AlertSendStatus.SENT) {
                row.markSent();
            } else if (r == AlertSendStatus.FAILED) {
                row.markFailed(err);
            } else {
                row.markSkipped(err);
            }
        }));
        return result;
    }

    private static StringBuilder mailHead(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:'Malgun Gothic',sans-serif;font-size:14px;color:#111\">");
        sb.append("<h3 style=\"margin:0 0 12px\">").append(esc(title)).append("</h3>");
        return sb;
    }

    private String mailTail(StringBuilder sb, Long contractId) {
        if (StringUtils.hasText(baseUrl) && contractId != null) {
            String link = baseUrl + "/personalinfo/index.html#/contracts/" + contractId;
            sb.append("<p style=\"margin-top:16px\"><a href=\"").append(esc(link)).append("\">시스템에서 계약 상세 보기</a></p>");
        }
        sb.append("<p style=\"color:#6b7280;font-size:12px;margin-top:16px\">본 메일은 개인정보 위수탁 협력사 관리 시스템에서 자동 발송되었습니다.</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String infoTable(List<String[]> rows) {
        StringBuilder sb = new StringBuilder("<table style=\"border-collapse:collapse;min-width:480px\">");
        for (String[] r : rows) {
            sb.append("<tr><th style=\"text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f8fafc;width:140px\">")
              .append(esc(r[0])).append("</th><td style=\"padding:6px 10px;border:1px solid #ddd\">")
              .append(esc(nz(r[1]))).append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String td(String v) {
        return "<td style=\"padding:6px 10px;border:1px solid #ddd\">" + esc(nz(v)) + "</td>";
    }

    // ─────────────────────────────────────────────────────────────
    //  수동 발송 (상세 화면)
    // ─────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AlertLogResponse sendManual(Long contractId, AlertSendRequest request, Long userId) {
        PiContract c = contractRepository.findByContractIdAndDeletedYn(contractId, "N")
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException("위수탁 계약", contractId));
        accessScope.check(c);
        String extra = request == null ? null : request.getExtraRecipients();
        Set<String> recipients = recipientsOf(c, extra);
        if (recipients.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "수신자가 없습니다. 계약 담당자 이메일 또는 추가 수신자를 입력하세요.");
        }
        String stage = stageOf(c, LocalDate.now()).orElse(STAGE_MANUAL);
        Long alertId = newTx.execute(status -> alertLogRepository.saveAndFlush(PiAlertLog.builder()
                .contractId(c.getContractId())
                .triggerType(AlertTriggerType.MANUAL)
                .alertStage(stage)
                .targetEndDate(c.getContractEndDate())
                .dedupKey(null)
                .recipients(String.join(", ", recipients))
                .subject(subjectOf(c, stage))
                .createdBy(userId)
                .build()).getAlertId());
        deliver(alertId, c, stage, request, LocalDate.now());
        return newTx.execute(status -> alertLogRepository.findById(alertId).map(AlertLogResponse::from).orElse(null));
    }

    // ─────────────────────────────────────────────────────────────
    //  발송
    // ─────────────────────────────────────────────────────────────

    private AlertSendStatus deliver(Long alertId, PiContract c, String stage, AlertSendRequest manual, LocalDate today) {
        String extra = manual == null ? null : manual.getExtraRecipients();
        Set<String> recipients = recipientsOf(c, extra);
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String error = null;
        String formVersions = null;
        AlertSendStatus result;

        if (recipients.isEmpty()) {
            result = AlertSendStatus.SKIPPED;
            error = "수신자 없음 (계약 담당자 이메일/추가 수신자/기본 수신자 미설정)";
        } else if (sender == null) {
            result = AlertSendStatus.SKIPPED;
            error = "메일 서버 미설정 (spring.mail.host)";
        } else {
            try {
                // 계약만료시 점검 양식 3종(체크리스트·확약서·파기 확인서)을 계약 정보로 채워 첨부
                List<PiFileStorage.DownloadFile> attachments = expiryForms.mailAttachments(c);
                formVersions = attachments.isEmpty() ? null : expiryForms.versionSummary();
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, !attachments.isEmpty(), StandardCharsets.UTF_8.name());
                helper.setEncodeFilenames(true);   // 한글 파일명 (RFC 2047)
                if (StringUtils.hasText(mailFrom)) {
                    helper.setFrom(mailFrom);
                }
                helper.setTo(recipients.toArray(String[]::new));
                helper.setSubject(subjectOf(c, stage));
                helper.setText(bodyOf(c, stage, today, manual == null ? null : manual.getMessage(),
                        attachments.stream().map(PiFileStorage.DownloadFile::fileName).toList(), expiryStatus(c)), true);
                for (PiFileStorage.DownloadFile f : attachments) {
                    helper.addAttachment(f.fileName(), new ByteArrayResource(f.data()), f.contentType());
                }
                sender.send(message);
                result = AlertSendStatus.SENT;
            } catch (Exception e) {
                log.warn("[personalinfo] 만료 알림 메일 발송 실패 contractId={} stage={}", c.getContractId(), stage, e);
                result = AlertSendStatus.FAILED;
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        final AlertSendStatus finalResult = result;
        final String finalError = error;
        final String finalVersions = formVersions;
        newTx.executeWithoutResult(status -> alertLogRepository.findById(alertId).ifPresent(row -> {
            row.recordFormVersions(finalVersions);
            if (finalResult == AlertSendStatus.SENT) {
                row.markSent();
            } else if (finalResult == AlertSendStatus.FAILED) {
                row.markFailed(finalError);
            } else {
                row.markSkipped(finalError);
            }
        }));
        return result;
    }

    Set<String> recipientsOf(PiContract c, String extra) {
        Set<String> set = new LinkedHashSet<>();
        addEmails(set, c.getManagerEmail());
        addEmails(set, c.getAlertExtraEmails());
        addEmails(set, defaultRecipients);
        addEmails(set, extra);
        return set;
    }

    private static void addEmails(Set<String> set, String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> s.contains("@") && s.length() <= 200)
                .map(String::toLowerCase)
                .forEach(set::add);
    }

    String subjectOf(PiContract c, String stage) {
        String when = switch (stage) {
            case STAGE_EXPIRED -> "계약 만료됨";
            case STAGE_DDAY -> "오늘 계약 만료";
            case STAGE_MANUAL -> "계약 확인 요청";
            default -> "계약 만료 " + stage.replace("D-", "") + "일 전";
        };
        return subjectPrefix + " " + when + " - " + c.getVendorName()
                + (StringUtils.hasText(c.getContractName()) ? " / " + c.getContractName() : "")
                + (c.getContractEndDate() == null ? "" : " (" + c.getContractEndDate().format(D) + ")");
    }

    /** 계약만료시 점검 자료 수취 여부: [만료 점검 체크리스트, 비밀유지 확약서, 파기 확인서] */
    boolean[] expiryStatus(PiContract c) {
        Long id = c.getContractId();
        boolean checklist = !inspectionRepository
                .findByContractIdInAndInspectionTypeAndDeletedYn(List.of(id), InspectionType.TERMINATION, "N").isEmpty();
        var files = fileRepository.findByContractIdAndDeletedYnOrderByFileIdDesc(id, "N");
        boolean pledge = files.stream().anyMatch(f -> f.getFileType() == ContractFileType.SECURITY_PLEDGE);
        boolean destruction = files.stream().anyMatch(f -> f.getFileType() == ContractFileType.DESTRUCTION_CONFIRM);
        return new boolean[]{checklist, pledge, destruction};
    }

    String bodyOf(PiContract c, String stage, LocalDate today, String extraMessage) {
        return bodyOf(c, stage, today, extraMessage, List.of(), null);
    }

    String bodyOf(PiContract c, String stage, LocalDate today, String extraMessage,
                  List<String> attachmentNames, boolean[] received) {
        Long daysLeft = c.getContractEndDate() == null ? null : ChronoUnit.DAYS.between(today, c.getContractEndDate());
        String dLabel = daysLeft == null ? "-" : (daysLeft < 0 ? "만료 " + (-daysLeft) + "일 경과"
                : (daysLeft == 0 ? "오늘 만료" : daysLeft + "일 남음"));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"수탁업체명", c.getVendorName()});
        if (StringUtils.hasText(c.getContractName())) {
            rows.add(new String[]{"계약명", c.getContractName()});
        }
        rows.add(new String[]{"관련 서비스", c.getServiceName()});
        rows.add(new String[]{"위탁업무 내용", c.getEntrustedTask()});
        rows.add(new String[]{"담당부서 / 담당자", nz(c.getDeptName()) + " / " + nz(c.getManagerName())});
        rows.add(new String[]{"계약기간", fmt(c.getContractStartDate()) + " ~ " + fmt(c.getContractEndDate())});
        rows.add(new String[]{"만료까지", dLabel});
        rows.add(new String[]{"자동연장", "Y".equals(c.getAutoRenewYn()) ? "예" + (StringUtils.hasText(c.getAutoRenewNote()) ? " (" + c.getAutoRenewNote() + ")" : "") : "아니오"});
        rows.add(new String[]{"재위탁", "Y".equals(c.getSubcontractYn()) ? nz(c.getSubcontractorName()) : "없음"});
        rows.add(new String[]{"최근 점검일", fmt(c.getLastInspectionDate())});

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:'Malgun Gothic',sans-serif;font-size:14px;color:#111\">");
        sb.append("<h3 style=\"margin:0 0 12px\">개인정보 위수탁 계약 만료 안내</h3>");
        sb.append("<p>아래 개인정보 처리 위탁 계약의 만료일이 다가왔습니다(또는 만료되었습니다).<br>")
          .append("계약 갱신 여부를 확인하고, 계약을 종료하는 경우 <b>만료일 전에</b> 협력사로부터 ")
          .append("<b>계약만료시 점검 체크리스트, 정보보호 및 비밀유지 확약서, 개인정보 파기 확인서</b>를 받아 주세요.</p>");
        if (StringUtils.hasText(extraMessage)) {
            sb.append("<p style=\"padding:8px 12px;background:#f3f4f6;border-left:3px solid #4f46e5\">")
              .append(esc(extraMessage).replace("\n", "<br>")).append("</p>");
        }
        sb.append("<table style=\"border-collapse:collapse;min-width:480px\">");
        for (String[] r : rows) {
            sb.append("<tr><th style=\"text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f8fafc;width:140px\">")
              .append(esc(r[0])).append("</th><td style=\"padding:6px 10px;border:1px solid #ddd\">")
              .append(esc(nz(r[1]))).append("</td></tr>");
        }
        sb.append("</table>");
        appendExpiryForms(sb, attachmentNames, received);
        if (StringUtils.hasText(baseUrl)) {
            String link = baseUrl + "/personalinfo/index.html#/contracts/" + c.getContractId();
            sb.append("<p style=\"margin-top:16px\"><a href=\"").append(esc(link)).append("\">시스템에서 계약 상세 보기</a></p>");
        }
        sb.append("<p style=\"color:#6b7280;font-size:12px;margin-top:16px\">본 메일은 개인정보 위수탁 협력사 관리 시스템에서 자동 발송되었습니다.</p>");
        sb.append("</div>");
        return sb.toString();
    }

    /** 계약만료시 점검 자료 안내 (첨부 양식 + 수취 현황) */
    private static void appendExpiryForms(StringBuilder sb, List<String> attachmentNames, boolean[] received) {
        String[][] items = {
                {"계약만료시 점검 체크리스트 (엑셀)", "체크리스트의 '3.계약변경 만료시 체크' 시트 작성"},
                {"정보보호 및 비밀유지 확약서", "대표자 날인 / 개인정보 보호책임자 서명"},
                {"개인정보 파기 확인서", "파기 내역 작성 + 파기 증빙 첨부"},
        };
        sb.append("<h4 style=\"margin:20px 0 8px\">계약 만료 시 협력사 제출 자료</h4>");
        if (attachmentNames != null && !attachmentNames.isEmpty()) {
            sb.append("<p style=\"margin:0 0 8px\">이 메일에 <b>양식 ").append(attachmentNames.size())
              .append("종</b>이 첨부되어 있습니다(계약 정보가 미리 채워져 있음). 협력사에 전달하여 작성본을 받은 뒤, ")
              .append("시스템 계약 상세의 <b>[계약만료시 점검]</b>에 업로드해 주세요.</p>");
        }
        sb.append("<table style=\"border-collapse:collapse;min-width:480px\"><tr>")
          .append("<th style=\"text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f8fafc\">제출 자료</th>")
          .append("<th style=\"text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f8fafc\">작성 안내</th>")
          .append("<th style=\"text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f8fafc\">수취 현황</th></tr>");
        for (int i = 0; i < items.length; i++) {
            boolean ok = received != null && received.length > i && received[i];
            sb.append("<tr><td style=\"padding:6px 10px;border:1px solid #ddd\">").append(esc(items[i][0]))
              .append("</td><td style=\"padding:6px 10px;border:1px solid #ddd;color:#4b5563\">").append(esc(items[i][1]))
              .append("</td><td style=\"padding:6px 10px;border:1px solid #ddd;font-weight:700;color:")
              .append(ok ? "#166534\">수취 완료" : "#b91c1c\">미수취").append("</td></tr>");
        }
        sb.append("</table>");
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String fmt(LocalDate d) {
        return d == null ? "-" : d.format(D);
    }
}
