package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 연료비 배분비율(보일러별) — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 연료비 배분비율 › 보일러별 종합 (energy-fuel-ratio.html t01~t03)
 * <p>Table: steam_energy_ratio_boiler
 * <p>PK: RATIO_BOILER_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_ratio_boiler")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioBoiler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RATIO_BOILER_ID")
    private Long ratioBoilerId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "STEAM_LNG_BOILER", precision = 18, scale = 6)
    private BigDecimal steamLngBoiler;

    @Column(name = "STEAM_COMPOSITE", precision = 18, scale = 6)
    private BigDecimal steamComposite;

    @Column(name = "STEAM_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal steamIncinerator;

    @Column(name = "STEAM_FLUID", precision = 18, scale = 6)
    private BigDecimal steamFluid;

    @Column(name = "STEAM_EXT_BOILER", precision = 18, scale = 6)
    private BigDecimal steamExtBoiler;

    @Column(name = "FLUID_VENT_TON", precision = 18, scale = 6)
    private BigDecimal fluidVentTon;

    @Column(name = "COMPOSITE_PENALTY_TON", precision = 18, scale = 6)
    private BigDecimal compositePenaltyTon;

    @Column(name = "COMPOSITE_GUARANTEE_TON", precision = 18, scale = 6)
    private BigDecimal compositeGuaranteeTon;

    @Column(name = "COST_LNG_BOILER", precision = 18, scale = 6)
    private BigDecimal costLngBoiler;

    @Column(name = "COST_COMPOSITE", precision = 18, scale = 6)
    private BigDecimal costComposite;

    @Column(name = "COST_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal costIncinerator;

    @Column(name = "COST_FLUID", precision = 18, scale = 6)
    private BigDecimal costFluid;

    @Column(name = "COST_EXT_BOILER", precision = 18, scale = 6)
    private BigDecimal costExtBoiler;

    @Column(name = "FLUID_INTERNAL_VENT_COST", precision = 18, scale = 6)
    private BigDecimal fluidInternalVentCost;

    @Column(name = "PRICE_LNG_BOILER", precision = 18, scale = 6)
    private BigDecimal priceLngBoiler;

    @Column(name = "PRICE_COMPOSITE", precision = 18, scale = 6)
    private BigDecimal priceComposite;

    @Column(name = "PRICE_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal priceIncinerator;

    @Column(name = "PRICE_FLUID", precision = 18, scale = 6)
    private BigDecimal priceFluid;

    @Column(name = "PRICE_EXT_BOILER", precision = 18, scale = 6)
    private BigDecimal priceExtBoiler;

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
    public void update(BigDecimal steamLngBoiler, BigDecimal steamComposite, BigDecimal steamIncinerator, BigDecimal steamFluid, BigDecimal steamExtBoiler, BigDecimal fluidVentTon, BigDecimal compositePenaltyTon, BigDecimal compositeGuaranteeTon, BigDecimal costLngBoiler, BigDecimal costComposite, BigDecimal costIncinerator, BigDecimal costFluid, BigDecimal costExtBoiler, BigDecimal fluidInternalVentCost, BigDecimal priceLngBoiler, BigDecimal priceComposite, BigDecimal priceIncinerator, BigDecimal priceFluid, BigDecimal priceExtBoiler, Long updatedBy) {
        this.steamLngBoiler = steamLngBoiler;
        this.steamComposite = steamComposite;
        this.steamIncinerator = steamIncinerator;
        this.steamFluid = steamFluid;
        this.steamExtBoiler = steamExtBoiler;
        this.fluidVentTon = fluidVentTon;
        this.compositePenaltyTon = compositePenaltyTon;
        this.compositeGuaranteeTon = compositeGuaranteeTon;
        this.costLngBoiler = costLngBoiler;
        this.costComposite = costComposite;
        this.costIncinerator = costIncinerator;
        this.costFluid = costFluid;
        this.costExtBoiler = costExtBoiler;
        this.fluidInternalVentCost = fluidInternalVentCost;
        this.priceLngBoiler = priceLngBoiler;
        this.priceComposite = priceComposite;
        this.priceIncinerator = priceIncinerator;
        this.priceFluid = priceFluid;
        this.priceExtBoiler = priceExtBoiler;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
