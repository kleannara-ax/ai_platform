package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 소모품 재고 변동(InventoryTransaction)의 유형.
 */
@Getter
public enum TransactionType {

    INBOUND("입고"),   // 재고 증가 (입고 등록)
    OUTBOUND("출고");  // 재고 감소 (소모품 지급)

    /** 화면에 표시할 한글 명칭 */
    private final String label;

    TransactionType(String label) {
        this.label = label;
    }
}
