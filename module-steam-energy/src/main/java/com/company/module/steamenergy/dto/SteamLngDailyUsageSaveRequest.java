package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일별 LNG 연료 사용량 저장 요청 — 일자 업서트. 한 행의 모든 항목을 함께 보낸다.
 */
@Getter
@Setter
public class SteamLngDailyUsageSaveRequest {

    @NotNull(message = "일자(usageDate)는 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal boiler20t1;
    private BigDecimal boiler20t2;
    private BigDecimal boiler50t;
    private BigDecimal boiler60t;
    private BigDecimal boilerCombined;
    private BigDecimal boilerNewIncinerator;
    private BigDecimal boilerUnitPrice;

    private BigDecimal burnerPm3;
    private BigDecimal burnerTm3;
    private BigDecimal burnerTm4;
    private BigDecimal burnerTm5;
    private BigDecimal burnerUnitPrice;

    private BigDecimal mixPm2;
    private BigDecimal mixPm3;
    private BigDecimal mixTm3;
    private BigDecimal mixTm4;
    private BigDecimal mixTm5;
}
