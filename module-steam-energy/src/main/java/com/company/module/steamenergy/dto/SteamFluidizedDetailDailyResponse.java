package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedDetailDaily;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 유동상 세부 운영내역 (일별) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailDailyResponse {

    private Long detailDailyId;
    private LocalDate usageDate;
    private BigDecimal runHours;
    private BigDecimal steamProducedTon;
    private BigDecimal sludgeBurnTon;
    private BigDecimal srfBurnTon;
    private BigDecimal steamUnitPrice;
    private BigDecimal stopHourUnitPrice;
    private BigDecimal ammoniumCarbonateInKg;
    private BigDecimal ammoniumCarbonateUseKg;
    private BigDecimal boilerChemInKg;
    private BigDecimal boilerChemUseKg;
    private BigDecimal activatedCarbonInKg;
    private BigDecimal activatedCarbonUseKg;
    private BigDecimal causticSodaInKg;
    private BigDecimal causticSodaUseKg;
    private BigDecimal slakedLimeInKg;
    private BigDecimal slakedLimeUseKg;
    private BigDecimal saltInKg;
    private BigDecimal saltUseKg;
    private BigDecimal dieselInL;
    private BigDecimal dieselUseL;
    private BigDecimal silicaSandInKg;
    private BigDecimal silicaSandUseKg;
    private BigDecimal activatedCarbonExtraInKg;
    private BigDecimal emergencyDieselInL;
    private BigDecimal srfInTon;
    private BigDecimal flyAshGeneratedTon;
    private BigDecimal flyAshOut01Ton;
    private BigDecimal flyAshOut02Ton;
    private BigDecimal flyAshOut03Ton;
    private BigDecimal flyAshOut04Ton;
    private BigDecimal flyAshOut05Ton;
    private BigDecimal flyAshOut06Ton;
    private BigDecimal flyAshOut07Ton;
    private BigDecimal flyAshOut08Ton;
    private BigDecimal bottomAshGeneratedTon;
    private BigDecimal bottomAshOut01Ton;
    private BigDecimal bottomAshOut02Ton;
    private BigDecimal bottomAshOut03Ton;
    private BigDecimal bottomAshOut04Ton;
    private BigDecimal powerMainKwh;
    private BigDecimal powerAshVentFilterKwh;
    private BigDecimal powerBagFilterKwh;
    private BigDecimal powerScrubberKwh;
    private BigDecimal powerCarbonFeedKwh;
    private BigDecimal powerLimeFeedKwh;
    private BigDecimal powerUreaKwh;
    private BigDecimal powerFdFanKwh;
    private BigDecimal reuseWaterTon;
    private BigDecimal riverbedWaterTon;
    private BigDecimal condensateTon;
    private BigDecimal boilerFeedWaterTon;
    private BigDecimal wasteWaterTon;
    private BigDecimal softWaterTon;
    private BigDecimal sludgeOutsource01Ton;
    private BigDecimal sludgeOutsource02Ton;
    private BigDecimal sludgeOutsource03Ton;
    private BigDecimal sludgeOutsource04Ton;
    private BigDecimal sludgeOutsource05Ton;
    private BigDecimal sludgeOutsource06Ton;
    private BigDecimal sludgeOutsource07Ton;
    private BigDecimal sludgeOutsource08Ton;
    private BigDecimal sludgeOutsource09Ton;
    private BigDecimal sludgeOutsource10Ton;
    private BigDecimal sludgeOutsource11Ton;
    private BigDecimal inspectionFeeQty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedDetailDailyResponse from(SteamFluidizedDetailDaily e) {
        return from(e, null);
    }

    public static SteamFluidizedDetailDailyResponse from(SteamFluidizedDetailDaily e, Boolean isUpdate) {
        return SteamFluidizedDetailDailyResponse.builder()
                .detailDailyId(e.getDetailDailyId())
                .usageDate(e.getUsageDate())
                .runHours(e.getRunHours())
                .steamProducedTon(e.getSteamProducedTon())
                .sludgeBurnTon(e.getSludgeBurnTon())
                .srfBurnTon(e.getSrfBurnTon())
                .steamUnitPrice(e.getSteamUnitPrice())
                .stopHourUnitPrice(e.getStopHourUnitPrice())
                .ammoniumCarbonateInKg(e.getAmmoniumCarbonateInKg())
                .ammoniumCarbonateUseKg(e.getAmmoniumCarbonateUseKg())
                .boilerChemInKg(e.getBoilerChemInKg())
                .boilerChemUseKg(e.getBoilerChemUseKg())
                .activatedCarbonInKg(e.getActivatedCarbonInKg())
                .activatedCarbonUseKg(e.getActivatedCarbonUseKg())
                .causticSodaInKg(e.getCausticSodaInKg())
                .causticSodaUseKg(e.getCausticSodaUseKg())
                .slakedLimeInKg(e.getSlakedLimeInKg())
                .slakedLimeUseKg(e.getSlakedLimeUseKg())
                .saltInKg(e.getSaltInKg())
                .saltUseKg(e.getSaltUseKg())
                .dieselInL(e.getDieselInL())
                .dieselUseL(e.getDieselUseL())
                .silicaSandInKg(e.getSilicaSandInKg())
                .silicaSandUseKg(e.getSilicaSandUseKg())
                .activatedCarbonExtraInKg(e.getActivatedCarbonExtraInKg())
                .emergencyDieselInL(e.getEmergencyDieselInL())
                .srfInTon(e.getSrfInTon())
                .flyAshGeneratedTon(e.getFlyAshGeneratedTon())
                .flyAshOut01Ton(e.getFlyAshOut01Ton())
                .flyAshOut02Ton(e.getFlyAshOut02Ton())
                .flyAshOut03Ton(e.getFlyAshOut03Ton())
                .flyAshOut04Ton(e.getFlyAshOut04Ton())
                .flyAshOut05Ton(e.getFlyAshOut05Ton())
                .flyAshOut06Ton(e.getFlyAshOut06Ton())
                .flyAshOut07Ton(e.getFlyAshOut07Ton())
                .flyAshOut08Ton(e.getFlyAshOut08Ton())
                .bottomAshGeneratedTon(e.getBottomAshGeneratedTon())
                .bottomAshOut01Ton(e.getBottomAshOut01Ton())
                .bottomAshOut02Ton(e.getBottomAshOut02Ton())
                .bottomAshOut03Ton(e.getBottomAshOut03Ton())
                .bottomAshOut04Ton(e.getBottomAshOut04Ton())
                .powerMainKwh(e.getPowerMainKwh())
                .powerAshVentFilterKwh(e.getPowerAshVentFilterKwh())
                .powerBagFilterKwh(e.getPowerBagFilterKwh())
                .powerScrubberKwh(e.getPowerScrubberKwh())
                .powerCarbonFeedKwh(e.getPowerCarbonFeedKwh())
                .powerLimeFeedKwh(e.getPowerLimeFeedKwh())
                .powerUreaKwh(e.getPowerUreaKwh())
                .powerFdFanKwh(e.getPowerFdFanKwh())
                .reuseWaterTon(e.getReuseWaterTon())
                .riverbedWaterTon(e.getRiverbedWaterTon())
                .condensateTon(e.getCondensateTon())
                .boilerFeedWaterTon(e.getBoilerFeedWaterTon())
                .wasteWaterTon(e.getWasteWaterTon())
                .softWaterTon(e.getSoftWaterTon())
                .sludgeOutsource01Ton(e.getSludgeOutsource01Ton())
                .sludgeOutsource02Ton(e.getSludgeOutsource02Ton())
                .sludgeOutsource03Ton(e.getSludgeOutsource03Ton())
                .sludgeOutsource04Ton(e.getSludgeOutsource04Ton())
                .sludgeOutsource05Ton(e.getSludgeOutsource05Ton())
                .sludgeOutsource06Ton(e.getSludgeOutsource06Ton())
                .sludgeOutsource07Ton(e.getSludgeOutsource07Ton())
                .sludgeOutsource08Ton(e.getSludgeOutsource08Ton())
                .sludgeOutsource09Ton(e.getSludgeOutsource09Ton())
                .sludgeOutsource10Ton(e.getSludgeOutsource10Ton())
                .sludgeOutsource11Ton(e.getSludgeOutsource11Ton())
                .inspectionFeeQty(e.getInspectionFeeQty())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
