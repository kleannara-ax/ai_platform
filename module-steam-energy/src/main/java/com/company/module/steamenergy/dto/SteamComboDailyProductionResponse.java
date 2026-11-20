package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamComboDailyProduction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 복합보일러 스팀생산실적 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboDailyProductionResponse {

    private Long comboProductionId;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamComboDailyProductionResponse from(SteamComboDailyProduction e) {
        return from(e, null);
    }

    public static SteamComboDailyProductionResponse from(SteamComboDailyProduction e, Boolean isUpdate) {
        return SteamComboDailyProductionResponse.builder()
                .comboProductionId(e.getComboProductionId())
                .usageDate(e.getUsageDate())
                .comboSteam(e.getComboSteam())
                .comboCondensate(e.getComboCondensate())
                .comboMakeup(e.getComboMakeup())
                .comboDeaerator(e.getComboDeaerator())
                .comboProcess(e.getComboProcess())
                .comboDilution(e.getComboDilution())
                .externalSteam(e.getExternalSteam())
                .externalCondensate(e.getExternalCondensate())
                .externalMakeup(e.getExternalMakeup())
                .wasteSteam1(e.getWasteSteam1())
                .wasteSteam2(e.getWasteSteam2())
                .kn20(e.getKn20())
                .kn50(e.getKn50())
                .fluidizedSteam(e.getFluidizedSteam())
                .fluidizedCondensate(e.getFluidizedCondensate())
                .runtime20(e.getRuntime20())
                .runtime50(e.getRuntime50())
                .runtimeFluidized(e.getRuntimeFluidized())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
