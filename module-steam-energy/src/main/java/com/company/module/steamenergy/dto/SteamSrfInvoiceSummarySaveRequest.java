package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRF 인보이스 Summary 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamSrfInvoiceSummarySaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal bscRate;
    private BigDecimal bscFactor;
    private BigDecimal afbscrBsc;
    private BigDecimal bscSteamAmount;
    private BigDecimal ascRate;
    private BigDecimal ascFactor;
    private BigDecimal ascSteamAmount;
    private BigDecimal others0;
    private BigDecimal others1;
    private BigDecimal others2;
    private BigDecimal others3;
    private BigDecimal payableAdjust;
    private BigDecimal liquidatedDamage;
    private BigDecimal ascRefundAnnual;
    private BigDecimal utilitiesDeduction;
    private BigDecimal otherDeduction1;
    private BigDecimal otherDeduction2;
}
