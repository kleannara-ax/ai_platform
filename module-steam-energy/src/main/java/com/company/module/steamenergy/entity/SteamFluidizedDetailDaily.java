package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 세부 운영내역 (일별) — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 세부 운영내역 · 유동상 운전일지 (unit-fluidized-detail.html, fluidized-daily-log.html)
 * <p>Table: steam_fluidized_detail_daily
 * <p>PK: DETAIL_DAILY_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_fluidized_detail_daily")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DETAIL_DAILY_ID")
    private Long detailDailyId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "RUN_HOURS", precision = 18, scale = 6)
    private BigDecimal runHours;

    @Column(name = "STEAM_PRODUCED_TON", precision = 18, scale = 6)
    private BigDecimal steamProducedTon;

    @Column(name = "SLUDGE_BURN_TON", precision = 18, scale = 6)
    private BigDecimal sludgeBurnTon;

    @Column(name = "SRF_BURN_TON", precision = 18, scale = 6)
    private BigDecimal srfBurnTon;

    @Column(name = "STEAM_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal steamUnitPrice;

    @Column(name = "STOP_HOUR_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal stopHourUnitPrice;

    @Column(name = "AMMONIUM_CARBONATE_IN_KG", precision = 18, scale = 6)
    private BigDecimal ammoniumCarbonateInKg;

    @Column(name = "AMMONIUM_CARBONATE_USE_KG", precision = 18, scale = 6)
    private BigDecimal ammoniumCarbonateUseKg;

    @Column(name = "BOILER_CHEM_IN_KG", precision = 18, scale = 6)
    private BigDecimal boilerChemInKg;

    @Column(name = "BOILER_CHEM_USE_KG", precision = 18, scale = 6)
    private BigDecimal boilerChemUseKg;

    @Column(name = "ACTIVATED_CARBON_IN_KG", precision = 18, scale = 6)
    private BigDecimal activatedCarbonInKg;

    @Column(name = "ACTIVATED_CARBON_USE_KG", precision = 18, scale = 6)
    private BigDecimal activatedCarbonUseKg;

    @Column(name = "CAUSTIC_SODA_IN_KG", precision = 18, scale = 6)
    private BigDecimal causticSodaInKg;

    @Column(name = "CAUSTIC_SODA_USE_KG", precision = 18, scale = 6)
    private BigDecimal causticSodaUseKg;

    @Column(name = "SLAKED_LIME_IN_KG", precision = 18, scale = 6)
    private BigDecimal slakedLimeInKg;

    @Column(name = "SLAKED_LIME_USE_KG", precision = 18, scale = 6)
    private BigDecimal slakedLimeUseKg;

    @Column(name = "SALT_IN_KG", precision = 18, scale = 6)
    private BigDecimal saltInKg;

    @Column(name = "SALT_USE_KG", precision = 18, scale = 6)
    private BigDecimal saltUseKg;

    @Column(name = "DIESEL_IN_L", precision = 18, scale = 6)
    private BigDecimal dieselInL;

    @Column(name = "DIESEL_USE_L", precision = 18, scale = 6)
    private BigDecimal dieselUseL;

    @Column(name = "SILICA_SAND_IN_KG", precision = 18, scale = 6)
    private BigDecimal silicaSandInKg;

    @Column(name = "SILICA_SAND_USE_KG", precision = 18, scale = 6)
    private BigDecimal silicaSandUseKg;

    @Column(name = "ACTIVATED_CARBON_EXTRA_IN_KG", precision = 18, scale = 6)
    private BigDecimal activatedCarbonExtraInKg;

    @Column(name = "EMERGENCY_DIESEL_IN_L", precision = 18, scale = 6)
    private BigDecimal emergencyDieselInL;

    @Column(name = "SRF_IN_TON", precision = 18, scale = 6)
    private BigDecimal srfInTon;

    @Column(name = "FLY_ASH_GENERATED_TON", precision = 18, scale = 6)
    private BigDecimal flyAshGeneratedTon;

    @Column(name = "FLY_ASH_OUT_01_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut01Ton;

    @Column(name = "FLY_ASH_OUT_02_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut02Ton;

    @Column(name = "FLY_ASH_OUT_03_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut03Ton;

    @Column(name = "FLY_ASH_OUT_04_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut04Ton;

    @Column(name = "FLY_ASH_OUT_05_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut05Ton;

    @Column(name = "FLY_ASH_OUT_06_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut06Ton;

    @Column(name = "FLY_ASH_OUT_07_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut07Ton;

    @Column(name = "FLY_ASH_OUT_08_TON", precision = 18, scale = 6)
    private BigDecimal flyAshOut08Ton;

    @Column(name = "BOTTOM_ASH_GENERATED_TON", precision = 18, scale = 6)
    private BigDecimal bottomAshGeneratedTon;

    @Column(name = "BOTTOM_ASH_OUT_01_TON", precision = 18, scale = 6)
    private BigDecimal bottomAshOut01Ton;

    @Column(name = "BOTTOM_ASH_OUT_02_TON", precision = 18, scale = 6)
    private BigDecimal bottomAshOut02Ton;

    @Column(name = "BOTTOM_ASH_OUT_03_TON", precision = 18, scale = 6)
    private BigDecimal bottomAshOut03Ton;

    @Column(name = "BOTTOM_ASH_OUT_04_TON", precision = 18, scale = 6)
    private BigDecimal bottomAshOut04Ton;

    @Column(name = "POWER_MAIN_KWH", precision = 18, scale = 6)
    private BigDecimal powerMainKwh;

    @Column(name = "POWER_ASH_VENT_FILTER_KWH", precision = 18, scale = 6)
    private BigDecimal powerAshVentFilterKwh;

    @Column(name = "POWER_BAG_FILTER_KWH", precision = 18, scale = 6)
    private BigDecimal powerBagFilterKwh;

    @Column(name = "POWER_SCRUBBER_KWH", precision = 18, scale = 6)
    private BigDecimal powerScrubberKwh;

    @Column(name = "POWER_CARBON_FEED_KWH", precision = 18, scale = 6)
    private BigDecimal powerCarbonFeedKwh;

    @Column(name = "POWER_LIME_FEED_KWH", precision = 18, scale = 6)
    private BigDecimal powerLimeFeedKwh;

    @Column(name = "POWER_UREA_KWH", precision = 18, scale = 6)
    private BigDecimal powerUreaKwh;

    @Column(name = "POWER_FD_FAN_KWH", precision = 18, scale = 6)
    private BigDecimal powerFdFanKwh;

    @Column(name = "REUSE_WATER_TON", precision = 18, scale = 6)
    private BigDecimal reuseWaterTon;

    @Column(name = "RIVERBED_WATER_TON", precision = 18, scale = 6)
    private BigDecimal riverbedWaterTon;

    @Column(name = "CONDENSATE_TON", precision = 18, scale = 6)
    private BigDecimal condensateTon;

    @Column(name = "BOILER_FEED_WATER_TON", precision = 18, scale = 6)
    private BigDecimal boilerFeedWaterTon;

    @Column(name = "WASTE_WATER_TON", precision = 18, scale = 6)
    private BigDecimal wasteWaterTon;

    @Column(name = "SOFT_WATER_TON", precision = 18, scale = 6)
    private BigDecimal softWaterTon;

    @Column(name = "SLUDGE_OUTSOURCE_01_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource01Ton;

    @Column(name = "SLUDGE_OUTSOURCE_02_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource02Ton;

    @Column(name = "SLUDGE_OUTSOURCE_03_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource03Ton;

    @Column(name = "SLUDGE_OUTSOURCE_04_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource04Ton;

    @Column(name = "SLUDGE_OUTSOURCE_05_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource05Ton;

    @Column(name = "SLUDGE_OUTSOURCE_06_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource06Ton;

    @Column(name = "SLUDGE_OUTSOURCE_07_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource07Ton;

    @Column(name = "SLUDGE_OUTSOURCE_08_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource08Ton;

    @Column(name = "SLUDGE_OUTSOURCE_09_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource09Ton;

    @Column(name = "SLUDGE_OUTSOURCE_10_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource10Ton;

    @Column(name = "SLUDGE_OUTSOURCE_11_TON", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource11Ton;

    @Column(name = "INSPECTION_FEE_QTY", precision = 18, scale = 6)
    private BigDecimal inspectionFeeQty;

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
    public void update(BigDecimal runHours, BigDecimal steamProducedTon, BigDecimal sludgeBurnTon, BigDecimal srfBurnTon, BigDecimal steamUnitPrice, BigDecimal stopHourUnitPrice, BigDecimal ammoniumCarbonateInKg, BigDecimal ammoniumCarbonateUseKg, BigDecimal boilerChemInKg, BigDecimal boilerChemUseKg, BigDecimal activatedCarbonInKg, BigDecimal activatedCarbonUseKg, BigDecimal causticSodaInKg, BigDecimal causticSodaUseKg, BigDecimal slakedLimeInKg, BigDecimal slakedLimeUseKg, BigDecimal saltInKg, BigDecimal saltUseKg, BigDecimal dieselInL, BigDecimal dieselUseL, BigDecimal silicaSandInKg, BigDecimal silicaSandUseKg, BigDecimal activatedCarbonExtraInKg, BigDecimal emergencyDieselInL, BigDecimal srfInTon, BigDecimal flyAshGeneratedTon, BigDecimal flyAshOut01Ton, BigDecimal flyAshOut02Ton, BigDecimal flyAshOut03Ton, BigDecimal flyAshOut04Ton, BigDecimal flyAshOut05Ton, BigDecimal flyAshOut06Ton, BigDecimal flyAshOut07Ton, BigDecimal flyAshOut08Ton, BigDecimal bottomAshGeneratedTon, BigDecimal bottomAshOut01Ton, BigDecimal bottomAshOut02Ton, BigDecimal bottomAshOut03Ton, BigDecimal bottomAshOut04Ton, BigDecimal powerMainKwh, BigDecimal powerAshVentFilterKwh, BigDecimal powerBagFilterKwh, BigDecimal powerScrubberKwh, BigDecimal powerCarbonFeedKwh, BigDecimal powerLimeFeedKwh, BigDecimal powerUreaKwh, BigDecimal powerFdFanKwh, BigDecimal reuseWaterTon, BigDecimal riverbedWaterTon, BigDecimal condensateTon, BigDecimal boilerFeedWaterTon, BigDecimal wasteWaterTon, BigDecimal softWaterTon, BigDecimal sludgeOutsource01Ton, BigDecimal sludgeOutsource02Ton, BigDecimal sludgeOutsource03Ton, BigDecimal sludgeOutsource04Ton, BigDecimal sludgeOutsource05Ton, BigDecimal sludgeOutsource06Ton, BigDecimal sludgeOutsource07Ton, BigDecimal sludgeOutsource08Ton, BigDecimal sludgeOutsource09Ton, BigDecimal sludgeOutsource10Ton, BigDecimal sludgeOutsource11Ton, BigDecimal inspectionFeeQty, Long updatedBy) {
        this.runHours = runHours;
        this.steamProducedTon = steamProducedTon;
        this.sludgeBurnTon = sludgeBurnTon;
        this.srfBurnTon = srfBurnTon;
        this.steamUnitPrice = steamUnitPrice;
        this.stopHourUnitPrice = stopHourUnitPrice;
        this.ammoniumCarbonateInKg = ammoniumCarbonateInKg;
        this.ammoniumCarbonateUseKg = ammoniumCarbonateUseKg;
        this.boilerChemInKg = boilerChemInKg;
        this.boilerChemUseKg = boilerChemUseKg;
        this.activatedCarbonInKg = activatedCarbonInKg;
        this.activatedCarbonUseKg = activatedCarbonUseKg;
        this.causticSodaInKg = causticSodaInKg;
        this.causticSodaUseKg = causticSodaUseKg;
        this.slakedLimeInKg = slakedLimeInKg;
        this.slakedLimeUseKg = slakedLimeUseKg;
        this.saltInKg = saltInKg;
        this.saltUseKg = saltUseKg;
        this.dieselInL = dieselInL;
        this.dieselUseL = dieselUseL;
        this.silicaSandInKg = silicaSandInKg;
        this.silicaSandUseKg = silicaSandUseKg;
        this.activatedCarbonExtraInKg = activatedCarbonExtraInKg;
        this.emergencyDieselInL = emergencyDieselInL;
        this.srfInTon = srfInTon;
        this.flyAshGeneratedTon = flyAshGeneratedTon;
        this.flyAshOut01Ton = flyAshOut01Ton;
        this.flyAshOut02Ton = flyAshOut02Ton;
        this.flyAshOut03Ton = flyAshOut03Ton;
        this.flyAshOut04Ton = flyAshOut04Ton;
        this.flyAshOut05Ton = flyAshOut05Ton;
        this.flyAshOut06Ton = flyAshOut06Ton;
        this.flyAshOut07Ton = flyAshOut07Ton;
        this.flyAshOut08Ton = flyAshOut08Ton;
        this.bottomAshGeneratedTon = bottomAshGeneratedTon;
        this.bottomAshOut01Ton = bottomAshOut01Ton;
        this.bottomAshOut02Ton = bottomAshOut02Ton;
        this.bottomAshOut03Ton = bottomAshOut03Ton;
        this.bottomAshOut04Ton = bottomAshOut04Ton;
        this.powerMainKwh = powerMainKwh;
        this.powerAshVentFilterKwh = powerAshVentFilterKwh;
        this.powerBagFilterKwh = powerBagFilterKwh;
        this.powerScrubberKwh = powerScrubberKwh;
        this.powerCarbonFeedKwh = powerCarbonFeedKwh;
        this.powerLimeFeedKwh = powerLimeFeedKwh;
        this.powerUreaKwh = powerUreaKwh;
        this.powerFdFanKwh = powerFdFanKwh;
        this.reuseWaterTon = reuseWaterTon;
        this.riverbedWaterTon = riverbedWaterTon;
        this.condensateTon = condensateTon;
        this.boilerFeedWaterTon = boilerFeedWaterTon;
        this.wasteWaterTon = wasteWaterTon;
        this.softWaterTon = softWaterTon;
        this.sludgeOutsource01Ton = sludgeOutsource01Ton;
        this.sludgeOutsource02Ton = sludgeOutsource02Ton;
        this.sludgeOutsource03Ton = sludgeOutsource03Ton;
        this.sludgeOutsource04Ton = sludgeOutsource04Ton;
        this.sludgeOutsource05Ton = sludgeOutsource05Ton;
        this.sludgeOutsource06Ton = sludgeOutsource06Ton;
        this.sludgeOutsource07Ton = sludgeOutsource07Ton;
        this.sludgeOutsource08Ton = sludgeOutsource08Ton;
        this.sludgeOutsource09Ton = sludgeOutsource09Ton;
        this.sludgeOutsource10Ton = sludgeOutsource10Ton;
        this.sludgeOutsource11Ton = sludgeOutsource11Ton;
        this.inspectionFeeQty = inspectionFeeQty;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
