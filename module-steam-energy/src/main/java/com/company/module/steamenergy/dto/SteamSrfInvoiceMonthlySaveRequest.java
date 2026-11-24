package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRF 인보이스 월별 운영내역 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamSrfInvoiceMonthlySaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal steamQty;
    private BigDecimal steamUnit;
    private BigDecimal powerQty;
    private BigDecimal powerUnit;
    private BigDecimal lngQty;
    private BigDecimal lngUnit;
    private BigDecimal processQty;
    private BigDecimal processUnit;
    private BigDecimal softQty;
    private BigDecimal softUnit;
    private BigDecimal softenQty;
    private BigDecimal softenUnit;
    private BigDecimal lifeQty;
    private BigDecimal lifeUnit;
    private BigDecimal etcSpecial;
    private BigDecimal etcBsc7;
    private BigDecimal etcSrfShare;
    private BigDecimal etcInspect;
    private BigDecimal etcOver7;
    private BigDecimal etcAirOver;
    private BigDecimal etcWaste;
}
