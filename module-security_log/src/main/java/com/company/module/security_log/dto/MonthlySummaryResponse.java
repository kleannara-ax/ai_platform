package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 월별 모니터링 요약 (정보보안팀 대시보드) */
@Getter
@Builder
public class MonthlySummaryResponse {

    private String targetYm;
    private int uploadCount;
    private int systemCount;
    private int reviewedUploadCount;
    private int pendingUploadCount;
    private long totalLineCount;
    private long detectionCount;
    private long criticalCount;
    private long highCount;
    private long mediumCount;
    private long lowCount;
    private long pendingDetectionCount;
    private List<RuleHitCount> topRules;
}
