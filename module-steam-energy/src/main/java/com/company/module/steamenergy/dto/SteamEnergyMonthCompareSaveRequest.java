package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 회계비용 마감비교 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyMonthCompareSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal zescoSteamBuy;
    private BigDecimal lngBuy;
    private BigDecimal zescoOffset;
    private BigDecimal zescoPenalty;
    private BigDecimal fuelPlan;
    private BigDecimal incineratorSteamBuy;
    private BigDecimal fluidSteamBuy;
    private BigDecimal fuelActual;
    private BigDecimal fuelSteamUnit;
}
