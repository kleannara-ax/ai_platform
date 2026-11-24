package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 유동상 세부 운영내역 (월별 단가·비용) 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedDetailMonthSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal improvementCost;
    private BigDecimal extraCost;
    private BigDecimal steamProducedUnitPrice;
    private BigDecimal ammoniumCarbonateUnitPrice;
    private BigDecimal boilerChemInUnitPrice;
    private BigDecimal boilerChemUseUnitPrice;
    private BigDecimal activatedCarbonInUnitPrice;
    private BigDecimal activatedCarbonUseUnitPrice;
    private BigDecimal causticSodaInUnitPrice;
    private BigDecimal causticSodaUseUnitPrice;
    private BigDecimal slakedLimeUnitPrice;
    private BigDecimal saltUnitPrice;
    private BigDecimal dieselUnitPrice;
    private BigDecimal silicaSandUseUnitPrice;
    private BigDecimal srfUnitPrice;
    private BigDecimal flyAshGeneratedUnitPrice;
    private BigDecimal flyAshOut01UnitPrice;
    private BigDecimal flyAshOut02UnitPrice;
    private BigDecimal flyAshOut03UnitPrice;
    private BigDecimal flyAshOut04UnitPrice;
    private BigDecimal flyAshOut05UnitPrice;
    private BigDecimal flyAshOut06UnitPrice;
    private BigDecimal flyAshOut07UnitPrice;
    private BigDecimal flyAshOut08UnitPrice;
    private BigDecimal bottomAshGeneratedUnitPrice;
    private BigDecimal bottomAshOut01UnitPrice;
    private BigDecimal bottomAshOut02UnitPrice;
    private BigDecimal bottomAshOut03UnitPrice;
    private BigDecimal bottomAshOut04UnitPrice;
    private BigDecimal powerUnitPrice;
    private BigDecimal powerUreaUnitPrice;
    private BigDecimal powerFdFanUnitPrice;
    private BigDecimal reuseWaterUnitPrice;
    private BigDecimal condensateUnitPrice;
    private BigDecimal boilerFeedWaterUnitPrice;
    private BigDecimal wasteWaterUnitPrice;
    private BigDecimal softWaterUnitPrice;
    private BigDecimal sludgeOutsource01UnitPrice;
    private BigDecimal sludgeOutsource02UnitPrice;
    private BigDecimal sludgeOutsource03UnitPrice;
    private BigDecimal sludgeOutsource04UnitPrice;
    private BigDecimal sludgeOutsource05UnitPrice;
    private BigDecimal sludgeOutsource06UnitPrice;
    private BigDecimal sludgeOutsource07UnitPrice;
    private BigDecimal sludgeOutsource08UnitPrice;
    private BigDecimal sludgeOutsource09UnitPrice;
    private BigDecimal sludgeOutsource10UnitPrice;
    private BigDecimal sludgeOutsource11UnitPrice;
    private BigDecimal inspectionFeeUnitPrice;
    private String silicaSandUseNote;
}
