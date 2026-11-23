package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 연료비 사업계획 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 연료비 사업계획(안) (energy-fuel-plan.html)
 * <p>Table: steam_energy_fuel_plan
 * <p>PK: FUEL_PLAN_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_fuel_plan")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyFuelPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FUEL_PLAN_ID")
    private Long fuelPlanId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "PAPER_PROD_TON", precision = 18, scale = 6)
    private BigDecimal paperProdTon;

    @Column(name = "TISSUE_PROD_TON", precision = 18, scale = 6)
    private BigDecimal tissueProdTon;

    @Column(name = "PAPER_UNIT", precision = 18, scale = 6)
    private BigDecimal paperUnit;

    @Column(name = "TISSUE_UNIT", precision = 18, scale = 6)
    private BigDecimal tissueUnit;

    @Column(name = "PAPER_TOTAL_STEAM", precision = 18, scale = 6)
    private BigDecimal paperTotalSteam;

    @Column(name = "TISSUE_TOTAL_STEAM", precision = 18, scale = 6)
    private BigDecimal tissueTotalSteam;

    @Column(name = "ETC_DISPERSER", precision = 18, scale = 6)
    private BigDecimal etcDisperser;

    @Column(name = "ETC_TOC", precision = 18, scale = 6)
    private BigDecimal etcToc;

    @Column(name = "OPERATING_DAYS", precision = 18, scale = 6)
    private BigDecimal operatingDays;

    @Column(name = "PROD_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal prodIncinerator;

    @Column(name = "PROD_COMPOSITE", precision = 18, scale = 6)
    private BigDecimal prodComposite;

    @Column(name = "PROD_EXT_BOILER", precision = 18, scale = 6)
    private BigDecimal prodExtBoiler;

    @Column(name = "PROD_FLUID", precision = 18, scale = 6)
    private BigDecimal prodFluid;

    @Column(name = "LNG_COMPOSITE", precision = 18, scale = 6)
    private BigDecimal lngComposite;

    @Column(name = "LNG_TM3", precision = 18, scale = 6)
    private BigDecimal lngTm3;

    @Column(name = "LNG_TM4", precision = 18, scale = 6)
    private BigDecimal lngTm4;

    @Column(name = "LNG_TM5", precision = 18, scale = 6)
    private BigDecimal lngTm5;

    @Column(name = "LNG_PM3", precision = 18, scale = 6)
    private BigDecimal lngPm3;

    @Column(name = "PRICE_LNG", precision = 18, scale = 6)
    private BigDecimal priceLng;

    @Column(name = "PRICE_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal priceIncinerator;

    @Column(name = "PRICE_COMPOSITE", precision = 18, scale = 6)
    private BigDecimal priceComposite;

    @Column(name = "PRICE_EXT_BOILER", precision = 18, scale = 6)
    private BigDecimal priceExtBoiler;

    @Column(name = "PRICE_FLUID", precision = 18, scale = 6)
    private BigDecimal priceFluid;

    @Column(name = "OPEX_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal opexIncinerator;

    @Column(name = "OPEX_FLUID", precision = 18, scale = 6)
    private BigDecimal opexFluid;

    @Column(name = "PAY_EXT_ADVANCE", precision = 18, scale = 6)
    private BigDecimal payExtAdvance;

    @Column(name = "PAY_EXT_PENALTY", precision = 18, scale = 6)
    private BigDecimal payExtPenalty;

    @Column(name = "PAY_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal payIncinerator;

    @Column(name = "PAY_FLUID", precision = 18, scale = 6)
    private BigDecimal payFluid;

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
    public void update(BigDecimal paperProdTon, BigDecimal tissueProdTon, BigDecimal paperUnit, BigDecimal tissueUnit, BigDecimal paperTotalSteam, BigDecimal tissueTotalSteam, BigDecimal etcDisperser, BigDecimal etcToc, BigDecimal operatingDays, BigDecimal prodIncinerator, BigDecimal prodComposite, BigDecimal prodExtBoiler, BigDecimal prodFluid, BigDecimal lngComposite, BigDecimal lngTm3, BigDecimal lngTm4, BigDecimal lngTm5, BigDecimal lngPm3, BigDecimal priceLng, BigDecimal priceIncinerator, BigDecimal priceComposite, BigDecimal priceExtBoiler, BigDecimal priceFluid, BigDecimal opexIncinerator, BigDecimal opexFluid, BigDecimal payExtAdvance, BigDecimal payExtPenalty, BigDecimal payIncinerator, BigDecimal payFluid, Long updatedBy) {
        this.paperProdTon = paperProdTon;
        this.tissueProdTon = tissueProdTon;
        this.paperUnit = paperUnit;
        this.tissueUnit = tissueUnit;
        this.paperTotalSteam = paperTotalSteam;
        this.tissueTotalSteam = tissueTotalSteam;
        this.etcDisperser = etcDisperser;
        this.etcToc = etcToc;
        this.operatingDays = operatingDays;
        this.prodIncinerator = prodIncinerator;
        this.prodComposite = prodComposite;
        this.prodExtBoiler = prodExtBoiler;
        this.prodFluid = prodFluid;
        this.lngComposite = lngComposite;
        this.lngTm3 = lngTm3;
        this.lngTm4 = lngTm4;
        this.lngTm5 = lngTm5;
        this.lngPm3 = lngPm3;
        this.priceLng = priceLng;
        this.priceIncinerator = priceIncinerator;
        this.priceComposite = priceComposite;
        this.priceExtBoiler = priceExtBoiler;
        this.priceFluid = priceFluid;
        this.opexIncinerator = opexIncinerator;
        this.opexFluid = opexFluid;
        this.payExtAdvance = payExtAdvance;
        this.payExtPenalty = payExtPenalty;
        this.payIncinerator = payIncinerator;
        this.payFluid = payFluid;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
