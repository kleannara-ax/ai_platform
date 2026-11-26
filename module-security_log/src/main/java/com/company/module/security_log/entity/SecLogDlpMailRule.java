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
 * DLP 관리 &gt; 메일 알림 관리 - 메일 알림 규칙
 * - 카카오톡 승인 대장 신청 종료일 + N일(음수 = 종료일 전)에 매일 정한 시각에 받는 사람에게 메일
 */
@Entity
@Table(name = "sec_log_dlp_mail_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpMailRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAIL_RULE_ID")
    private Long mailRuleId;

    @Column(name = "RULE_KEY", length = 50, updatable = false)
    private String ruleKey;

    @Column(name = "RULE_NAME", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "OFFSET_DAYS", nullable = false)
    private Integer offsetDays;

    @Column(name = "SEND_TIME", nullable = false, length = 5)
    private String sendTime;

    @Column(name = "TO_ADDRS", nullable = false, length = 1000)
    private String toAddrs;

    @Column(name = "CC_ADDRS", length = 1000)
    private String ccAddrs;

    @Column(name = "SUBJECT_TPL", nullable = false, length = 300)
    private String subjectTpl;

    @Column(name = "BODY_TPL", nullable = false, length = 2000)
    private String bodyTpl;

    @Column(name = "USE_YN", nullable = false, length = 1)
    private String useYn;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "LAST_RUN_AT")
    private LocalDateTime lastRunAt;

    @Column(name = "LAST_RUN_RESULT", length = 500)
    private String lastRunResult;

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
    private SecLogDlpMailRule(String ruleName, Integer offsetDays, String sendTime, String toAddrs, String ccAddrs,
                              String subjectTpl, String bodyTpl, String useYn, LocalDate startDate, Long createdBy) {
        this.ruleName = ruleName;
        this.offsetDays = offsetDays;
        this.sendTime = sendTime;
        this.toAddrs = toAddrs;
        this.ccAddrs = ccAddrs;
        this.subjectTpl = subjectTpl;
        this.bodyTpl = bodyTpl;
        this.useYn = useYn == null ? "Y" : useYn;
        this.startDate = startDate;
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

    /** 규칙 수정 (보내기 시작일·실행 기록은 그대로) */
    public void update(String ruleName, Integer offsetDays, String sendTime, String toAddrs, String ccAddrs,
                       String subjectTpl, String bodyTpl, String useYn, Long userId) {
        this.ruleName = ruleName;
        this.offsetDays = offsetDays;
        this.sendTime = sendTime;
        this.toAddrs = toAddrs;
        this.ccAddrs = ccAddrs;
        this.subjectTpl = subjectTpl;
        this.bodyTpl = bodyTpl;
        this.useYn = useYn == null ? "Y" : useYn;
        this.updatedBy = userId;
    }

    /** 실행 결과 한 줄 기록 */
    public void recordRun(String result) {
        this.lastRunAt = SecLogTime.now();
        this.lastRunResult = result;
    }

    public void delete(Long userId) {
        this.deletedYn = "Y";
        this.deletedAt = SecLogTime.now();
        this.deletedBy = userId;
    }
}
