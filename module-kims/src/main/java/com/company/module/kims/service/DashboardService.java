package com.company.module.kims.service;

import com.company.module.kims.dto.response.DashboardResponse;
import com.company.module.kims.dto.response.InventoryItemResponse;
import com.company.module.kims.dto.response.IpHistoryResponse;
import com.company.module.kims.dto.response.ServiceRequestResponse;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import com.company.module.kims.repository.InventoryItemRepository;
import com.company.module.kims.repository.IpHistoryRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드 집계 서비스.
 * <p>요청/소모품/IP 도메인의 집계와 알림을 모아 한 번에 제공한다. (읽기 전용)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final IpHistoryRepository ipHistoryRepository;

    public DashboardResponse getDashboard() {
        // 1) 처리상태별 건수 (0건 상태도 포함하도록 0채움)
        Map<RequestStatus, Long> statusMap = toEnumLongMap(serviceRequestRepository.countGroupByStatus());
        long total = statusMap.values().stream().mapToLong(Long::longValue).sum();

        List<DashboardResponse.CountItem> byStatus = new ArrayList<>();
        for (RequestStatus s : RequestStatus.values()) {
            byStatus.add(DashboardResponse.CountItem.builder()
                    .code(s.name()).label(s.getLabel())
                    .count(statusMap.getOrDefault(s, 0L)).build());
        }

        // 2) 연도 목록 + 기준 연도(최신) → 유형별/월별 차트는 기준 연도 기준
        List<Integer> years = buildYears();
        int defaultYear = years.get(years.size() - 1);
        List<DashboardResponse.CountItem> byType = getTypeBreakdown(defaultYear, null);

        // 3) 월별 접수 건수 (기준 연도 1~12월, 빈 달 0채움)
        List<DashboardResponse.MonthCount> monthly = getMonthly(defaultYear);

        // 4) 최근 요청 목록
        List<ServiceRequestResponse> recent = serviceRequestRepository.findTop10ByOrderByCreatedAtDesc()
                .stream().map(ServiceRequestResponse::from).toList();

        // 5) 재고 부족 알림
        List<InventoryItemResponse> lowStock = inventoryItemRepository.findLowStockItems()
                .stream().map(InventoryItemResponse::from).toList();

        // 6) 미품의 IP 변경 알림
        List<IpHistoryResponse> unapproved = ipHistoryRepository.findByApprovedFalseOrderByCreatedAtDesc()
                .stream().map(IpHistoryResponse::from).toList();

        // 오늘(0시) 이후 접수 수
        long todayReceived = serviceRequestRepository
                .countByCreatedAtGreaterThanEqual(LocalDate.now().atStartOfDay());

        return DashboardResponse.builder()
                .todayReceived(todayReceived)
                .inProgress(statusMap.getOrDefault(RequestStatus.IN_PROGRESS, 0L))
                .onHold(statusMap.getOrDefault(RequestStatus.ON_HOLD, 0L))
                .completed(statusMap.getOrDefault(RequestStatus.COMPLETED, 0L))
                .totalRequests(total)
                .byStatus(byStatus)
                .byType(byType)
                .monthly(monthly)
                .years(years)
                .year(defaultYear)
                .recentRequests(recent)
                .lowStockCount(lowStock.size())
                .lowStockItems(lowStock)
                .unapprovedIpCount(unapproved.size())
                .unapprovedIpChanges(unapproved)
                .build();
    }

    // ----------------------------------------------------------------
    // 내부 헬퍼
    // ----------------------------------------------------------------

    /** [Enum, Long] 형태의 그룹 집계 결과를 Map 으로 변환 */
    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> Map<E, Long> toEnumLongMap(List<Object[]> rows) {
        Map<E, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((E) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /** 특정 연도의 월별(1~12월) 접수 건수, 빈 달 0채움 */
    public List<DashboardResponse.MonthCount> getMonthly(int year) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Object[] row : serviceRequestRepository.countMonthlyOfYear(year)) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        List<DashboardResponse.MonthCount> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            result.add(DashboardResponse.MonthCount.builder()
                    .month(String.format("%04d-%02d", year, m))
                    .count(counts.getOrDefault(m, 0L)).build());
        }
        return result;
    }

    /**
     * 요청유형별 건수 (0채움).
     * <ul>
     *   <li>year + month 지정 → 해당 월</li>
     *   <li>year 만 지정 → 해당 연도 전체</li>
     *   <li>둘 다 null → 전체 기간</li>
     * </ul>
     */
    public List<DashboardResponse.CountItem> getTypeBreakdown(Integer year, Integer month) {
        Map<RequestType, Long> typeMap;
        if (year != null && month != null) {
            LocalDate first = LocalDate.of(year, month, 1);
            LocalDateTime from = first.atStartOfDay();
            LocalDateTime to = first.withDayOfMonth(first.lengthOfMonth()).atTime(23, 59, 59);
            typeMap = toEnumLongMap(serviceRequestRepository.countTypeInPeriod(from, to));
        } else if (year != null) {
            LocalDateTime from = LocalDate.of(year, 1, 1).atStartOfDay();
            LocalDateTime to = LocalDate.of(year, 12, 31).atTime(23, 59, 59);
            typeMap = toEnumLongMap(serviceRequestRepository.countTypeInPeriod(from, to));
        } else {
            typeMap = toEnumLongMap(serviceRequestRepository.countGroupByType());
        }
        List<DashboardResponse.CountItem> result = new ArrayList<>();
        for (RequestType t : RequestType.values()) {
            result.add(DashboardResponse.CountItem.builder()
                    .code(t.name()).label(t.getLabel())
                    .count(typeMap.getOrDefault(t, 0L)).build());
        }
        return result;
    }

    /** 데이터가 있는 연도 목록(+올해), 오름차순 */
    private List<Integer> buildYears() {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>(serviceRequestRepository.distinctYears());
        set.add(LocalDate.now().getYear());
        return new ArrayList<>(set);
    }
}
