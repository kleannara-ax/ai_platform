package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.InboundRequest;
import com.company.module.kims.dto.request.InventoryItemCreateRequest;
import com.company.module.kims.dto.response.InventoryItemResponse;
import com.company.module.kims.dto.response.InventoryTransactionResponse;
import com.company.module.kims.service.InventoryItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 전산소모품 품목/재고 REST API.
 * <p>URL prefix: {@code /kims-api/inventory-items}
 */
@RestController
@RequestMapping("/kims-api/inventory-items")
@RequiredArgsConstructor
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    /** 6. 품목 등록 - 관리자 권한 */
    @PostMapping
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> create(
            @Valid @RequestBody InventoryItemCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(inventoryItemService.create(request)));
    }

    /** 6. 품목 목록 조회 (품목명/카테고리 검색 + 페이징) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InventoryItemResponse>>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryItemService.getList(keyword, category, page, size)));
    }

    /**
     * 9. 재고 부족 품목 조회.
     * <p>주의: 고정 경로 {@code /low-stock} 는 {@code /{itemId}} 보다 먼저 선언하여
     * 경로 충돌을 피한다.
     */
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success(inventoryItemService.getLowStockItems()));
    }

    /** 6. 품목 상세 조회 */
    @GetMapping("/{itemId}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getDetail(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryItemService.getDetail(itemId)));
    }

    /** 품목 재고 변동 이력 조회 */
    @GetMapping("/{itemId}/transactions")
    public ResponseEntity<ApiResponse<List<InventoryTransactionResponse>>> getTransactions(
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryItemService.getTransactions(itemId)));
    }

    /** 7. 소모품 입고 (재고 증가) - 관리자 권한 */
    @PostMapping("/{itemId}/inbound")
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> inbound(
            @PathVariable Long itemId,
            @Valid @RequestBody InboundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inventoryItemService.inbound(itemId, request)));
    }
}
