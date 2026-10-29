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
    private List<AccessSummaryResponse> accounts;
}
