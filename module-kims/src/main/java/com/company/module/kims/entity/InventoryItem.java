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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // ----------------------------------------------------------------
    // 세부 구분(신형/구형, 제조사 등) 지급/입고 반영
    // ----------------------------------------------------------------
    /**
     * 비고(remark)에 세부 구분 라벨("신형", "구형", "레노버" 등)과 그 뒤의 숫자가
     * 함께 존재할 때만, 그 숫자를 delta 만큼 증감시켜 remark 를 갱신한다.
     * <p>대시보드 재고 현황 막대그래프가 remark 의 세부 수량 텍스트를 파싱해서 그리므로,
     * 지급/입고 시점에 이 메서드로 remark 도 함께 갱신해야 세부 구분 막대가 실제
     * currentStock 변화와 어긋나지 않는다(어긋나더라도 화면단 보정 로직이 있어 전체
     * 합계는 항상 currentStock 과 맞춰지지만, 세부 구분별 비율은 부정확해질 수 있다).
     * <p>remark 에 해당 라벨의 숫자 패턴이 없으면(예: 자유 서술형 remark) 아무 것도
     * 하지 않고 조용히 무시한다 — 세부 구분을 추적할 수 없는 품목이므로 무시가 안전하다.
     *
     * @param label 세부 구분 라벨 (예: "신형", "구형", "레노버", "갤럭시", "대여")
     * @param delta 증감량 (지급 시 음수, 지급 취소/원복 시 양수)
     */
    public void adjustRemarkSegment(String label, int delta) {
        if (this.remark == null || this.remark.isBlank() || label == null || label.isBlank() || delta == 0) {
            return;
        }
        Pattern p = Pattern.compile(Pattern.quote(label) + "\\s*(\\d+)");
        Matcher m = p.matcher(this.remark);
        if (!m.find()) {
            return;
        }
        int current = Integer.parseInt(m.group(1));
        int next = Math.max(0, current + delta);
        this.remark = this.remark.substring(0, m.start(1)) + next + this.remark.substring(m.end(1));
    }
}
