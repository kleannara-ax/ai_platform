package com.company.module.kims.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.InboundRequest;
import com.company.module.kims.dto.request.InventoryItemCreateRequest;
import com.company.module.kims.dto.response.InventoryItemResponse;
import com.company.module.kims.dto.response.InventoryTransactionResponse;
import com.company.module.kims.entity.InventoryItem;
import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.repository.InventoryItemRepository;
import com.company.module.kims.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                .findByInventoryItem_ItemIdOrderByCreatedAtDesc(itemId)
                .stream()
                .map(InventoryTransactionResponse::from)
                .toList();
    }

    // ================================================================
    // 7. 소모품 입고 (재고 증가 + 이력 기록)
    // ================================================================
    @Transactional
    public InventoryItemResponse inbound(Long itemId, InboundRequest request) {
        InventoryItem item = findItem(itemId);

        int before = item.getCurrentStock();
        item.increaseStock(request.getQuantity()); // 재고 증가
        int after = item.getCurrentStock();

        // 입고 이력 기록
        inventoryTransactionRepository.save(
                InventoryTransaction.ofInbound(item, request.getQuantity(), before, after,
                        request.getCreatedBy(), request.getNote()));

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
