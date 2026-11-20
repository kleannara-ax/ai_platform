package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 회계비용 계획 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyAccountingPlanSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal compositeSteamBuy;
    private BigDecimal zescoSteamBuy;
    private BigDecimal lngBuy;
    private BigDecimal incineratorCost;
    private BigDecimal fluidCost;
    private BigDecimal zescoOffset;
    private BigDecimal zescoPenalty;
    private BigDecimal zescoAdvanceRepay;
    private BigDecimal compositeLngWater;
    private BigDecimal compositeInvest;
    private BigDecimal compositeIncome;
    private BigDecimal landLease;
    private BigDecimal paperFuel;
    private BigDecimal livingFuel;
    private BigDecimal fuelVsPlan;
    private BigDecimal paperProdTon;
    private BigDecimal tissueProdTon;
    private BigDecimal steamProdTon;
    private BigDecimal lngBurnerCost;
    private BigDecimal compositeNet;
    private BigDecimal zescoNet;
}
