package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 유동상·소각로·복합·외부 일일 스팀 구매량 저장 요청 — 일자 업서트. 한 행의 모든 항목을 함께 보낸다.
 */
@Getter
@Setter
public class SteamFuelDailyUsageSaveRequest {

    @NotNull(message = "일자(usageDate)는 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal fluidSteamTon;
    private BigDecimal fluidUnitPrice;
    private BigDecimal incineratorSteamTon;
    private BigDecimal incineratorUnitPrice;
    private BigDecimal combinedSteamTon;
    private BigDecimal combinedUnitPrice;
    private BigDecimal combinedDeduction;
    private BigDecimal extBoilerSteamTon;
    private BigDecimal extBoilerUnitPrice;
    private BigDecimal extBoilerDeduction;
}
