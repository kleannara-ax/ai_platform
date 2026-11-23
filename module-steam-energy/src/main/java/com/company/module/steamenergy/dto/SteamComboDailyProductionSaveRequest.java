package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 복합보일러 스팀생산실적 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamComboDailyProductionSaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal comboSteam;
    private BigDecimal comboCondensate;
    private BigDecimal comboMakeup;
    private BigDecimal comboDeaerator;
    private BigDecimal comboProcess;
    private BigDecimal comboDilution;
    private BigDecimal externalSteam;
    private BigDecimal externalCondensate;
    private BigDecimal externalMakeup;
    private BigDecimal wasteSteam1;
    private BigDecimal wasteSteam2;
    private BigDecimal kn20;
    private BigDecimal kn50;
    private BigDecimal fluidizedSteam;
    private BigDecimal fluidizedCondensate;
    private BigDecimal runtime20;
    private BigDecimal runtime50;
    private BigDecimal runtimeFluidized;
    private BigDecimal wasteRoomSteam2;
    private BigDecimal fluidizedTotalSteam;
    private BigDecimal fluidizedTotalCondensate;
}
