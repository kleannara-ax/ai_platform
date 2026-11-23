package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFuelDailyUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 유동상·소각로·복합·외부 일일 스팀 구매량 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFuelDailyUsageResponse {

    private Long fuelUsageId;
    private LocalDate usageDate;
    private BigDecimal fluidSteamTon;
    private BigDecimal fluidUnitPrice;
    private BigDecimal incineratorSteamTon;
    private BigDecimal incineratorUnitPrice;
    private BigDecimal combinedSteamTon;
    private BigDecimal combinedUnitPrice;
    private BigDecimal combinedDeduction;
    private BigDecimal extBoilerSteamTon;
    private BigDecimal extBoilerUnitPrice;
    private BigDecimal extBoilerDeduction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFuelDailyUsageResponse from(SteamFuelDailyUsage e) {
        return from(e, null);
    }

    public static SteamFuelDailyUsageResponse from(SteamFuelDailyUsage e, Boolean isUpdate) {
        return SteamFuelDailyUsageResponse.builder()
                .fuelUsageId(e.getFuelUsageId())
                .usageDate(e.getUsageDate())
                .fluidSteamTon(e.getFluidSteamTon())
                .fluidUnitPrice(e.getFluidUnitPrice())
                .incineratorSteamTon(e.getIncineratorSteamTon())
                .incineratorUnitPrice(e.getIncineratorUnitPrice())
                .combinedSteamTon(e.getCombinedSteamTon())
                .combinedUnitPrice(e.getCombinedUnitPrice())
                .combinedDeduction(e.getCombinedDeduction())
                .extBoilerSteamTon(e.getExtBoilerSteamTon())
                .extBoilerUnitPrice(e.getExtBoilerUnitPrice())
                .extBoilerDeduction(e.getExtBoilerDeduction())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
