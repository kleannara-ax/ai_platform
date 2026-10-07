package com.company.module.kims.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.InboundRequest;
import com.company.module.kims.dto.request.InventoryItemCreateRequest;
import com.company.module.kims.dto.request.LedgerUpdateRequest;
import com.company.module.kims.dto.response.InventoryItemResponse;
import com.company.module.kims.dto.response.InventoryLedgerResponse;
import com.company.module.kims.dto.response.InventoryTransactionResponse;
import com.company.module.kims.entity.InventoryItem;
import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.entity.RequestLog;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.SupplyIssue;
import com.company.module.kims.entity.enums.TransactionType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 전산소모품 품목/재고 관련 비즈니스 로직.
 * <p>품목 등록 / 목록 / 상세 / 입고 / 재고 부족 조회를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryItemService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final SupplyIssueRepository supplyIssueRepository;
    private final RequestLogRepository requestLogRepository;

    /** 출고 이력 비고("업무요청 KIMS-20261006-0001 지급")에서 요청번호를 뽑는 패턴 */
    private static final Pattern REQUEST_NO_IN_NOTE = Pattern.compile("업무요청\\s+(\\S+)\\s+지급");
    /** 업무요청 없이 지급한 출고 이력 비고("요청자 홍길동 직접 지급")에서 요청자명을 뽑는 패턴 */
    private static final Pattern DIRECT_REQUESTER_IN_NOTE = Pattern.compile("요청자\\s+(.+?)\\s+직접 지급");

    // ================================================================
    // 6. 품목 등록
    // ================================================================
    @Transactional
    public InventoryItemResponse create(InventoryItemCreateRequest request) {
        // 품목명 중복 방지
        if (inventoryItemRepository.existsByItemName(request.getItemName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "이미 존재하는 품목명입니다. itemName=" + request.getItemName());
        }

        InventoryItem entity = InventoryItem.builder()
                .itemName(request.getItemName())
                .category(request.getCategory())
                .currentStock(request.getCurrentStock())
                .minStock(request.getMinStock())
                .unit(request.getUnit())
                .remark(request.getRemark())
                .build();

        return InventoryItemResponse.from(inventoryItemRepository.save(entity));
    }

    // ================================================================
    // 6. 품목 목록 조회
    // ================================================================
    public PageResponse<InventoryItemResponse> getList(String keyword, String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryItemResponse> result = inventoryItemRepository
                .search(emptyToNull(keyword), emptyToNull(category), pageable)
                .map(InventoryItemResponse::from);
        return PageResponse.of(result);
    }

    // ================================================================
    // 6. 품목 상세 조회
    // ================================================================
    public InventoryItemResponse getDetail(Long itemId) {
        return InventoryItemResponse.from(findItem(itemId));
    }

    /** 특정 품목의 재고 변동 이력 조회 */
    public List<InventoryTransactionResponse> getTransactions(Long itemId) {
        findItem(itemId); // 존재 확인
        return inventoryTransactionRepository
                .findByInventoryItem_ItemIdOrderByTransactionDateDescTransactionIdDesc(itemId)
                .stream()
                .map(InventoryTransactionResponse::from)
                .toList();
    }

    // ================================================================
    // 전체 품목 입출고 이력 검색 (소모품 업무요청 이력 탭)
    // ================================================================
    public PageResponse<InventoryLedgerResponse> searchLedger(LocalDate from, LocalDate to, Long itemId,
                                                             TransactionType type, String createdBy,
                                                             int page, int size) {
        Page<InventoryTransaction> result = inventoryTransactionRepository.search(
                from, to, itemId, type,
                (createdBy == null || createdBy.isBlank()) ? null : createdBy.trim(),
                PageRequest.of(page, size));

        // 지급 내역과 연결된 출고는 그 지급 내역에서 요청자·지급대상자·부서를 가져온다.
        // 연결되지 않은 예전 출고 이력만 비고에 남은 요청번호/요청자명으로 보완한다.
        Set<String> requestNos = new HashSet<>();
        result.forEach(t -> {
            String no = requestNoOf(t);
            if (no != null) requestNos.add(no);
        });
        Map<String, ServiceRequest> requests = requestNos.isEmpty() ? Map.of()
                : serviceRequestRepository.findByRequestNoIn(requestNos).stream()
                    .collect(Collectors.toMap(ServiceRequest::getRequestNo, Function.identity(), (a, b) -> a));

        return PageResponse.of(result.map(t -> {
            String no = requestNoOf(t);
            return InventoryLedgerResponse.of(t, no != null ? requests.get(no) : null, directRequesterOf(t));
        }));
    }

    // ================================================================
    // 입출고 이력 수정 — 날짜·비고와 (출고) 요청자·지급대상자·부서·지급 담당자. 재고는 바뀌지 않는다.
    // ================================================================
    @Transactional
    public void updateLedger(Long transactionId, LedgerUpdateRequest request) {
        InventoryTransaction t = inventoryTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("입출고 이력을 찾을 수 없습니다. id=" + transactionId));
        SupplyIssue issue = t.getSupplyIssue();
        if (issue != null) {
            if (request.getReceiverName() != null && request.getReceiverName().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "지급 대상자는 비울 수 없습니다.");
            }
            if (issue.getServiceRequest() == null && request.getRequesterName() != null && request.getRequesterName().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "요청자명은 비울 수 없습니다.");
            }
            issue.update(request.getRequesterName(), request.getReceiverName(), request.getDepartment(),
                    request.getIssuedBy(), request.getTransactionDate());
        }
        t.changeTransactionDate(request.getTransactionDate());
        String note = request.getNote();
        if (note != null && note.length() > 255) note = note.substring(0, 255);   // NOTE 컬럼 길이
        t.changeNote(note);
    }

    // ================================================================
    // 입출고 이력 취소 — 이력을 삭제하고 그 이력의 재고 증감(세부 구분 수량 포함)을 되돌린다
    // ================================================================
    @Transactional
    public void cancelLedger(Long transactionId, String changedBy) {
        InventoryTransaction t = inventoryTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("입출고 이력을 찾을 수 없습니다. id=" + transactionId));
        InventoryItem item = t.getInventoryItem();
        int qty = t.getQuantity();
        String by = (changedBy != null && !changedBy.isBlank()) ? changedBy : "system";

        if (t.getTransactionType() == TransactionType.INBOUND) {
            // 입고 취소 = 입고 수량만큼 재고 차감. 이미 출고되어 남은 재고가 부족하면 취소할 수 없다.
            if (item.getCurrentStock() < qty) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "입고를 취소할 재고가 부족합니다(이미 출고됨). 품목=" + item.getItemName()
                                + ", 현재재고=" + item.getCurrentStock() + ", 취소수량=" + qty);
            }
            String sub = t.getSubType();
            if (sub != null && item.tracksSegments()) {
                Integer segment = item.getSegmentCount(sub);
                if (segment == null || segment < qty) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                            "입고를 취소할 재고가 부족합니다(이미 출고됨). 품목=" + item.getItemName()
                                    + ", " + sub + " 재고=" + (segment != null ? segment : 0) + ", 취소수량=" + qty);
                }
            }
            item.decreaseStock(qty);
            if (sub != null) item.adjustRemarkSegment(sub, -qty);
            inventoryTransactionRepository.delete(t);
            return;
        }

        // 출고 취소 = 지급 수량만큼 재고 복원 + 연결된 지급 내역 삭제
        SupplyIssue issue = t.getSupplyIssue();
        if (issue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "지급 내역과 연결되지 않은 예전 출고 이력이라 취소할 수 없습니다. id=" + transactionId);
        }
        item.increaseStock(qty);
        String sub = (issue.getSubType() != null) ? issue.getSubType() : t.getSubType();
        if (sub != null) item.adjustRemarkSegment(sub, qty);
        ServiceRequest request = issue.getServiceRequest();
        String receiver = issue.getReceiverName();
        inventoryTransactionRepository.delete(t);
        supplyIssueRepository.delete(issue);
        if (request != null) {
            requestLogRepository.save(RequestLog.forNote(request, by,
                    String.format("소모품 지급 취소: %s %d%s (대상자: %s)", item.getItemName(), qty, item.getUnit(), receiver)));
        }
    }

    private String directRequesterOf(InventoryTransaction t) {
        if (t.getTransactionType() != TransactionType.OUTBOUND || t.getSupplyIssue() != null || t.getNote() == null) return null;
        Matcher m = DIRECT_REQUESTER_IN_NOTE.matcher(t.getNote());
        return m.find() ? m.group(1) : null;
    }

    private String requestNoOf(InventoryTransaction t) {
        if (t.getTransactionType() != TransactionType.OUTBOUND || t.getSupplyIssue() != null || t.getNote() == null) return null;
        Matcher m = REQUEST_NO_IN_NOTE.matcher(t.getNote());
        return m.find() ? m.group(1) : null;
    }

    // ================================================================
    // 7. 소모품 입고 (재고 증가 + 이력 기록)
    // ================================================================
    @Transactional
    public InventoryItemResponse inbound(Long itemId, InboundRequest request) {
        InventoryItem item = findItem(itemId);

        // 세부 구분(신형/구형, 레노버/갤럭시)을 추적하는 품목은 구분을 반드시 골라야 한다.
        String subType = (request.getSubType() != null && !request.getSubType().isBlank())
                ? request.getSubType().trim() : null;
        if (item.tracksSegments() && (subType == null || !InventoryItem.SEGMENT_LABELS.contains(subType))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "세부 구분을 선택하세요. 품목=" + item.getItemName());
        }

        int before = item.getCurrentStock();
        item.increaseStock(request.getQuantity()); // 재고 증가
        int after = item.getCurrentStock();

        // 세부 구분이 선택된 경우, 비고(remark)의 해당 구분 수치도 함께 늘린다
        // (비고에 그 구분이 없으면 새로 덧붙임) — 지급(SupplyIssueService.issue)과 같은 방식으로 대시보드 막대와 맞춘다.
        if (subType != null) {
            item.adjustRemarkSegment(subType, request.getQuantity());
        }

        // 입고 이력 기록 — 입고일(모달 입력, 미입력 시 오늘)과 세부 구분을 함께 남긴다
        String note = (request.getNote() != null && !request.getNote().isBlank()) ? request.getNote().trim() : null;
        if (note != null && note.length() > 255) note = note.substring(0, 255);   // NOTE 컬럼 길이
        inventoryTransactionRepository.save(
                InventoryTransaction.ofInbound(item, request.getQuantity(), before, after,
                        request.getCreatedBy(), note, request.getInboundAt(), subType));

        return InventoryItemResponse.from(item);
    }

    // ================================================================
    // 9. 재고 부족 품목 조회
    // ================================================================
    public List<InventoryItemResponse> getLowStockItems() {
        return inventoryItemRepository.findLowStockItems()
                .stream()
                .map(InventoryItemResponse::from)
                .toList();
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    private InventoryItem findItem(Long itemId) {
        return inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("소모품 품목을 찾을 수 없습니다. id=" + itemId));
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
