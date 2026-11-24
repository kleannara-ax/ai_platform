package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 유동상 월별 계획·비용 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedMonthSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal planOperating;
    private BigDecimal planPower;
    private BigDecimal planWaste;
    private BigDecimal planSrfIncome;
    private String summaryRemark;
    private String monthNote;
    private BigDecimal laborCost;
    private BigDecimal operatingPayCost;
    private BigDecimal improvementPayCost;
    private BigDecimal improvementCost;
    private BigDecimal deductionCost;
    private BigDecimal extraPayment;
    private BigDecimal wastewaterQty;
    private BigDecimal soilQty;
    private BigDecimal soilCost;
    private BigDecimal contractCost;
    private BigDecimal operatingCost;
}
