package com.company.module.security_log.entity;

import com.company.module.security_log.support.SecLogTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDateTime;

/**
 * 룰별 분석 실행 결과 (업로드 1건 x 적용 룰 1개, 탐지 0건 룰 포함)
 * - 행이 있으면 그 룰로 분석함(DONE) 또는 분석 실패(FAILED), 행이 없으면 미분석
 * - 룰 코드/룰명/위험도는 분석 시점 스냅샷. 재분석·업로드 삭제 시 소프트 삭제
 */
@Entity
@Table(name = "sec_log_rule_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogRuleRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RULE_RUN_ID")
    private Long ruleRunId;

    @Column(name = "UPLOAD_ID", nullable = false, updatable = false)
    private Long uploadId;

    @Column(name = "RULE_ID", nullable = false, updatable = false)
    private Long ruleId;

    @Column(name = "RULE_CODE", nullable = false, length = 50, updatable = false)
    private String ruleCode;

    @Column(name = "RULE_NAME", nullable = false, length = 200, updatable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "SEVERITY", nullable = false, length = 20, updatable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "RUN_STATUS", nullable = false, length = 20, updatable = false)
    private RuleRunStatus runStatus;

    @Column(name = "MATCH_COUNT", nullable = false, updatable = false)
    private Integer matchCount;

    @Column(name = "STORED_COUNT", nullable = false, updatable = false)
    private Integer storedCount;

    @Column(name = "TIMEOUT_LINES", nullable = false, updatable = false)
    private Integer timeoutLines;

    @Column(name = "FAIL_REASON", length = 500, updatable = false)
    private String failReason;

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
    private SecLogRuleRun(Long uploadId, Long ruleId, String ruleCode, String ruleName, Severity severity,
                          int matchCount, int storedCount, int timeoutLines, String failReason, Long createdBy) {
        this.uploadId = uploadId;
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.severity = severity;
        this.matchCount = matchCount;
        this.storedCount = storedCount;
        this.timeoutLines = timeoutLines;
        this.failReason = failReason;
        this.runStatus = failReason == null ? RuleRunStatus.DONE : RuleRunStatus.FAILED;
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
}
