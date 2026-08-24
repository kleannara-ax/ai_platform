package com.company.module.kims.dto.response;

import com.company.module.kims.entity.InventoryItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 소모품 품목 응답 DTO.
 */
@Getter
@Builder
public class InventoryItemResponse {

    private final Long itemId;
    private final String itemName;
    private final String category;
    private final int currentStock;
    private final int minStock;
    private final String unit;
    private final String remark;

    /** 재고 부족 여부 (현재고 <= 최소기준) */
    private final boolean belowMinStock;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static InventoryItemResponse from(InventoryItem entity) {
        return InventoryItemResponse.builder()
                .itemId(entity.getItemId())
                .itemName(entity.getItemName())
                .category(entity.getCategory())
                .currentStock(entity.getCurrentStock())
                .minStock(entity.getMinStock())
                .unit(entity.getUnit())
                .remark(entity.getRemark())
                .belowMinStock(entity.isBelowMinStock())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
