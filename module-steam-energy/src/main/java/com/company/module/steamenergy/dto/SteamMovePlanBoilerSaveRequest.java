package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 이동계획 보일러별 기준값 저장 요청 — (YEAR_NO, MONTH_NO, BOILER_CODE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamMovePlanBoilerSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @NotBlank(message = "보일러(boilerCode)는 필수입니다.")
    private String boilerCode;

    @Pattern(regexp = "[YN]", message = "useYn 은(는) Y 또는 N 이어야 합니다.")
    private String useYn;
    private BigDecimal maxTonDay;
    private BigDecimal minTonDay;
    private BigDecimal steamUnitPrice;
    private BigDecimal idleHourPrice;
}
