package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 이동계획 월 기준값 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamMovePlanMonthSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @Min(0) @Max(31)
    private Integer actualUntilDay;
    @Pattern(regexp = "[YN]", message = "adjustYn 은(는) Y 또는 N 이어야 합니다.")
    private String adjustYn;
    private BigDecimal waste2BillCapTon;
    private BigDecimal comboGuaranteeTon;
    private BigDecimal comboBscPrice;
    private BigDecimal comboAscPrice;
    private BigDecimal fluidBillTonHour;
    private BigDecimal fluidImprovementCost;
    private BigDecimal lngM3PerTon;
    private BigDecimal lngUnitPrice;
    @Size(max = 500)
    private String note;
}
