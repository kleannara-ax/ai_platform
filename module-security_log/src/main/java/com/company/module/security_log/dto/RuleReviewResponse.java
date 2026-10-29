package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

/** 룰 단위 일괄 검토 결과 */
@Getter
@Builder
public class RuleReviewResponse {

    private String ruleCode;
    private int updatedCount;
}
