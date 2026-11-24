package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 소각로 사업계획대비 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamIncineratorPlanMonthlySaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal contractLabor;
    private BigDecimal laborPlan;
    private BigDecimal laborActual;
    private BigDecimal operationPlan;
    private BigDecimal operationActual;
    private BigDecimal totalPlan;
    private String noteU1;
    private String noteU2;
    private BigDecimal brkMonthPlan;
    private BigDecimal brkMonthActual;
    private BigDecimal brkYtdPlan;
    private BigDecimal brkYtdActual;
    private BigDecimal brkPrevActual;
    private String brkNote;
}
