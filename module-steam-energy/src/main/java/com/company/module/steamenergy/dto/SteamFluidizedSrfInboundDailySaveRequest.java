package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 유동상 SRF 입고내역 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedSrfInboundDailySaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal vendor01Kg;
    private BigDecimal vendor02Kg;
    private BigDecimal vendor03Kg;
    private BigDecimal vendor04Kg;
    private BigDecimal vendor05Kg;
    private BigDecimal vendor06Kg;
    private BigDecimal vendor07Kg;
    private BigDecimal vendor08Kg;
    private BigDecimal vendor09Kg;
    private BigDecimal vendor10Kg;
}
