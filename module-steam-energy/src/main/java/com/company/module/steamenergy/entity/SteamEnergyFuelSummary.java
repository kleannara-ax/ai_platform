package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 연료비 요약 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 연료비 요약 (energy-fuel-summary.html)
 * <p>Table: steam_energy_fuel_summary
 * <p>PK: FUEL_SUMMARY_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_fuel_summary")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyFuelSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FUEL_SUMMARY_ID")
    private Long fuelSummaryId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "T1_PM2_PROD", precision = 18, scale = 6)
    private BigDecimal t1Pm2Prod;

    @Column(name = "T1_PM2_FUEL", precision = 18, scale = 6)
    private BigDecimal t1Pm2Fuel;

    @Column(name = "T1_PM3_PROD", precision = 18, scale = 6)
    private BigDecimal t1Pm3Prod;

    @Column(name = "T1_PM3_FUEL", precision = 18, scale = 6)
    private BigDecimal t1Pm3Fuel;

    @Column(name = "T1_TM3_PROD", precision = 18, scale = 6)
    private BigDecimal t1Tm3Prod;

    @Column(name = "T1_TM3_FUEL", precision = 18, scale = 6)
    private BigDecimal t1Tm3Fuel;

    @Column(name = "T1_TM4_PROD", precision = 18, scale = 6)
    private BigDecimal t1Tm4Prod;

    @Column(name = "T1_TM4_FUEL", precision = 18, scale = 6)
    private BigDecimal t1Tm4Fuel;

    @Column(name = "T1_TM5_PROD", precision = 18, scale = 6)
    private BigDecimal t1Tm5Prod;

    @Column(name = "T1_TM5_FUEL", precision = 18, scale = 6)
    private BigDecimal t1Tm5Fuel;

    @Column(name = "T2_LNG_USAGE", precision = 18, scale = 6)
    private BigDecimal t2LngUsage;

    @Column(name = "T2_LNG_PRICE", precision = 18, scale = 6)
    private BigDecimal t2LngPrice;

    @Column(name = "T2_LNG_COST", precision = 18, scale = 6)
    private BigDecimal t2LngCost;

    @Column(name = "T2_COMPOSITE_USAGE", precision = 18, scale = 6)
    private BigDecimal t2CompositeUsage;

    @Column(name = "T2_COMPOSITE_PRICE", precision = 18, scale = 6)
    private BigDecimal t2CompositePrice;

    @Column(name = "T2_COMPOSITE_COST", precision = 18, scale = 6)
    private BigDecimal t2CompositeCost;

    @Column(name = "T2_INCINERATOR_USAGE", precision = 18, scale = 6)
    private BigDecimal t2IncineratorUsage;

    @Column(name = "T2_INCINERATOR_PRICE", precision = 18, scale = 6)
    private BigDecimal t2IncineratorPrice;

    @Column(name = "T2_INCINERATOR_COST", precision = 18, scale = 6)
    private BigDecimal t2IncineratorCost;

    @Column(name = "T2_FLUID_USAGE", precision = 18, scale = 6)
    private BigDecimal t2FluidUsage;

    @Column(name = "T2_FLUID_PRICE", precision = 18, scale = 6)
    private BigDecimal t2FluidPrice;

    @Column(name = "T2_FLUID_COST", precision = 18, scale = 6)
    private BigDecimal t2FluidCost;

    @Column(name = "T2_EXT_USAGE", precision = 18, scale = 6)
    private BigDecimal t2ExtUsage;

    @Column(name = "T2_EXT_PRICE", precision = 18, scale = 6)
    private BigDecimal t2ExtPrice;

    @Column(name = "T2_EXT_COST", precision = 18, scale = 6)
    private BigDecimal t2ExtCost;

    @Column(name = "T2_LNG_ELEC", precision = 18, scale = 6)
    private BigDecimal t2LngElec;

    @Column(name = "T2_LNG_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2LngPenalty;

    @Column(name = "T2_LNG_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2LngOverprice;

    @Column(name = "T2_COMPOSITE_ELEC", precision = 18, scale = 6)
    private BigDecimal t2CompositeElec;

    @Column(name = "T2_COMPOSITE_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2CompositePenalty;

    @Column(name = "T2_COMPOSITE_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2CompositeOverprice;

    @Column(name = "T2_INCINERATOR_ELEC", precision = 18, scale = 6)
    private BigDecimal t2IncineratorElec;

    @Column(name = "T2_INCINERATOR_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2IncineratorPenalty;

    @Column(name = "T2_INCINERATOR_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2IncineratorOverprice;

    @Column(name = "T2_FLUID_ELEC", precision = 18, scale = 6)
    private BigDecimal t2FluidElec;

    @Column(name = "T2_FLUID_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2FluidPenalty;

    @Column(name = "T2_FLUID_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2FluidOverprice;

    @Column(name = "T2_EXT_ELEC", precision = 18, scale = 6)
    private BigDecimal t2ExtElec;

    @Column(name = "T2_EXT_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2ExtPenalty;

    @Column(name = "T2_EXT_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2ExtOverprice;

    @Column(name = "T2_SUBTOTAL_ELEC", precision = 18, scale = 6)
    private BigDecimal t2SubtotalElec;

    @Column(name = "T2_SUBTOTAL_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2SubtotalPenalty;

    @Column(name = "T2_SUBTOTAL_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2SubtotalOverprice;

    @Column(name = "T2_BURNER_LNG_ELEC", precision = 18, scale = 6)
    private BigDecimal t2BurnerLngElec;

    @Column(name = "T2_BURNER_LNG_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2BurnerLngPenalty;

    @Column(name = "T2_BURNER_LNG_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2BurnerLngOverprice;

    @Column(name = "T2_TOTAL_ELEC", precision = 18, scale = 6)
    private BigDecimal t2TotalElec;

    @Column(name = "T2_TOTAL_PENALTY", precision = 18, scale = 6)
    private BigDecimal t2TotalPenalty;

    @Column(name = "T2_TOTAL_OVERPRICE", precision = 18, scale = 6)
    private BigDecimal t2TotalOverprice;

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
    public void update(BigDecimal t1Pm2Prod, BigDecimal t1Pm2Fuel, BigDecimal t1Pm3Prod, BigDecimal t1Pm3Fuel, BigDecimal t1Tm3Prod, BigDecimal t1Tm3Fuel, BigDecimal t1Tm4Prod, BigDecimal t1Tm4Fuel, BigDecimal t1Tm5Prod, BigDecimal t1Tm5Fuel, BigDecimal t2LngUsage, BigDecimal t2LngPrice, BigDecimal t2LngCost, BigDecimal t2CompositeUsage, BigDecimal t2CompositePrice, BigDecimal t2CompositeCost, BigDecimal t2IncineratorUsage, BigDecimal t2IncineratorPrice, BigDecimal t2IncineratorCost, BigDecimal t2FluidUsage, BigDecimal t2FluidPrice, BigDecimal t2FluidCost, BigDecimal t2ExtUsage, BigDecimal t2ExtPrice, BigDecimal t2ExtCost, BigDecimal t2LngElec, BigDecimal t2LngPenalty, BigDecimal t2LngOverprice, BigDecimal t2CompositeElec, BigDecimal t2CompositePenalty, BigDecimal t2CompositeOverprice, BigDecimal t2IncineratorElec, BigDecimal t2IncineratorPenalty, BigDecimal t2IncineratorOverprice, BigDecimal t2FluidElec, BigDecimal t2FluidPenalty, BigDecimal t2FluidOverprice, BigDecimal t2ExtElec, BigDecimal t2ExtPenalty, BigDecimal t2ExtOverprice, BigDecimal t2SubtotalElec, BigDecimal t2SubtotalPenalty, BigDecimal t2SubtotalOverprice, BigDecimal t2BurnerLngElec, BigDecimal t2BurnerLngPenalty, BigDecimal t2BurnerLngOverprice, BigDecimal t2TotalElec, BigDecimal t2TotalPenalty, BigDecimal t2TotalOverprice, Long updatedBy) {
        this.t1Pm2Prod = t1Pm2Prod;
        this.t1Pm2Fuel = t1Pm2Fuel;
        this.t1Pm3Prod = t1Pm3Prod;
        this.t1Pm3Fuel = t1Pm3Fuel;
        this.t1Tm3Prod = t1Tm3Prod;
        this.t1Tm3Fuel = t1Tm3Fuel;
        this.t1Tm4Prod = t1Tm4Prod;
        this.t1Tm4Fuel = t1Tm4Fuel;
        this.t1Tm5Prod = t1Tm5Prod;
        this.t1Tm5Fuel = t1Tm5Fuel;
        this.t2LngUsage = t2LngUsage;
        this.t2LngPrice = t2LngPrice;
        this.t2LngCost = t2LngCost;
        this.t2CompositeUsage = t2CompositeUsage;
        this.t2CompositePrice = t2CompositePrice;
        this.t2CompositeCost = t2CompositeCost;
        this.t2IncineratorUsage = t2IncineratorUsage;
        this.t2IncineratorPrice = t2IncineratorPrice;
        this.t2IncineratorCost = t2IncineratorCost;
        this.t2FluidUsage = t2FluidUsage;
        this.t2FluidPrice = t2FluidPrice;
        this.t2FluidCost = t2FluidCost;
        this.t2ExtUsage = t2ExtUsage;
        this.t2ExtPrice = t2ExtPrice;
        this.t2ExtCost = t2ExtCost;
        this.t2LngElec = t2LngElec;
        this.t2LngPenalty = t2LngPenalty;
        this.t2LngOverprice = t2LngOverprice;
        this.t2CompositeElec = t2CompositeElec;
        this.t2CompositePenalty = t2CompositePenalty;
        this.t2CompositeOverprice = t2CompositeOverprice;
        this.t2IncineratorElec = t2IncineratorElec;
        this.t2IncineratorPenalty = t2IncineratorPenalty;
        this.t2IncineratorOverprice = t2IncineratorOverprice;
        this.t2FluidElec = t2FluidElec;
        this.t2FluidPenalty = t2FluidPenalty;
        this.t2FluidOverprice = t2FluidOverprice;
        this.t2ExtElec = t2ExtElec;
        this.t2ExtPenalty = t2ExtPenalty;
        this.t2ExtOverprice = t2ExtOverprice;
        this.t2SubtotalElec = t2SubtotalElec;
        this.t2SubtotalPenalty = t2SubtotalPenalty;
        this.t2SubtotalOverprice = t2SubtotalOverprice;
        this.t2BurnerLngElec = t2BurnerLngElec;
        this.t2BurnerLngPenalty = t2BurnerLngPenalty;
        this.t2BurnerLngOverprice = t2BurnerLngOverprice;
        this.t2TotalElec = t2TotalElec;
        this.t2TotalPenalty = t2TotalPenalty;
        this.t2TotalOverprice = t2TotalOverprice;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
