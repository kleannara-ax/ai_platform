package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 목록 상단 요약 카드 */
@Getter
@Builder
public class DashboardResponse {

    private long totalCount;
    private long activeCount;
    private long expiringCount;
    private long expiredCount;
    private long terminatedCount;
    private long noEndDateCount;
    private long ismsScopeCount;
    private long subcontractCount;
    /** 올해 점검 완료 계약 수 */
    private long inspectedThisYearCount;
    /** 최근 점검 결과가 '조치 필요' 인 계약 수 */
    private long needActionCount;
    /** 미종료 계약 중 올해 미점검 */
    private long notInspectedThisYearCount;
    private int year;
    /** 만료 임박 상위 목록 */
    private List<ContractSummaryResponse> expiringSoon;
}
