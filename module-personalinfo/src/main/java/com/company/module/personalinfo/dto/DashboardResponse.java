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

    // ── 기준 연도 요약 (상단 카드) ──
    /** 기준 연도 */
    private int baseYear;
    /** 선택 가능한 연도 (최근 연도부터) */
    private List<Integer> availableYears;
    /** 기준 연도에 원계약 기간이 걸친 계약 수 (= 계약중 + 종료) */
    private long yearTotalCount;
    /** 기준 연도 계약중 */
    private long yearActiveCount;
    /** 기준 연도 계약중 중 수탁사 정기 점검 완료 */
    private long yearActiveInspectedCount;
    /** 기준 연도 안에 종료(종료 처리·만료) */
    private long yearEndedCount;
    /** 종료 중 계약종결 체크(계약변경·만료 점검) 업로드 */
    private long yearEndedTermCheckCount;
    /** 종료 중 계약종결 체크 미업로드 */
    private long yearEndedTermMissingCount;
}
