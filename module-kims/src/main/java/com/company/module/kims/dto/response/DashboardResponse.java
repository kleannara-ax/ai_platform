package com.company.module.kims.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 관리자 대시보드 응답 DTO.
 * <p>여러 도메인의 집계/알림을 한 번에 담는다.
 */
@Getter
@Builder
public class DashboardResponse {

    // ---- 상단 카드 (요청 처리상태 요약) ----
    private final long todayReceived;  // 오늘 접수된 요청 수
    private final long inProgress;     // 처리중
    private final long onHold;         // 보류
    private final long completed;      // 완료
    private final long totalRequests;  // 전체 요청 수

    // ---- 차트/집계 ----
    private final List<CountItem> byStatus;   // 처리상태별 건수 (전체)
    private final List<CountItem> byType;     // 요청유형별 건수 (선택 연도 기준)
    private final List<MonthCount> monthly;   // 월별 접수 건수 (선택 연도 12개월)
    private final List<Integer> years;        // 데이터가 있는 연도 목록(+올해)
    private final Integer year;               // monthly/byType 가 반영하는 기준 연도

    // ---- 목록/알림 ----
    private final List<ServiceRequestResponse> recentRequests;   // 최근 요청 목록
    private final long lowStockCount;                            // 재고 부족 품목 수
    private final List<InventoryItemResponse> lowStockItems;     // 재고 부족 품목
    private final long unapprovedIpCount;                        // 미품의 IP 변경 수
    private final List<IpHistoryResponse> unapprovedIpChanges;   // 미품의 IP 변경 내역

    /** 코드/라벨/건수 묶음 (상태별·유형별 집계) */
    @Getter
    @Builder
    public static class CountItem {
        private final String code;
        private final String label;
        private final long count;
    }

    /** 월별 건수 (month 형식: "yyyy-MM") */
    @Getter
    @Builder
    public static class MonthCount {
        private final String month;
        private final long count;
    }
}
