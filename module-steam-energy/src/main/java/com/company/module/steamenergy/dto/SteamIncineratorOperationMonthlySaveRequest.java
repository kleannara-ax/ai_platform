package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 소각로 계획대비 가동실적 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamIncineratorOperationMonthlySaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal u1Plan;
    private BigDecimal u1Actual;
    private BigDecimal u2Plan;
    private BigDecimal u2Actual;
    private BigDecimal steamPlan;
    private BigDecimal steamActual;
    private BigDecimal ratePlan;
    private BigDecimal rateActual;
    private BigDecimal costPlan;
    private BigDecimal costActual;
}
