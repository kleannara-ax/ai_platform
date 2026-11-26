package com.company.module.security_log.entity;

import com.company.module.security_log.support.SecLogTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DLP 관리 &gt; 메일 알림 관리 - 보낸 기록 (규칙 1개 x 승인 건 1개당 1행)
 * - SENT 보냄 (다시 보내지 않음) / FAIL 실패 (다음 실행 때 같은 행으로 다시 시도)
 */
@Entity
@Table(name = "sec_log_dlp_mail_send_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpMailSendLog {

    public static final String SENT = "SENT";
    public static final String FAIL = "FAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAIL_SEND_LOG_ID")
    private Long mailSendLogId;

    @Column(name = "MAIL_RULE_ID", nullable = false, updatable = false)
    private Long mailRuleId;

    @Column(name = "KAKAO_APPROVAL_ID", nullable = false, updatable = false)
    private Long kakaoApprovalId;

    @Column(name = "DEPT_NAME", nullable = false, length = 200)
    private String deptName;

    @Column(name = "USER_NAME", nullable = false, length = 200)
    private String userName;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    @Column(name = "TO_ADDRS", nullable = false, length = 1000)
    private String toAddrs;

    @Column(name = "CC_ADDRS", length = 1000)
    private String ccAddrs;

    @Column(name = "SUBJECT", nullable = false, length = 500)
    private String subject;

    @Column(name = "BODY", nullable = false, length = 4000)
    private String body;

    @Column(name = "SEND_STATUS", nullable = false, length = 10)
    private String sendStatus;

    @Column(name = "TRY_COUNT", nullable = false)
    private Integer tryCount;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;

    @Column(name = "LAST_TRY_AT")
    private LocalDateTime lastTryAt;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    @Builder
    private SecLogDlpMailSendLog(Long mailRuleId, Long kakaoApprovalId, Long createdBy) {
        this.mailRuleId = mailRuleId;
        this.kakaoApprovalId = kakaoApprovalId;
        this.sendStatus = FAIL;
        this.tryCount = 0;
        this.createdBy = createdBy;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = SecLogTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = SecLogTime.now();
    }

    /** 한 번 시도한 결과 기록 (error == null 이면 보냄) */
    public void recordTry(String deptName, String userName, LocalDate endDate, String toAddrs, String ccAddrs,
                          String subject, String body, String error, Long userId) {
        LocalDateTime now = SecLogTime.now();
        this.deptName = deptName;
        this.userName = userName;
        this.endDate = endDate;
        this.toAddrs = toAddrs;
        this.ccAddrs = ccAddrs;
        this.subject = subject;
        this.body = body;
        this.tryCount = (this.tryCount == null ? 0 : this.tryCount) + 1;
        this.lastTryAt = now;
        if (error == null) {
            this.sendStatus = SENT;
            this.sentAt = now;
            this.errorMessage = null;
        } else {
            this.sendStatus = FAIL;
            this.errorMessage = error;
        }
        if (userId != null) {
            this.updatedBy = userId;
        }
    }
}
