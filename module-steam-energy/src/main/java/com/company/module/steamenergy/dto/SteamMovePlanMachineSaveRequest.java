package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 이동계획 호기별 생산계획 저장 요청 — (YEAR_NO, MONTH_NO, MACHINE_CODE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamMovePlanMachineSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @NotBlank(message = "호기(machineCode)는 필수입니다.")
    private String machineCode;

    private BigDecimal productionTon;
    private BigDecimal runtimeHours;
    @Size(max = 10)
    private String planKind;
    private BigDecimal unitOverride;
}
