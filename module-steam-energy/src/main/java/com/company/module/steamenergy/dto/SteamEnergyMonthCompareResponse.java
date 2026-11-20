package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyMonthCompare;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 회계비용 마감비교 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyMonthCompareResponse {

    private Long monthCompareId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal zescoSteamBuy;
    private BigDecimal lngBuy;
    private BigDecimal zescoOffset;
    private BigDecimal zescoPenalty;
    private BigDecimal fuelPlan;
    private BigDecimal incineratorSteamBuy;
    private BigDecimal fluidSteamBuy;
    private BigDecimal fuelActual;
    private BigDecimal fuelSteamUnit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyMonthCompareResponse from(SteamEnergyMonthCompare e) {
        return from(e, null);
    }

    public static SteamEnergyMonthCompareResponse from(SteamEnergyMonthCompare e, Boolean isUpdate) {
        return SteamEnergyMonthCompareResponse.builder()
                .monthCompareId(e.getMonthCompareId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .zescoSteamBuy(e.getZescoSteamBuy())
                .lngBuy(e.getLngBuy())
                .zescoOffset(e.getZescoOffset())
                .zescoPenalty(e.getZescoPenalty())
                .fuelPlan(e.getFuelPlan())
                .incineratorSteamBuy(e.getIncineratorSteamBuy())
                .fluidSteamBuy(e.getFluidSteamBuy())
                .fuelActual(e.getFuelActual())
                .fuelSteamUnit(e.getFuelSteamUnit())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
