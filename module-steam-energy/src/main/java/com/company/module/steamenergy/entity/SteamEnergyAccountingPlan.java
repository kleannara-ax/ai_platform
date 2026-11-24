package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 회계비용 계획 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 회계비용 계획 (energy-accounting-plan.html)
 * <p>Table: steam_energy_accounting_plan
 * <p>PK: ACCOUNTING_PLAN_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_accounting_plan")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyAccountingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACCOUNTING_PLAN_ID")
    private Long accountingPlanId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "COMPOSITE_STEAM_BUY", precision = 18, scale = 6)
    private BigDecimal compositeSteamBuy;

    @Column(name = "ZESCO_STEAM_BUY", precision = 18, scale = 6)
    private BigDecimal zescoSteamBuy;

    @Column(name = "LNG_BUY", precision = 18, scale = 6)
    private BigDecimal lngBuy;

    @Column(name = "INCINERATOR_COST", precision = 18, scale = 6)
    private BigDecimal incineratorCost;

    @Column(name = "FLUID_COST", precision = 18, scale = 6)
    private BigDecimal fluidCost;

    @Column(name = "ZESCO_OFFSET", precision = 18, scale = 6)
    private BigDecimal zescoOffset;

    @Column(name = "ZESCO_PENALTY", precision = 18, scale = 6)
    private BigDecimal zescoPenalty;

    @Column(name = "ZESCO_ADVANCE_REPAY", precision = 18, scale = 6)
    private BigDecimal zescoAdvanceRepay;

    @Column(name = "COMPOSITE_LNG_WATER", precision = 18, scale = 6)
    private BigDecimal compositeLngWater;

    @Column(name = "COMPOSITE_INVEST", precision = 18, scale = 6)
    private BigDecimal compositeInvest;

    @Column(name = "COMPOSITE_INCOME", precision = 18, scale = 6)
    private BigDecimal compositeIncome;

    @Column(name = "LAND_LEASE", precision = 18, scale = 6)
    private BigDecimal landLease;

    @Column(name = "PAPER_FUEL", precision = 18, scale = 6)
    private BigDecimal paperFuel;

    @Column(name = "LIVING_FUEL", precision = 18, scale = 6)
    private BigDecimal livingFuel;

    @Column(name = "FUEL_VS_PLAN", precision = 18, scale = 6)
    private BigDecimal fuelVsPlan;

    @Column(name = "PAPER_PROD_TON", precision = 18, scale = 6)
    private BigDecimal paperProdTon;

    @Column(name = "TISSUE_PROD_TON", precision = 18, scale = 6)
    private BigDecimal tissueProdTon;

    @Column(name = "STEAM_PROD_TON", precision = 18, scale = 6)
    private BigDecimal steamProdTon;

    @Column(name = "LNG_BURNER_COST", precision = 18, scale = 6)
    private BigDecimal lngBurnerCost;

    @Column(name = "COMPOSITE_NET", precision = 18, scale = 6)
    private BigDecimal compositeNet;

    @Column(name = "ZESCO_NET", precision = 18, scale = 6)
    private BigDecimal zescoNet;

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
    public void update(BigDecimal compositeSteamBuy, BigDecimal zescoSteamBuy, BigDecimal lngBuy, BigDecimal incineratorCost, BigDecimal fluidCost, BigDecimal zescoOffset, BigDecimal zescoPenalty, BigDecimal zescoAdvanceRepay, BigDecimal compositeLngWater, BigDecimal compositeInvest, BigDecimal compositeIncome, BigDecimal landLease, BigDecimal paperFuel, BigDecimal livingFuel, BigDecimal fuelVsPlan, BigDecimal paperProdTon, BigDecimal tissueProdTon, BigDecimal steamProdTon, BigDecimal lngBurnerCost, BigDecimal compositeNet, BigDecimal zescoNet, Long updatedBy) {
        this.compositeSteamBuy = compositeSteamBuy;
        this.zescoSteamBuy = zescoSteamBuy;
        this.lngBuy = lngBuy;
        this.incineratorCost = incineratorCost;
        this.fluidCost = fluidCost;
        this.zescoOffset = zescoOffset;
        this.zescoPenalty = zescoPenalty;
        this.zescoAdvanceRepay = zescoAdvanceRepay;
        this.compositeLngWater = compositeLngWater;
        this.compositeInvest = compositeInvest;
        this.compositeIncome = compositeIncome;
        this.landLease = landLease;
        this.paperFuel = paperFuel;
        this.livingFuel = livingFuel;
        this.fuelVsPlan = fuelVsPlan;
        this.paperProdTon = paperProdTon;
        this.tissueProdTon = tissueProdTon;
        this.steamProdTon = steamProdTon;
        this.lngBurnerCost = lngBurnerCost;
        this.compositeNet = compositeNet;
        this.zescoNet = zescoNet;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
