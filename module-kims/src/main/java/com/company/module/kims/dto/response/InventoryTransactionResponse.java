package com.company.module.kims.dto.response;

import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.entity.enums.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 소모품 재고 변동(입고/출고) 이력 응답 DTO.
 */
@Getter
@Builder
public class InventoryTransactionResponse {

    private final Long transactionId;
    private final TransactionType transactionType;
    private final String transactionTypeLabel;

    private final int quantity;
    private final int beforeStock;
    private final int afterStock;
    private final String note;
    private final String createdBy;
    private final LocalDateTime createdAt;

    public static InventoryTransactionResponse from(InventoryTransaction entity) {
        return InventoryTransactionResponse.builder()
                .transactionId(entity.getTransactionId())
                .transactionType(entity.getTransactionType())
                .transactionTypeLabel(entity.getTransactionType().getLabel())
                .quantity(entity.getQuantity())
                .beforeStock(entity.getBeforeStock())
                .afterStock(entity.getAfterStock())
                .note(entity.getNote())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
