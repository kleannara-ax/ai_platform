package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.MatchType;
import com.company.module.security_log.entity.SecLogRule;
import com.company.module.security_log.entity.Severity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RuleResponse {

    private Long ruleId;
    /** 시스템 전용 룰의 시스템 ID (NULL 이면 공통 룰) */
    private Long systemId;
    private String systemName;
    /** COMMON(공통) / SYSTEM(시스템 전용) */
    private String scope;
    private String ruleCode;
    private String ruleName;
    private String description;
    private LogType logType;
    private String logTypeLabel;
    private MatchType matchType;
    private String matchTypeLabel;
    private String pattern;
    private Severity severity;
    private String severityLabel;
    private Integer thresholdCount;
    private String useYn;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    public static RuleResponse from(SecLogRule rule) {
        return from(rule, null);
    }

    public static RuleResponse from(SecLogRule rule, String systemName) {
        return RuleResponse.builder()
                .ruleId(rule.getRuleId())
                .systemId(rule.getSystemId())
                .systemName(systemName)
                .scope(rule.getSystemId() == null ? "COMMON" : "SYSTEM")
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .description(rule.getDescription())
                .logType(rule.getLogType())
                .logTypeLabel(rule.getLogType().getLabel())
                .matchType(rule.getMatchType())
                .matchTypeLabel(rule.getMatchType().getLabel())
                .pattern(rule.getPattern())
                .severity(rule.getSeverity())
                .severityLabel(rule.getSeverity().getLabel())
                .thresholdCount(rule.getThresholdCount())
                .useYn(rule.getUseYn())
                .sortOrder(rule.getSortOrder())
                .createdAt(rule.getCreatedAt())
                .createdBy(rule.getCreatedBy())
                .updatedAt(rule.getUpdatedAt())
                .updatedBy(rule.getUpdatedBy())
                .build();
    }
}
