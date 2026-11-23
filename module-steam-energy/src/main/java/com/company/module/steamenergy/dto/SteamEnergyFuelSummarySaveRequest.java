package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 연료비 요약 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyFuelSummarySaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal t1Pm2Prod;
    private BigDecimal t1Pm2Fuel;
    private BigDecimal t1Pm3Prod;
    private BigDecimal t1Pm3Fuel;
    private BigDecimal t1Tm3Prod;
    private BigDecimal t1Tm3Fuel;
    private BigDecimal t1Tm4Prod;
    private BigDecimal t1Tm4Fuel;
    private BigDecimal t1Tm5Prod;
    private BigDecimal t1Tm5Fuel;
    private BigDecimal t2LngUsage;
    private BigDecimal t2LngPrice;
    private BigDecimal t2LngCost;
    private BigDecimal t2CompositeUsage;
    private BigDecimal t2CompositePrice;
    private BigDecimal t2CompositeCost;
    private BigDecimal t2IncineratorUsage;
    private BigDecimal t2IncineratorPrice;
    private BigDecimal t2IncineratorCost;
    private BigDecimal t2FluidUsage;
    private BigDecimal t2FluidPrice;
    private BigDecimal t2FluidCost;
    private BigDecimal t2ExtUsage;
    private BigDecimal t2ExtPrice;
    private BigDecimal t2ExtCost;
    private BigDecimal t2LngElec;
    private BigDecimal t2LngPenalty;
    private BigDecimal t2LngOverprice;
    private BigDecimal t2CompositeElec;
    private BigDecimal t2CompositePenalty;
    private BigDecimal t2CompositeOverprice;
    private BigDecimal t2IncineratorElec;
    private BigDecimal t2IncineratorPenalty;
    private BigDecimal t2IncineratorOverprice;
    private BigDecimal t2FluidElec;
    private BigDecimal t2FluidPenalty;
    private BigDecimal t2FluidOverprice;
    private BigDecimal t2ExtElec;
    private BigDecimal t2ExtPenalty;
    private BigDecimal t2ExtOverprice;
    private BigDecimal t2SubtotalElec;
    private BigDecimal t2SubtotalPenalty;
    private BigDecimal t2SubtotalOverprice;
    private BigDecimal t2BurnerLngElec;
    private BigDecimal t2BurnerLngPenalty;
    private BigDecimal t2BurnerLngOverprice;
    private BigDecimal t2TotalElec;
    private BigDecimal t2TotalPenalty;
    private BigDecimal t2TotalOverprice;
}
