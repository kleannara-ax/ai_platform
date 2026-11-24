package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyFuelPlan;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 연료비 사업계획 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyFuelPlanResponse {

    private Long fuelPlanId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal paperProdTon;
    private BigDecimal tissueProdTon;
    private BigDecimal paperUnit;
    private BigDecimal tissueUnit;
    private BigDecimal paperTotalSteam;
    private BigDecimal tissueTotalSteam;
    private BigDecimal etcDisperser;
    private BigDecimal etcToc;
    private BigDecimal operatingDays;
    private BigDecimal prodIncinerator;
    private BigDecimal prodComposite;
    private BigDecimal prodExtBoiler;
    private BigDecimal prodFluid;
    private BigDecimal lngComposite;
    private BigDecimal lngTm3;
    private BigDecimal lngTm4;
    private BigDecimal lngTm5;
    private BigDecimal lngPm3;
    private BigDecimal priceLng;
    private BigDecimal priceIncinerator;
    private BigDecimal priceComposite;
    private BigDecimal priceExtBoiler;
    private BigDecimal priceFluid;
    private BigDecimal opexIncinerator;
    private BigDecimal opexFluid;
    private BigDecimal payExtAdvance;
    private BigDecimal payExtPenalty;
    private BigDecimal payIncinerator;
    private BigDecimal payFluid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyFuelPlanResponse from(SteamEnergyFuelPlan e) {
        return from(e, null);
    }

    public static SteamEnergyFuelPlanResponse from(SteamEnergyFuelPlan e, Boolean isUpdate) {
        return SteamEnergyFuelPlanResponse.builder()
                .fuelPlanId(e.getFuelPlanId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .paperProdTon(e.getPaperProdTon())
                .tissueProdTon(e.getTissueProdTon())
                .paperUnit(e.getPaperUnit())
                .tissueUnit(e.getTissueUnit())
                .paperTotalSteam(e.getPaperTotalSteam())
                .tissueTotalSteam(e.getTissueTotalSteam())
                .etcDisperser(e.getEtcDisperser())
                .etcToc(e.getEtcToc())
                .operatingDays(e.getOperatingDays())
                .prodIncinerator(e.getProdIncinerator())
                .prodComposite(e.getProdComposite())
                .prodExtBoiler(e.getProdExtBoiler())
                .prodFluid(e.getProdFluid())
                .lngComposite(e.getLngComposite())
                .lngTm3(e.getLngTm3())
                .lngTm4(e.getLngTm4())
                .lngTm5(e.getLngTm5())
                .lngPm3(e.getLngPm3())
                .priceLng(e.getPriceLng())
                .priceIncinerator(e.getPriceIncinerator())
                .priceComposite(e.getPriceComposite())
                .priceExtBoiler(e.getPriceExtBoiler())
                .priceFluid(e.getPriceFluid())
                .opexIncinerator(e.getOpexIncinerator())
                .opexFluid(e.getOpexFluid())
                .payExtAdvance(e.getPayExtAdvance())
                .payExtPenalty(e.getPayExtPenalty())
                .payIncinerator(e.getPayIncinerator())
                .payFluid(e.getPayFluid())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
