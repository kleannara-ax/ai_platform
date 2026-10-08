package com.company.module.security_log.dto;

import com.company.module.security_log.entity.SecLogUpload;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 업로드 상세 화면 응답 (요약 + 룰별 집계 + 검토현황) */
@Getter
@Builder
public class LogUploadDetailResponse {

    private LogUploadResponse upload;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    /** 검토상태별 건수 (PENDING/CONFIRMED/RESOLVED/FALSE_POSITIVE) */
    private Map<String, Long> reviewStatusCounts;

    /** 룰별 탐지 건수 */
    private List<RuleHitCount> ruleHits;

    public static LogUploadDetailResponse of(SecLogUpload u, Map<String, Long> reviewStatusCounts,
                                             List<RuleHitCount> ruleHits) {
        return LogUploadDetailResponse.builder()
                .upload(LogUploadResponse.from(u))
                .reviewComment(u.getReviewComment())
                .reviewedAt(u.getReviewedAt())
                .reviewedBy(u.getReviewedBy())
                .updatedAt(u.getUpdatedAt())
                .updatedBy(u.getUpdatedBy())
                .reviewStatusCounts(reviewStatusCounts)
                .ruleHits(ruleHits)
                .build();
    }
}
