package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 연료비 사업계획 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyFuelPlanSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal paperProdTon;
    private BigDecimal tissueProdTon;
    private BigDecimal paperUnit;
    private BigDecimal tissueUnit;
    private BigDecimal paperTotalSteam;
    private BigDecimal tissueTotalSteam;
    private BigDecimal etcDisperser;
    private BigDecimal etcToc;
    private BigDecimal operatingDays;
    private BigDecimal prodIncinerator;
    private BigDecimal prodComposite;
    private BigDecimal prodExtBoiler;
    private BigDecimal prodFluid;
    private BigDecimal lngComposite;
    private BigDecimal lngTm3;
    private BigDecimal lngTm4;
    private BigDecimal lngTm5;
    private BigDecimal lngPm3;
    private BigDecimal priceLng;
    private BigDecimal priceIncinerator;
    private BigDecimal priceComposite;
    private BigDecimal priceExtBoiler;
    private BigDecimal priceFluid;
    private BigDecimal opexIncinerator;
    private BigDecimal opexFluid;
    private BigDecimal payExtAdvance;
    private BigDecimal payExtPenalty;
    private BigDecimal payIncinerator;
    private BigDecimal payFluid;
}
