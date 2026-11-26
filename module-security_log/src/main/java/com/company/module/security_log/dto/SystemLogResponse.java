package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 대시보드 → 시스템 상세 (접속·행위 로그) */
@Getter
@Builder
public class SystemLogResponse {

    private Long systemId;
    private String systemName;
    private String category;
    private String targetYm;
    private DashboardResponse.CalendarRow calendar;

    /** ① 접속·행위 로그: 이번 달 업로드·수집 건 */
    private List<LogUploadResponse> uploads;
    /** 이번 달 계정별 접속 집계 (위험 표시 계정 먼저) */
    private List<AccessSummaryResponse> accessAccounts;

}
