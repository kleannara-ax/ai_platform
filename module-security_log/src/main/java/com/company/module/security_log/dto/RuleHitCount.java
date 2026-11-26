package com.company.module.security_log.dto;

import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogRule;
import com.company.module.security_log.entity.Severity;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/** 룰별 탐지 건수 집계 */
@Getter
@Builder(toBuilder = true)
public class RuleHitCount {

    private String ruleCode;
    private String ruleName;
    private Severity severity;
    private String severityLabel;
    private long hitCount;

    /** 검토 상태별 건수 (업로드 상세에서만 채움) */
    private long pendingCount;
    private long confirmedCount;
    private long resolvedCount;
    private long falsePositiveCount;

    /** 룰 구분 COMMON(공통) / SYSTEM(시스템 전용) - 업로드 상세에서만 채움, 현재 룰셋에 없으면 null */
    private String scope;

    /** 임계 건수 (이 건수 이상 매칭될 때만 탐지) - 업로드 상세에서만 채움 */
    private Integer thresholdCount;

    /** 검토 상태별 건수를 채운 사본 */
    public RuleHitCount withReviewCounts(Map<ReviewStatus, Long> counts) {
        return toBuilder()
                .pendingCount(counts.getOrDefault(ReviewStatus.PENDING, 0L))
                .confirmedCount(counts.getOrDefault(ReviewStatus.CONFIRMED, 0L))
                .resolvedCount(counts.getOrDefault(ReviewStatus.RESOLVED, 0L))
                .falsePositiveCount(counts.getOrDefault(ReviewStatus.FALSE_POSITIVE, 0L))
                .build();
    }

    /** 룰 구분·임계 건수를 채운 사본 */
    public RuleHitCount withRule(SecLogRule rule) {
        return toBuilder()
                .scope(scopeOf(rule))
                .thresholdCount(rule.getThresholdCount())
                .build();
    }

    /** 이번 분석에서 탐지되지 않은(0건) 적용 룰 */
    public static RuleHitCount zero(SecLogRule rule) {
        return RuleHitCount.builder()
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .severity(rule.getSeverity())
                .severityLabel(rule.getSeverity().getLabel())
                .hitCount(0)
                .scope(scopeOf(rule))
                .thresholdCount(rule.getThresholdCount())
                .build();
    }

    private static String scopeOf(SecLogRule rule) {
        return rule.getSystemId() == null ? "COMMON" : "SYSTEM";
    }
}
