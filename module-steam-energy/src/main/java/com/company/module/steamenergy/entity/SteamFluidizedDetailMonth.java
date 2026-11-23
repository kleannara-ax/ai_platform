package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 세부 운영내역 (월별 단가·비용) — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 세부 운영내역 (unit-fluidized-detail.html)
 * <p>Table: steam_fluidized_detail_month
 * <p>PK: DETAIL_MONTH_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_fluidized_detail_month")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DETAIL_MONTH_ID")
    private Long detailMonthId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "IMPROVEMENT_COST", precision = 18, scale = 6)
    private BigDecimal improvementCost;

    @Column(name = "EXTRA_COST", precision = 18, scale = 6)
    private BigDecimal extraCost;

    @Column(name = "STEAM_PRODUCED_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal steamProducedUnitPrice;

    @Column(name = "AMMONIUM_CARBONATE_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal ammoniumCarbonateUnitPrice;

    @Column(name = "BOILER_CHEM_IN_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal boilerChemInUnitPrice;

    @Column(name = "BOILER_CHEM_USE_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal boilerChemUseUnitPrice;

    @Column(name = "ACTIVATED_CARBON_IN_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal activatedCarbonInUnitPrice;

    @Column(name = "ACTIVATED_CARBON_USE_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal activatedCarbonUseUnitPrice;

    @Column(name = "CAUSTIC_SODA_IN_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal causticSodaInUnitPrice;

    @Column(name = "CAUSTIC_SODA_USE_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal causticSodaUseUnitPrice;

    @Column(name = "SLAKED_LIME_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal slakedLimeUnitPrice;

    @Column(name = "SALT_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal saltUnitPrice;

    @Column(name = "DIESEL_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal dieselUnitPrice;

    @Column(name = "SILICA_SAND_USE_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal silicaSandUseUnitPrice;

    @Column(name = "SRF_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal srfUnitPrice;

    @Column(name = "FLY_ASH_GENERATED_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshGeneratedUnitPrice;

    @Column(name = "FLY_ASH_OUT_01_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut01UnitPrice;

    @Column(name = "FLY_ASH_OUT_02_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut02UnitPrice;

    @Column(name = "FLY_ASH_OUT_03_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut03UnitPrice;

    @Column(name = "FLY_ASH_OUT_04_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut04UnitPrice;

    @Column(name = "FLY_ASH_OUT_05_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut05UnitPrice;

    @Column(name = "FLY_ASH_OUT_06_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut06UnitPrice;

    @Column(name = "FLY_ASH_OUT_07_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut07UnitPrice;

    @Column(name = "FLY_ASH_OUT_08_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal flyAshOut08UnitPrice;

    @Column(name = "BOTTOM_ASH_GENERATED_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal bottomAshGeneratedUnitPrice;

    @Column(name = "BOTTOM_ASH_OUT_01_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal bottomAshOut01UnitPrice;

    @Column(name = "BOTTOM_ASH_OUT_02_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal bottomAshOut02UnitPrice;

    @Column(name = "BOTTOM_ASH_OUT_03_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal bottomAshOut03UnitPrice;

    @Column(name = "BOTTOM_ASH_OUT_04_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal bottomAshOut04UnitPrice;

    @Column(name = "POWER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal powerUnitPrice;

    @Column(name = "POWER_UREA_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal powerUreaUnitPrice;

    @Column(name = "POWER_FD_FAN_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal powerFdFanUnitPrice;

    @Column(name = "REUSE_WATER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal reuseWaterUnitPrice;

    @Column(name = "CONDENSATE_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal condensateUnitPrice;

    @Column(name = "BOILER_FEED_WATER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal boilerFeedWaterUnitPrice;

    @Column(name = "WASTE_WATER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal wasteWaterUnitPrice;

    @Column(name = "SOFT_WATER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal softWaterUnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_01_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource01UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_02_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource02UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_03_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource03UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_04_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource04UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_05_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource05UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_06_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource06UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_07_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource07UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_08_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource08UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_09_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource09UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_10_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource10UnitPrice;

    @Column(name = "SLUDGE_OUTSOURCE_11_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal sludgeOutsource11UnitPrice;

    @Column(name = "INSPECTION_FEE_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal inspectionFeeUnitPrice;

    @Column(name = "SILICA_SAND_USE_NOTE", length = 200)
    private String silicaSandUseNote;

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
    public void update(BigDecimal improvementCost, BigDecimal extraCost, BigDecimal steamProducedUnitPrice, BigDecimal ammoniumCarbonateUnitPrice, BigDecimal boilerChemInUnitPrice, BigDecimal boilerChemUseUnitPrice, BigDecimal activatedCarbonInUnitPrice, BigDecimal activatedCarbonUseUnitPrice, BigDecimal causticSodaInUnitPrice, BigDecimal causticSodaUseUnitPrice, BigDecimal slakedLimeUnitPrice, BigDecimal saltUnitPrice, BigDecimal dieselUnitPrice, BigDecimal silicaSandUseUnitPrice, BigDecimal srfUnitPrice, BigDecimal flyAshGeneratedUnitPrice, BigDecimal flyAshOut01UnitPrice, BigDecimal flyAshOut02UnitPrice, BigDecimal flyAshOut03UnitPrice, BigDecimal flyAshOut04UnitPrice, BigDecimal flyAshOut05UnitPrice, BigDecimal flyAshOut06UnitPrice, BigDecimal flyAshOut07UnitPrice, BigDecimal flyAshOut08UnitPrice, BigDecimal bottomAshGeneratedUnitPrice, BigDecimal bottomAshOut01UnitPrice, BigDecimal bottomAshOut02UnitPrice, BigDecimal bottomAshOut03UnitPrice, BigDecimal bottomAshOut04UnitPrice, BigDecimal powerUnitPrice, BigDecimal powerUreaUnitPrice, BigDecimal powerFdFanUnitPrice, BigDecimal reuseWaterUnitPrice, BigDecimal condensateUnitPrice, BigDecimal boilerFeedWaterUnitPrice, BigDecimal wasteWaterUnitPrice, BigDecimal softWaterUnitPrice, BigDecimal sludgeOutsource01UnitPrice, BigDecimal sludgeOutsource02UnitPrice, BigDecimal sludgeOutsource03UnitPrice, BigDecimal sludgeOutsource04UnitPrice, BigDecimal sludgeOutsource05UnitPrice, BigDecimal sludgeOutsource06UnitPrice, BigDecimal sludgeOutsource07UnitPrice, BigDecimal sludgeOutsource08UnitPrice, BigDecimal sludgeOutsource09UnitPrice, BigDecimal sludgeOutsource10UnitPrice, BigDecimal sludgeOutsource11UnitPrice, BigDecimal inspectionFeeUnitPrice, String silicaSandUseNote, Long updatedBy) {
        this.improvementCost = improvementCost;
        this.extraCost = extraCost;
        this.steamProducedUnitPrice = steamProducedUnitPrice;
        this.ammoniumCarbonateUnitPrice = ammoniumCarbonateUnitPrice;
        this.boilerChemInUnitPrice = boilerChemInUnitPrice;
        this.boilerChemUseUnitPrice = boilerChemUseUnitPrice;
        this.activatedCarbonInUnitPrice = activatedCarbonInUnitPrice;
        this.activatedCarbonUseUnitPrice = activatedCarbonUseUnitPrice;
        this.causticSodaInUnitPrice = causticSodaInUnitPrice;
        this.causticSodaUseUnitPrice = causticSodaUseUnitPrice;
        this.slakedLimeUnitPrice = slakedLimeUnitPrice;
        this.saltUnitPrice = saltUnitPrice;
        this.dieselUnitPrice = dieselUnitPrice;
        this.silicaSandUseUnitPrice = silicaSandUseUnitPrice;
        this.srfUnitPrice = srfUnitPrice;
        this.flyAshGeneratedUnitPrice = flyAshGeneratedUnitPrice;
        this.flyAshOut01UnitPrice = flyAshOut01UnitPrice;
        this.flyAshOut02UnitPrice = flyAshOut02UnitPrice;
        this.flyAshOut03UnitPrice = flyAshOut03UnitPrice;
        this.flyAshOut04UnitPrice = flyAshOut04UnitPrice;
        this.flyAshOut05UnitPrice = flyAshOut05UnitPrice;
        this.flyAshOut06UnitPrice = flyAshOut06UnitPrice;
        this.flyAshOut07UnitPrice = flyAshOut07UnitPrice;
        this.flyAshOut08UnitPrice = flyAshOut08UnitPrice;
        this.bottomAshGeneratedUnitPrice = bottomAshGeneratedUnitPrice;
        this.bottomAshOut01UnitPrice = bottomAshOut01UnitPrice;
        this.bottomAshOut02UnitPrice = bottomAshOut02UnitPrice;
        this.bottomAshOut03UnitPrice = bottomAshOut03UnitPrice;
        this.bottomAshOut04UnitPrice = bottomAshOut04UnitPrice;
        this.powerUnitPrice = powerUnitPrice;
        this.powerUreaUnitPrice = powerUreaUnitPrice;
        this.powerFdFanUnitPrice = powerFdFanUnitPrice;
        this.reuseWaterUnitPrice = reuseWaterUnitPrice;
        this.condensateUnitPrice = condensateUnitPrice;
        this.boilerFeedWaterUnitPrice = boilerFeedWaterUnitPrice;
        this.wasteWaterUnitPrice = wasteWaterUnitPrice;
        this.softWaterUnitPrice = softWaterUnitPrice;
        this.sludgeOutsource01UnitPrice = sludgeOutsource01UnitPrice;
        this.sludgeOutsource02UnitPrice = sludgeOutsource02UnitPrice;
        this.sludgeOutsource03UnitPrice = sludgeOutsource03UnitPrice;
        this.sludgeOutsource04UnitPrice = sludgeOutsource04UnitPrice;
        this.sludgeOutsource05UnitPrice = sludgeOutsource05UnitPrice;
        this.sludgeOutsource06UnitPrice = sludgeOutsource06UnitPrice;
        this.sludgeOutsource07UnitPrice = sludgeOutsource07UnitPrice;
        this.sludgeOutsource08UnitPrice = sludgeOutsource08UnitPrice;
        this.sludgeOutsource09UnitPrice = sludgeOutsource09UnitPrice;
        this.sludgeOutsource10UnitPrice = sludgeOutsource10UnitPrice;
        this.sludgeOutsource11UnitPrice = sludgeOutsource11UnitPrice;
        this.inspectionFeeUnitPrice = inspectionFeeUnitPrice;
        this.silicaSandUseNote = silicaSandUseNote;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
