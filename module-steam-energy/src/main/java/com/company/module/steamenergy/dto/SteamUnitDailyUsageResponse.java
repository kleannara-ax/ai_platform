package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamUnitDailyUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 원단위 일지(호기별 일일 실적) 응답
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamUnitDailyUsageResponse {

    private Long unitUsageId;
    private LocalDate usageDate;
    private String machineCode;
    private BigDecimal mainSteam;
    private BigDecimal coaterSteam;
    private BigDecimal ventilationSteam;
    private BigDecimal steam;
    private BigDecimal productionKg;
    private String productionType;
    private BigDecimal runtimeHours;
    private BigDecimal disperserRuntimeHours;
    private BigDecimal ventRuntimeHours;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** INSERT / UPDATE 구분 (저장 응답에서만) */
    private Boolean isUpdate;

    public static SteamUnitDailyUsageResponse from(SteamUnitDailyUsage e) {
        return from(e, null);
    }

    public static SteamUnitDailyUsageResponse from(SteamUnitDailyUsage e, Boolean isUpdate) {
        return SteamUnitDailyUsageResponse.builder()
                .unitUsageId(e.getUnitUsageId())
                .usageDate(e.getUsageDate())
                .machineCode(e.getMachineCode())
                .mainSteam(e.getMainSteam())
                .coaterSteam(e.getCoaterSteam())
                .ventilationSteam(e.getVentilationSteam())
                .steam(e.getSteam())
                .productionKg(e.getProductionKg())
                .productionType(e.getProductionType())
                .runtimeHours(e.getRuntimeHours())
                .disperserRuntimeHours(e.getDisperserRuntimeHours())
                .ventRuntimeHours(e.getVentRuntimeHours())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
