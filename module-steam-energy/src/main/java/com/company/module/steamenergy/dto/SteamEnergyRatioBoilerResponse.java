package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyRatioBoiler;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 연료비 배분비율(보일러별) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioBoilerResponse {

    private Long ratioBoilerId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal steamLngBoiler;
    private BigDecimal steamComposite;
    private BigDecimal steamIncinerator;
    private BigDecimal steamFluid;
    private BigDecimal steamExtBoiler;
    private BigDecimal fluidVentTon;
    private BigDecimal compositePenaltyTon;
    private BigDecimal compositeGuaranteeTon;
    private BigDecimal costLngBoiler;
    private BigDecimal costComposite;
    private BigDecimal costIncinerator;
    private BigDecimal costFluid;
    private BigDecimal costExtBoiler;
    private BigDecimal fluidInternalVentCost;
    private BigDecimal priceLngBoiler;
    private BigDecimal priceComposite;
    private BigDecimal priceIncinerator;
    private BigDecimal priceFluid;
    private BigDecimal priceExtBoiler;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyRatioBoilerResponse from(SteamEnergyRatioBoiler e) {
        return from(e, null);
    }

    public static SteamEnergyRatioBoilerResponse from(SteamEnergyRatioBoiler e, Boolean isUpdate) {
        return SteamEnergyRatioBoilerResponse.builder()
                .ratioBoilerId(e.getRatioBoilerId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .steamLngBoiler(e.getSteamLngBoiler())
                .steamComposite(e.getSteamComposite())
                .steamIncinerator(e.getSteamIncinerator())
                .steamFluid(e.getSteamFluid())
                .steamExtBoiler(e.getSteamExtBoiler())
                .fluidVentTon(e.getFluidVentTon())
                .compositePenaltyTon(e.getCompositePenaltyTon())
                .compositeGuaranteeTon(e.getCompositeGuaranteeTon())
                .costLngBoiler(e.getCostLngBoiler())
                .costComposite(e.getCostComposite())
                .costIncinerator(e.getCostIncinerator())
                .costFluid(e.getCostFluid())
                .costExtBoiler(e.getCostExtBoiler())
                .fluidInternalVentCost(e.getFluidInternalVentCost())
                .priceLngBoiler(e.getPriceLngBoiler())
                .priceComposite(e.getPriceComposite())
                .priceIncinerator(e.getPriceIncinerator())
                .priceFluid(e.getPriceFluid())
                .priceExtBoiler(e.getPriceExtBoiler())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
