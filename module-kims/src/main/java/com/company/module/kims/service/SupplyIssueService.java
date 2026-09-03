package com.company.module.kims.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.SupplyIssueCreateRequest;
import com.company.module.kims.dto.response.SupplyIssueResponse;
import com.company.module.kims.entity.InventoryItem;
import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.entity.RequestLog;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.SupplyIssue;
import com.company.module.kims.repository.InventoryItemRepository;
import com.company.module.kims.repository.InventoryTransactionRepository;
import com.company.module.kims.repository.RequestLogRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import com.company.module.kims.repository.SupplyIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 소모품 지급 관련 비즈니스 로직.
 * <p>지급 등록 시 업무 요청과 연결하고, 품목 재고를 자동 차감하며,
 * 재고 변동 이력(OUTBOUND)과 요청 처리 로그를 함께 남긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplyIssueService {

    private final SupplyIssueRepository supplyIssueRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final RequestLogRepository requestLogRepository;
    private final ExcelExportService excelExportService;

    // ================================================================
    // 8. 소모품 지급 (재고 자동 차감)
    // ================================================================
    @Transactional
    public SupplyIssueResponse issue(SupplyIssueCreateRequest request) {
        // 1) 연결 대상(업무 요청 / 품목) 조회
        ServiceRequest serviceRequest = serviceRequestRepository.findById(request.getRequestId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "업무 요청을 찾을 수 없습니다. id=" + request.getRequestId()));

        InventoryItem item = inventoryItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "소모품 품목을 찾을 수 없습니다. id=" + request.getItemId()));

        int quantity = request.getQuantity();
        int before = item.getCurrentStock();

        // 2) 재고 차감 (재고 부족 시 IllegalArgumentException 발생)
        item.decreaseStock(quantity);
        int after = item.getCurrentStock();

        // 2-1) 세부 구분(신형/구형, 제조사 등)이 선택된 경우, 비고(remark)의 해당
        //      구분 수치도 함께 차감한다 — 대시보드 재고 막대그래프가 remark 를 파싱해
        //      세부 구분을 그리므로, 실제 지급 내역과 어긋나지 않도록 여기서 반영한다.
        String subType = (request.getSubType() != null && !request.getSubType().isBlank())
                ? request.getSubType() : null;
        if (subType != null) {
            item.adjustRemarkSegment(subType, -quantity);
        }

        // 3) 지급일 기본값 = 오늘
        LocalDate issuedAt = (request.getIssuedAt() != null) ? request.getIssuedAt() : LocalDate.now();

        // 4) 지급 내역 저장
        SupplyIssue issue = SupplyIssue.builder()
                .serviceRequest(serviceRequest)
                .inventoryItem(item)
                .quantity(quantity)
                .receiverName(request.getReceiverName())
                .department(request.getDepartment())
                .issuedBy(request.getIssuedBy())
                .issuedAt(issuedAt)
                .subType(subType)
                .build();
        SupplyIssue saved = supplyIssueRepository.save(issue);

        // 5) 재고 변동(출고) 이력 기록
        String note = "업무요청 " + serviceRequest.getRequestNo() + " 지급";
        inventoryTransactionRepository.save(
                InventoryTransaction.ofOutbound(item, quantity, before, after, request.getIssuedBy(), note));

        // 6) 요청 처리 로그(메모)에도 지급 사실 기록
        String logContent = String.format("소모품 지급: %s %d%s (대상자: %s)",
                item.getItemName(), quantity, item.getUnit(), request.getReceiverName());
        requestLogRepository.save(RequestLog.forNote(serviceRequest, request.getIssuedBy(), logContent));

        return SupplyIssueResponse.from(saved);
    }

    // ================================================================
    // 지급 원복 (요청 취소/반려 시 호출)
    // ================================================================
    /**
     * 특정 업무 요청에 연결된 소모품 지급을 모두 취소(원복)한다.
     * <p>재고를 되돌리고(입고 이력 기록) 지급 내역을 삭제하여
     * 정산/지급수량 집계에서 제외되도록 한다. (재고 부족 걱정 없음 — 되돌리는 것이므로)
     *
     * @return 원복된 총 수량 (지급 내역이 없으면 0)
     */
    @Transactional
    public int reverseByRequest(Long requestId, String changedBy) {
        List<SupplyIssue> issues =
                supplyIssueRepository.findByServiceRequest_RequestIdOrderByCreatedAtAsc(requestId);
        if (issues.isEmpty()) {
            return 0;
        }
        String by = (changedBy != null && !changedBy.isBlank()) ? changedBy : "system";
        int reversedQty = 0;
        for (SupplyIssue issue : issues) {
            InventoryItem item = issue.getInventoryItem();
            int before = item.getCurrentStock();
            item.increaseStock(issue.getQuantity());
            int after = item.getCurrentStock();
            // 지급 시 세부 구분(신형/구형 등)을 차감해 두었다면, 원복 시에도 그만큼
            // 되돌려 remark 세부 수치가 실제 재고와 계속 일치하도록 한다.
            if (issue.getSubType() != null && !issue.getSubType().isBlank()) {
                item.adjustRemarkSegment(issue.getSubType(), issue.getQuantity());
            }
            inventoryTransactionRepository.save(InventoryTransaction.ofInbound(
                    item, issue.getQuantity(), before, after, by, "요청 취소/반려로 지급 취소·재고 반환"));
            reversedQty += issue.getQuantity();
        }
        supplyIssueRepository.deleteAll(issues);
        return reversedQty;
    }

    // ================================================================
    // 소모품 지급 내역 목록 조회 (검색 + 페이징)
    // ================================================================
    public PageResponse<SupplyIssueResponse> getList(LocalDate from, LocalDate to,
                                                    Long itemId, String department,
                                                    int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SupplyIssueResponse> result = supplyIssueRepository
                .search(from, to, itemId, emptyToNull(department), pageable)
                .map(SupplyIssueResponse::from);
        return PageResponse.of(result);
    }

    // ================================================================
    // 10. 소모품 지급 내역 Excel 다운로드
    // ================================================================
    public byte[] exportExcel(LocalDate from, LocalDate to, Long itemId, String department) {
        List<SupplyIssue> issues = supplyIssueRepository
                .search(from, to, itemId, emptyToNull(department), Pageable.unpaged())
                .getContent();
        return excelExportService.buildSupplyIssueExcel(issues);
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
