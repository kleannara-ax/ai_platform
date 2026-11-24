package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 연료비 배분비율(호기별) 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyRatioMachineSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal fuelPm2;
    private BigDecimal fuelPm3;
    private BigDecimal fuelPaperSubtotal;
    private BigDecimal fuelTm3;
    private BigDecimal fuelTm4;
    private BigDecimal fuelTm5;
    private BigDecimal fuelTissueSubtotal;
    private BigDecimal fuelSteamProdTon;
    private BigDecimal usagePm2;
    private BigDecimal usagePm3;
    private BigDecimal usageTm3;
    private BigDecimal usageTm4;
    private BigDecimal usageTm5;
    private BigDecimal usageTotalProdSteam;
    private BigDecimal usageEtc;
    private BigDecimal prodPm2Kg;
    private BigDecimal prodPm3Kg;
    private BigDecimal prodPaperSubtotalKg;
    private BigDecimal prodTm3Kg;
    private BigDecimal prodTm4Kg;
    private BigDecimal prodTm5Kg;
    private BigDecimal prodTissueSubtotalKg;
}
