package com.company.module.personalinfo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 계약 만료 알림 메일 발송 이력.
 *
 * <p>자동 발송(AUTO)은 {@code DEDUP_KEY = 계약ID:단계:만료일} UNIQUE 로
 * 같은 단계 알림이 중복 발송되지 않게 한다(다중 WAS 동시 실행 대비).
 * 수동 발송(MANUAL)은 DEDUP_KEY 가 NULL 이라 제한 없이 이력이 쌓인다.
 */
@Entity
@Table(name = "pi_alert_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiAlertLog extends PiBaseEntity {

    public static final int MAX_RETRY = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ALERT_ID")
    private Long alertId;

    @Column(name = "CONTRACT_ID", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "TRIGGER_TYPE", nullable = false, length = 10)
    private AlertTriggerType triggerType;

    @Column(name = "ALERT_STAGE", nullable = false, length = 20)
    private String alertStage;

    @Column(name = "TARGET_END_DATE")
    private LocalDate targetEndDate;

    @Column(name = "DEDUP_KEY", length = 100)
    private String dedupKey;

    @Column(name = "RECIPIENTS", length = 2000)
    private String recipients;

    @Column(name = "SUBJECT", length = 500)
    private String subject;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "SEND_STATUS", nullable = false, length = 10)
    private AlertSendStatus sendStatus;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;

    @Column(name = "RETRY_COUNT", nullable = false)
    private Integer retryCount;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    @Builder
    private PiAlertLog(Long contractId, AlertTriggerType triggerType, String alertStage, LocalDate targetEndDate,
                       String dedupKey, String recipients, String subject, Long createdBy) {
        this.contractId = contractId;
        this.triggerType = triggerType;
        this.alertStage = alertStage;
        this.targetEndDate = targetEndDate;
        this.dedupKey = dedupKey;
        this.recipients = recipients;
        this.subject = subject;
        this.sendStatus = AlertSendStatus.PENDING;
        this.retryCount = 0;
        markCreatedBy(createdBy);
    }

    /** 재시도 준비 (수신자/제목 갱신) */
    public void prepareRetry(String recipients, String subject) {
        this.recipients = recipients;
        this.subject = subject;
        this.sendStatus = AlertSendStatus.PENDING;
        this.retryCount = this.retryCount + 1;
        this.errorMessage = null;
    }

    public void markSent() {
        this.sendStatus = AlertSendStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.sendStatus = AlertSendStatus.FAILED;
        this.errorMessage = errorMessage == null ? null
                : (errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
    }

    /** 수신자 없음/메일 서버 미설정으로 발송하지 않음 */
    public void markSkipped(String reason) {
        this.sendStatus = AlertSendStatus.SKIPPED;
        this.errorMessage = reason;
    }

    public boolean canRetry() {
        return this.sendStatus == AlertSendStatus.FAILED && this.retryCount < MAX_RETRY;
    }

    public void delete(Long deletedBy) {
        softDelete(deletedBy);
    }
}
