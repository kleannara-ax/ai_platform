package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyFuelSummary;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 연료비 요약 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyFuelSummaryResponse {

    private Long fuelSummaryId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal t1Pm2Prod;
    private BigDecimal t1Pm2Fuel;
    private BigDecimal t1Pm3Prod;
    private BigDecimal t1Pm3Fuel;
    private BigDecimal t1Tm3Prod;
    private BigDecimal t1Tm3Fuel;
    private BigDecimal t1Tm4Prod;
    private BigDecimal t1Tm4Fuel;
    private BigDecimal t1Tm5Prod;
    private BigDecimal t1Tm5Fuel;
    private BigDecimal t2LngUsage;
    private BigDecimal t2LngPrice;
    private BigDecimal t2LngCost;
    private BigDecimal t2CompositeUsage;
    private BigDecimal t2CompositePrice;
    private BigDecimal t2CompositeCost;
    private BigDecimal t2IncineratorUsage;
    private BigDecimal t2IncineratorPrice;
    private BigDecimal t2IncineratorCost;
    private BigDecimal t2FluidUsage;
    private BigDecimal t2FluidPrice;
    private BigDecimal t2FluidCost;
    private BigDecimal t2ExtUsage;
    private BigDecimal t2ExtPrice;
    private BigDecimal t2ExtCost;
    private BigDecimal t2LngElec;
    private BigDecimal t2LngPenalty;
    private BigDecimal t2LngOverprice;
    private BigDecimal t2CompositeElec;
    private BigDecimal t2CompositePenalty;
    private BigDecimal t2CompositeOverprice;
    private BigDecimal t2IncineratorElec;
    private BigDecimal t2IncineratorPenalty;
    private BigDecimal t2IncineratorOverprice;
    private BigDecimal t2FluidElec;
    private BigDecimal t2FluidPenalty;
    private BigDecimal t2FluidOverprice;
    private BigDecimal t2ExtElec;
    private BigDecimal t2ExtPenalty;
    private BigDecimal t2ExtOverprice;
    private BigDecimal t2SubtotalElec;
    private BigDecimal t2SubtotalPenalty;
    private BigDecimal t2SubtotalOverprice;
    private BigDecimal t2BurnerLngElec;
    private BigDecimal t2BurnerLngPenalty;
    private BigDecimal t2BurnerLngOverprice;
    private BigDecimal t2TotalElec;
    private BigDecimal t2TotalPenalty;
    private BigDecimal t2TotalOverprice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyFuelSummaryResponse from(SteamEnergyFuelSummary e) {
        return from(e, null);
    }

    public static SteamEnergyFuelSummaryResponse from(SteamEnergyFuelSummary e, Boolean isUpdate) {
        return SteamEnergyFuelSummaryResponse.builder()
                .fuelSummaryId(e.getFuelSummaryId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .t1Pm2Prod(e.getT1Pm2Prod())
                .t1Pm2Fuel(e.getT1Pm2Fuel())
                .t1Pm3Prod(e.getT1Pm3Prod())
                .t1Pm3Fuel(e.getT1Pm3Fuel())
                .t1Tm3Prod(e.getT1Tm3Prod())
                .t1Tm3Fuel(e.getT1Tm3Fuel())
                .t1Tm4Prod(e.getT1Tm4Prod())
                .t1Tm4Fuel(e.getT1Tm4Fuel())
                .t1Tm5Prod(e.getT1Tm5Prod())
                .t1Tm5Fuel(e.getT1Tm5Fuel())
                .t2LngUsage(e.getT2LngUsage())
                .t2LngPrice(e.getT2LngPrice())
                .t2LngCost(e.getT2LngCost())
                .t2CompositeUsage(e.getT2CompositeUsage())
                .t2CompositePrice(e.getT2CompositePrice())
                .t2CompositeCost(e.getT2CompositeCost())
                .t2IncineratorUsage(e.getT2IncineratorUsage())
                .t2IncineratorPrice(e.getT2IncineratorPrice())
                .t2IncineratorCost(e.getT2IncineratorCost())
                .t2FluidUsage(e.getT2FluidUsage())
                .t2FluidPrice(e.getT2FluidPrice())
                .t2FluidCost(e.getT2FluidCost())
                .t2ExtUsage(e.getT2ExtUsage())
                .t2ExtPrice(e.getT2ExtPrice())
                .t2ExtCost(e.getT2ExtCost())
                .t2LngElec(e.getT2LngElec())
                .t2LngPenalty(e.getT2LngPenalty())
                .t2LngOverprice(e.getT2LngOverprice())
                .t2CompositeElec(e.getT2CompositeElec())
                .t2CompositePenalty(e.getT2CompositePenalty())
                .t2CompositeOverprice(e.getT2CompositeOverprice())
                .t2IncineratorElec(e.getT2IncineratorElec())
                .t2IncineratorPenalty(e.getT2IncineratorPenalty())
                .t2IncineratorOverprice(e.getT2IncineratorOverprice())
                .t2FluidElec(e.getT2FluidElec())
                .t2FluidPenalty(e.getT2FluidPenalty())
                .t2FluidOverprice(e.getT2FluidOverprice())
                .t2ExtElec(e.getT2ExtElec())
                .t2ExtPenalty(e.getT2ExtPenalty())
                .t2ExtOverprice(e.getT2ExtOverprice())
                .t2SubtotalElec(e.getT2SubtotalElec())
                .t2SubtotalPenalty(e.getT2SubtotalPenalty())
                .t2SubtotalOverprice(e.getT2SubtotalOverprice())
                .t2BurnerLngElec(e.getT2BurnerLngElec())
                .t2BurnerLngPenalty(e.getT2BurnerLngPenalty())
                .t2BurnerLngOverprice(e.getT2BurnerLngOverprice())
                .t2TotalElec(e.getT2TotalElec())
                .t2TotalPenalty(e.getT2TotalPenalty())
                .t2TotalOverprice(e.getT2TotalOverprice())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
