package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 업로드 1건의 접속 행위 분석 결과 */
@Getter
@Builder
public class AccessReportResponse {

    private Long uploadId;
    private int accountCount;
    private int riskAccountCount;
    private long successTotal;
    private long failTotal;
    /** 위험 표시 계정 중 검토한 계정 수 / 검토가 남은 계정 수 */
    private int riskReviewedCount;
    private int riskPendingCount;
    /** 위험 표시 없는 계정 중 검토(선택)한 계정 수 */
    private int otherReviewedCount;
    private List<AccessSummaryResponse> accounts;
}
