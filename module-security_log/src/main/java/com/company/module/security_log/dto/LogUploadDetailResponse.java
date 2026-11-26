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

    /** 이 파일이 그 달·그 시스템에서 가장 최근에 올린 파일(분석 대상)인지. false 면 이전 파일이라 월 집계에서 빠짐 */
    private boolean latest;
    /** 그 달·그 시스템의 가장 최근 파일 (분석 대상) */
    private Long latestUploadId;

    public static LogUploadDetailResponse of(SecLogUpload u, Map<String, Long> reviewStatusCounts,
                                             List<RuleHitCount> ruleHits, Long latestUploadId) {
        return LogUploadDetailResponse.builder()
                .latest(latestUploadId == null || latestUploadId.equals(u.getUploadId()))
                .latestUploadId(latestUploadId)
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
