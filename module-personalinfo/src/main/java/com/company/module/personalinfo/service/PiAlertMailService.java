package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.AlertLogResponse;
import com.company.module.personalinfo.dto.AlertRunResponse;
import com.company.module.personalinfo.dto.AlertSendRequest;
import com.company.module.personalinfo.entity.AlertSendStatus;
import com.company.module.personalinfo.entity.AlertTriggerType;
import com.company.module.personalinfo.entity.PiAlertLog;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.repository.PiAlertLogRepository;
import com.company.module.personalinfo.repository.PiContractRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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

    private final String mailFrom;
    private final String defaultRecipients;
    private final String baseUrl;
    private final String subjectPrefix;

    public PiAlertMailService(PiContractRepository contractRepository,
                              PiAlertLogRepository alertLogRepository,
                              ObjectProvider<JavaMailSender> mailSenderProvider,
                              PlatformTransactionManager transactionManager,
                              @Value("${personalinfo.alert.mail-from:${spring.mail.username:}}") String mailFrom,
                              @Value("${personalinfo.alert.default-recipients:}") String defaultRecipients,
                              @Value("${personalinfo.alert.base-url:}") String baseUrl,
                              @Value("${personalinfo.alert.subject-prefix:[개인정보 위수탁]}") String subjectPrefix) {
        this.contractRepository = contractRepository;
        this.alertLogRepository = alertLogRepository;
        this.mailSenderProvider = mailSenderProvider;
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
        AlertSendStatus result;

        if (recipients.isEmpty()) {
            result = AlertSendStatus.SKIPPED;
            error = "수신자 없음 (계약 담당자 이메일/추가 수신자/기본 수신자 미설정)";
        } else if (sender == null) {
            result = AlertSendStatus.SKIPPED;
            error = "메일 서버 미설정 (spring.mail.host)";
        } else {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                if (StringUtils.hasText(mailFrom)) {
                    helper.setFrom(mailFrom);
                }
                helper.setTo(recipients.toArray(String[]::new));
                helper.setSubject(subjectOf(c, stage));
                helper.setText(bodyOf(c, stage, today, manual == null ? null : manual.getMessage()), true);
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
        newTx.executeWithoutResult(status -> alertLogRepository.findById(alertId).ifPresent(row -> {
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
                + (c.getContractEndDate() == null ? "" : " (" + c.getContractEndDate().format(D) + ")");
    }

    String bodyOf(PiContract c, String stage, LocalDate today, String extraMessage) {
        Long daysLeft = c.getContractEndDate() == null ? null : ChronoUnit.DAYS.between(today, c.getContractEndDate());
        String dLabel = daysLeft == null ? "-" : (daysLeft < 0 ? "만료 " + (-daysLeft) + "일 경과"
                : (daysLeft == 0 ? "오늘 만료" : daysLeft + "일 남음"));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"수탁업체명", c.getVendorName()});
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
          .append("계약 갱신 여부를 확인하고, 계약을 종료하는 경우 <b>계약변경·만료 체크리스트</b>")
          .append("(정보보호 및 비밀유지 확약서, 개인정보 파기 확인서 수취 등)를 진행해 주세요.</p>");
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
        if (StringUtils.hasText(baseUrl)) {
            String link = baseUrl + "/personalinfo/index.html#/contracts/" + c.getContractId();
            sb.append("<p style=\"margin-top:16px\"><a href=\"").append(esc(link)).append("\">시스템에서 계약 상세 보기</a></p>");
        }
        sb.append("<p style=\"color:#6b7280;font-size:12px;margin-top:16px\">본 메일은 개인정보 위수탁 협력사 관리 시스템에서 자동 발송되었습니다.</p>");
        sb.append("</div>");
        return sb.toString();
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
