package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 이동계획 월 기준값 — 연·월별 한 행. 비운 칸은 화면이 기본값(단가 입력·실적에서 구한 값)을 쓴다.
 *
 * <p>화면: 스팀에너지관리 › 이동계획 수립 (energy-move-plan.html) — 기준값 영역
 * <p>Table: steam_move_plan_month
 * <p>PK: MOVE_PLAN_MONTH_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_move_plan_month")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MOVE_PLAN_MONTH_ID")
    private Long movePlanMonthId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "ACTUAL_UNTIL_DAY")
    private Integer actualUntilDay;

    @Column(name = "ADJUST_YN", length = 1)
    private String adjustYn;

    @Column(name = "WASTE2_BILL_CAP_TON", precision = 18, scale = 6)
    private BigDecimal waste2BillCapTon;

    @Column(name = "COMBO_GUARANTEE_TON", precision = 18, scale = 6)
    private BigDecimal comboGuaranteeTon;

    @Column(name = "COMBO_BSC_PRICE", precision = 18, scale = 6)
    private BigDecimal comboBscPrice;

    @Column(name = "COMBO_ASC_PRICE", precision = 18, scale = 6)
    private BigDecimal comboAscPrice;

    @Column(name = "FLUID_BILL_TON_HOUR", precision = 18, scale = 6)
    private BigDecimal fluidBillTonHour;

    @Column(name = "FLUID_IMPROVEMENT_COST", precision = 18, scale = 6)
    private BigDecimal fluidImprovementCost;

    @Column(name = "LNG_M3_PER_TON", precision = 18, scale = 6)
    private BigDecimal lngM3PerTon;

    @Column(name = "LNG_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal lngUnitPrice;

    @Column(name = "NOTE", length = 500)
    private String note;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(KST);
        if (this.deletedYn == null) this.deletedYn = "N";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(KST);
    }

    /** 행 전체 값 갱신. null 은 빈 값으로 저장한다. */
    public void update(Integer actualUntilDay, String adjustYn, BigDecimal waste2BillCapTon, BigDecimal comboGuaranteeTon, BigDecimal comboBscPrice, BigDecimal comboAscPrice, BigDecimal fluidBillTonHour, BigDecimal fluidImprovementCost, BigDecimal lngM3PerTon, BigDecimal lngUnitPrice, String note, Long updatedBy) {
        this.actualUntilDay = actualUntilDay;
        this.adjustYn = adjustYn;
        this.waste2BillCapTon = waste2BillCapTon;
        this.comboGuaranteeTon = comboGuaranteeTon;
        this.comboBscPrice = comboBscPrice;
        this.comboAscPrice = comboAscPrice;
        this.fluidBillTonHour = fluidBillTonHour;
        this.fluidImprovementCost = fluidImprovementCost;
        this.lngM3PerTon = lngM3PerTon;
        this.lngUnitPrice = lngUnitPrice;
        this.note = note;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 일괄 저장에서 목록에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, updatedBy);
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now(KST);
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
