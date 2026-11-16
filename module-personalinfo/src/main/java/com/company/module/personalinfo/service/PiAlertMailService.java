package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.AlertLogResponse;
import com.company.module.personalinfo.dto.AlertRunResponse;
import com.company.module.personalinfo.dto.AlertSendRequest;
import com.company.module.personalinfo.entity.AlertSendStatus;
import com.company.module.personalinfo.entity.AlertTriggerType;
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
        return AlertRunResponse.builder()
                .baseDate(today).candidateCount(considered)
                .sentCount(sent).failedCount(failed).skippedCount(skipped).alreadySentCount(already)
                .build();
    }

    /**
     * 계약의 현재 알림 단계. 남은 일수가 통과한 기준 중 가장 가까운(작은) 기준을 단계로 쓴다.
     * 예) 알림 기준 30일: 남은 20일 → D-30, 남은 5일 → D-7, 남은 1일 → D-1, 당일 → D-DAY, 지남 → EXPIRED
     */
    static Optional<String> stageOf(PiContract c, LocalDate today) {
        if (c.getContractEndDate() == null) {
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
