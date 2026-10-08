package com.company.module.security_log.dto;

import com.company.module.security_log.entity.Severity;
import lombok.Builder;
import lombok.Getter;

/** 룰별 탐지 건수 집계 */
@Getter
@Builder
public class RuleHitCount {

    private String ruleCode;
    private String ruleName;
    private Severity severity;
    private String severityLabel;
    private long hitCount;
}
