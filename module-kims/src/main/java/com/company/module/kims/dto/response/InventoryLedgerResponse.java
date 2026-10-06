package com.company.module.kims.dto.response;

import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.enums.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 소모품 입출고 이력(전체 품목) 응답 DTO.
 * <p>출고 이력은 비고에 남은 업무요청 번호로 요청을 찾아 요청자·부서를 함께 담는다
 * (연결된 요청이 없으면 요청 관련 필드는 null).
 */
@Getter
@Builder
public class InventoryLedgerResponse {

    private final Long transactionId;
    private final LocalDateTime createdAt;
    private final TransactionType transactionType;
    private final String transactionTypeLabel;

    private final Long itemId;
    private final String itemName;
    private final String category;

    private final int quantity;
    private final int beforeStock;
    private final int afterStock;
    private final String createdBy;
    private final String note;

    private final Long requestId;
    private final String requestNo;
    private final String requesterName;
    private final String department;

    public static InventoryLedgerResponse of(InventoryTransaction t, ServiceRequest request) {
        return InventoryLedgerResponse.builder()
                .transactionId(t.getTransactionId())
                .createdAt(t.getCreatedAt())
                .transactionType(t.getTransactionType())
                .transactionTypeLabel(t.getTransactionType().getLabel())
                .itemId(t.getInventoryItem().getItemId())
                .itemName(t.getInventoryItem().getItemName())
                .category(t.getInventoryItem().getCategory())
                .quantity(t.getQuantity())
                .beforeStock(t.getBeforeStock())
                .afterStock(t.getAfterStock())
                .createdBy(t.getCreatedBy())
                .note(t.getNote())
                .requestId(request != null ? request.getRequestId() : null)
                .requestNo(request != null ? request.getRequestNo() : null)
                .requesterName(request != null ? request.getRequesterName() : null)
                .department(request != null ? request.getDepartment() : null)
                .build();
    }
}
