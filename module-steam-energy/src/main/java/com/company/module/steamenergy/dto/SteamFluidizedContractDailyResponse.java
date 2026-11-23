package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedContractDaily;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 유동상 소각로 도급비용 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedContractDailyResponse {

    private Long fluidizedContractId;
    private LocalDate usageDate;
    private BigDecimal steamTon;
    private BigDecimal steamUnitPrice;
    private BigDecimal stopHours;
    private BigDecimal fixedCostPerHour;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedContractDailyResponse from(SteamFluidizedContractDaily e) {
        return from(e, null);
    }

    public static SteamFluidizedContractDailyResponse from(SteamFluidizedContractDaily e, Boolean isUpdate) {
        return SteamFluidizedContractDailyResponse.builder()
                .fluidizedContractId(e.getFluidizedContractId())
                .usageDate(e.getUsageDate())
                .steamTon(e.getSteamTon())
                .steamUnitPrice(e.getSteamUnitPrice())
                .stopHours(e.getStopHours())
                .fixedCostPerHour(e.getFixedCostPerHour())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
