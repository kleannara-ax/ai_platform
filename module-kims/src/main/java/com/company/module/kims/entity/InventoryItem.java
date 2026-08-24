package com.company.module.kims.entity;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전산소모품 품목 (마스터).
 * <p>품목별 현재 재고를 보유하며, 입고 시 증가하고 지급 시 감소한다.
 */
@Entity
@Table(name = "inventory_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ITEM_ID")
    private Long itemId;

    @Column(name = "ITEM_NAME", nullable = false, length = 100, unique = true)
    private String itemName;

    @Column(name = "CATEGORY", nullable = false, length = 50)
    private String category;

    /** 현재 재고 수량 */
    @Column(name = "CURRENT_STOCK", nullable = false)
    private int currentStock;

    /** 최소 재고 기준 (이하이면 재고 부족으로 간주) */
    @Column(name = "MIN_STOCK", nullable = false)
    private int minStock;

    /** 단위 (개, 대, EA 등) */
    @Column(name = "UNIT", nullable = false, length = 20)
    private String unit;

    @Column(name = "REMARK", length = 255)
    private String remark;

    @Builder
    private InventoryItem(String itemName, String category, int currentStock,
                          int minStock, String unit, String remark) {
        this.itemName = itemName;
        this.category = category;
        this.currentStock = currentStock;
        this.minStock = minStock;
        this.unit = unit;
        this.remark = remark;
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    /**
     * 재고를 입고 수량만큼 증가시킨다.
     *
     * @param quantity 증가시킬 수량 (양수)
     */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "입고 수량은 1 이상이어야 합니다. 입력값=" + quantity);
        }
        this.currentStock += quantity;
    }

    /**
     * 재고를 지급 수량만큼 차감한다.
     * <p>현재 재고보다 많은 수량을 차감하려 하면 예외가 발생한다.
     *
     * @param quantity 차감할 수량 (양수)
     * @throws BusinessException 수량이 0 이하이거나 재고가 부족한 경우
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "지급 수량은 1 이상이어야 합니다. 입력값=" + quantity);
        }
        if (quantity > this.currentStock) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "재고가 부족합니다. 품목=" + this.itemName
                            + ", 현재고=" + this.currentStock + ", 요청수량=" + quantity);
        }
        this.currentStock -= quantity;
    }

    /**
     * 재고부족 여부. 업무 정책상 <b>재고가 0(품절)</b>일 때만 재고부족으로 표시한다.
     * (최소재고 minStock 은 참고용으로만 보관하고 이 판정에는 사용하지 않는다.)
     */
    public boolean isBelowMinStock() {
        return this.currentStock <= 0;
    }

    /** 품목 기본정보 수정 */
    public void update(String category, int minStock, String unit, String remark) {
        this.category = category;
        this.minStock = minStock;
        this.unit = unit;
        this.remark = remark;
    }
}
