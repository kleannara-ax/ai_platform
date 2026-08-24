package com.company.module.kims.entity;

import com.company.module.kims.entity.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소모품 재고 변동 이력.
 * <p>입고(INBOUND)와 출고(OUTBOUND, 지급)가 발생할 때마다 한 줄씩 기록되며,
 * 변동 전/후 재고 수량을 함께 남겨 추적성을 확보한다.
 */
@Entity
@Table(name = "inventory_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRANSACTION_ID")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ITEM_ID", nullable = false)
    private InventoryItem inventoryItem;

    /** 변동 유형 (입고/출고) */
    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 20)
    private TransactionType transactionType;

    /** 변동 수량 (항상 양수) */
    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

    /** 변동 전 재고 */
    @Column(name = "BEFORE_STOCK", nullable = false)
    private int beforeStock;

    /** 변동 후 재고 */
    @Column(name = "AFTER_STOCK", nullable = false)
    private int afterStock;

    /** 비고 (입고 사유, 지급 메모 등) */
    @Column(name = "NOTE", length = 255)
    private String note;

    /** 처리자 */
    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Builder
    private InventoryTransaction(InventoryItem inventoryItem, TransactionType transactionType,
                                 int quantity, int beforeStock, int afterStock,
                                 String note, String createdBy) {
        this.inventoryItem = inventoryItem;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.beforeStock = beforeStock;
        this.afterStock = afterStock;
        this.note = note;
        this.createdBy = createdBy;
    }

    /** 입고 이력 생성 */
    public static InventoryTransaction ofInbound(InventoryItem item, int quantity,
                                                 int beforeStock, int afterStock,
                                                 String createdBy, String note) {
        return InventoryTransaction.builder()
                .inventoryItem(item)
                .transactionType(TransactionType.INBOUND)
                .quantity(quantity)
                .beforeStock(beforeStock)
                .afterStock(afterStock)
                .createdBy(createdBy)
                .note(note)
                .build();
    }

    /** 출고(지급) 이력 생성 */
    public static InventoryTransaction ofOutbound(InventoryItem item, int quantity,
                                                  int beforeStock, int afterStock,
                                                  String createdBy, String note) {
        return InventoryTransaction.builder()
                .inventoryItem(item)
                .transactionType(TransactionType.OUTBOUND)
                .quantity(quantity)
                .beforeStock(beforeStock)
                .afterStock(afterStock)
                .createdBy(createdBy)
                .note(note)
                .build();
    }
}
