package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyAccountingPlan;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 회계비용 계획 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyAccountingPlanResponse {

    private Long accountingPlanId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal compositeSteamBuy;
    private BigDecimal zescoSteamBuy;
    private BigDecimal lngBuy;
    private BigDecimal incineratorCost;
    private BigDecimal fluidCost;
    private BigDecimal zescoOffset;
    private BigDecimal zescoPenalty;
    private BigDecimal zescoAdvanceRepay;
    private BigDecimal compositeLngWater;
    private BigDecimal compositeInvest;
    private BigDecimal compositeIncome;
    private BigDecimal landLease;
    private BigDecimal paperFuel;
    private BigDecimal livingFuel;
    private BigDecimal fuelVsPlan;
    private BigDecimal paperProdTon;
    private BigDecimal tissueProdTon;
    private BigDecimal steamProdTon;
    private BigDecimal lngBurnerCost;
    private BigDecimal compositeNet;
    private BigDecimal zescoNet;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyAccountingPlanResponse from(SteamEnergyAccountingPlan e) {
        return from(e, null);
    }

    public static SteamEnergyAccountingPlanResponse from(SteamEnergyAccountingPlan e, Boolean isUpdate) {
        return SteamEnergyAccountingPlanResponse.builder()
                .accountingPlanId(e.getAccountingPlanId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .compositeSteamBuy(e.getCompositeSteamBuy())
                .zescoSteamBuy(e.getZescoSteamBuy())
                .lngBuy(e.getLngBuy())
                .incineratorCost(e.getIncineratorCost())
                .fluidCost(e.getFluidCost())
                .zescoOffset(e.getZescoOffset())
                .zescoPenalty(e.getZescoPenalty())
                .zescoAdvanceRepay(e.getZescoAdvanceRepay())
                .compositeLngWater(e.getCompositeLngWater())
                .compositeInvest(e.getCompositeInvest())
                .compositeIncome(e.getCompositeIncome())
                .landLease(e.getLandLease())
                .paperFuel(e.getPaperFuel())
                .livingFuel(e.getLivingFuel())
                .fuelVsPlan(e.getFuelVsPlan())
                .paperProdTon(e.getPaperProdTon())
                .tissueProdTon(e.getTissueProdTon())
                .steamProdTon(e.getSteamProdTon())
                .lngBurnerCost(e.getLngBurnerCost())
                .compositeNet(e.getCompositeNet())
                .zescoNet(e.getZescoNet())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
