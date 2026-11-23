package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 원단위 일지(호기별 일일 실적) 저장 요청 — (일자 + 호기) 업서트. 한 행의 모든 항목을 함께 보낸다.
 */
@Getter
@Setter
public class SteamUnitDailyUsageSaveRequest {

    @NotNull(message = "일자(usageDate)는 필수입니다.")
    private LocalDate usageDate;

    @NotBlank(message = "호기(machineCode)는 필수입니다.")
    @Pattern(regexp = "PM-2|PM-3|TM-3|TM-4|TM-5", message = "호기(machineCode)는 PM-2, PM-3, TM-3, TM-4, TM-5 중 하나여야 합니다.")
    private String machineCode;

    private BigDecimal mainSteam;
    private BigDecimal coaterSteam;
    private BigDecimal ventilationSteam;
    private BigDecimal steam;
    private BigDecimal productionKg;

    @Size(max = 50)
    private String productionType;

    private BigDecimal runtimeHours;
    private BigDecimal disperserRuntimeHours;
    private BigDecimal ventRuntimeHours;

    @Size(max = 500)
    private String note;
}
