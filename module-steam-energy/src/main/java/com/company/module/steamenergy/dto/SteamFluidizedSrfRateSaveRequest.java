package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 유동상 SRF 처리단가 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedSrfRateSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal vendor01Rate;
    private BigDecimal vendor02Rate;
    private BigDecimal vendor03Rate;
    private BigDecimal vendor04Rate;
    private BigDecimal vendor05Rate;
    private BigDecimal vendor06Rate;
    private BigDecimal vendor07Rate;
    private BigDecimal vendor08Rate;
    private BigDecimal vendor09Rate;
    private BigDecimal vendor10Rate;
}
