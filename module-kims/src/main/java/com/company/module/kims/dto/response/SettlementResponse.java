package com.company.module.kims.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * 월말 결산 응답 DTO.
 * <p>지정 기간(from~to)의 처리내역·집계·미완료 목록을 모아서 제공한다.
 */
@Getter
@Builder
public class SettlementResponse {

    private final LocalDate from;
    private final LocalDate to;

    // ---- 요청 집계 ----
    private final List<NameCount> requestByType;        // 업무유형별 처리 건수
    private final List<NameCount> requestByIssueType;   // PC관련 불편사항 세부 불편유형별 처리 건수
    private final List<NameCount> requestByAssignee;    // 담당자별 처리 건수
    private final List<NameCount> requestByDepartment;  // 부서별 요청 건수
    private final List<ServiceRequestResponse> incompleteRequests; // 미완료 요청 목록

    // ---- 소모품 지급 집계(수량 합계) ----
    private final List<NameCount> supplyByItem;       // 품목별
    private final List<NameCount> supplyByDepartment; // 부서별
    private final List<NameCount> supplyByRequester;  // 요청자별
    private final List<NameCount> supplyByIssuedBy;   // 담당자별

    // ---- 기간 내 도메인별 내역 ----
    private final List<SupplyIssueResponse> supplyIssues;       // 소모품 지급 내역
    private final List<IpHistoryResponse> ipChanges;            // IP 변경/생성/회수 내역
    private final List<IpHistoryResponse> unapprovedIpChanges;  // 미품의 IP 변경 내역
    private final List<ProgramInstallResponse> programInstalls; // 프로그램 설치 내역
    private final List<InternetWorkResponse> internetWorks;     // 인터넷 공사 내역

    /** 이름/값(건수 또는 수량 합계) 묶음 */
    @Getter
    @Builder
    public static class NameCount {
        private final String name;
        private final long value;
    }
}
