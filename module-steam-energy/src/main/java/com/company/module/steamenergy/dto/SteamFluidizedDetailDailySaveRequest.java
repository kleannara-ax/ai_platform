package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 유동상 세부 운영내역 (일별) 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedDetailDailySaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
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
}
