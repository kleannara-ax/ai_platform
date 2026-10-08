package com.company.module.security_log.entity;

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
 * 이상징후 탐지 룰셋
 */
@Entity
@Table(name = "sec_log_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RULE_ID")
    private Long ruleId;

    @Column(name = "RULE_CODE", nullable = false, length = 50, updatable = false)
    private String ruleCode;

    @Column(name = "RULE_NAME", nullable = false, length = 200)
    private String ruleName;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "LOG_TYPE", nullable = false, length = 20)
    private LogType logType;

    @Enumerated(EnumType.STRING)
    @Column(name = "MATCH_TYPE", nullable = false, length = 20)
    private MatchType matchType;

    @Column(name = "PATTERN", nullable = false, length = 1000)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "SEVERITY", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "THRESHOLD_COUNT", nullable = false)
    private Integer thresholdCount;

    @Column(name = "USE_YN", nullable = false, length = 1)
    private String useYn;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

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
    private SecLogRule(String ruleCode, String ruleName, String description, LogType logType,
                       MatchType matchType, String pattern, Severity severity, Integer thresholdCount,
                       String useYn, Integer sortOrder, Long createdBy) {
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.description = description;
        this.logType = logType;
        this.matchType = matchType;
        this.pattern = pattern;
        this.severity = severity;
        this.thresholdCount = thresholdCount == null ? 1 : thresholdCount;
        this.useYn = useYn == null ? "Y" : useYn;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.createdBy = createdBy;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String ruleName, String description, LogType logType, MatchType matchType,
                       String pattern, Severity severity, Integer thresholdCount, String useYn,
                       Integer sortOrder, Long updatedBy) {
        this.ruleName = ruleName;
        this.description = description;
        this.logType = logType;
        this.matchType = matchType;
        this.pattern = pattern;
        this.severity = severity;
        this.thresholdCount = thresholdCount == null ? 1 : thresholdCount;
        this.useYn = useYn == null ? "Y" : useYn;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.updatedBy = updatedBy;
    }

    public void changeUseYn(String useYn, Long updatedBy) {
        this.useYn = useYn;
        this.updatedBy = updatedBy;
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.useYn = "N";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }

    public boolean isActive() {
        return "Y".equals(this.useYn) && "N".equals(this.deletedYn);
    }
}
