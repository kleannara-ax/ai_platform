package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyRatioMachine;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 연료비 배분비율(호기별) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioMachineResponse {

    private Long ratioMachineId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal fuelPm2;
    private BigDecimal fuelPm3;
    private BigDecimal fuelPaperSubtotal;
    private BigDecimal fuelTm3;
    private BigDecimal fuelTm4;
    private BigDecimal fuelTm5;
    private BigDecimal fuelTissueSubtotal;
    private BigDecimal fuelSteamProdTon;
    private BigDecimal usagePm2;
    private BigDecimal usagePm3;
    private BigDecimal usageTm3;
    private BigDecimal usageTm4;
    private BigDecimal usageTm5;
    private BigDecimal usageTotalProdSteam;
    private BigDecimal usageEtc;
    private BigDecimal prodPm2Kg;
    private BigDecimal prodPm3Kg;
    private BigDecimal prodPaperSubtotalKg;
    private BigDecimal prodTm3Kg;
    private BigDecimal prodTm4Kg;
    private BigDecimal prodTm5Kg;
    private BigDecimal prodTissueSubtotalKg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyRatioMachineResponse from(SteamEnergyRatioMachine e) {
        return from(e, null);
    }

    public static SteamEnergyRatioMachineResponse from(SteamEnergyRatioMachine e, Boolean isUpdate) {
        return SteamEnergyRatioMachineResponse.builder()
                .ratioMachineId(e.getRatioMachineId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .fuelPm2(e.getFuelPm2())
                .fuelPm3(e.getFuelPm3())
                .fuelPaperSubtotal(e.getFuelPaperSubtotal())
                .fuelTm3(e.getFuelTm3())
                .fuelTm4(e.getFuelTm4())
                .fuelTm5(e.getFuelTm5())
                .fuelTissueSubtotal(e.getFuelTissueSubtotal())
                .fuelSteamProdTon(e.getFuelSteamProdTon())
                .usagePm2(e.getUsagePm2())
                .usagePm3(e.getUsagePm3())
                .usageTm3(e.getUsageTm3())
                .usageTm4(e.getUsageTm4())
                .usageTm5(e.getUsageTm5())
                .usageTotalProdSteam(e.getUsageTotalProdSteam())
                .usageEtc(e.getUsageEtc())
                .prodPm2Kg(e.getProdPm2Kg())
                .prodPm3Kg(e.getProdPm3Kg())
                .prodPaperSubtotalKg(e.getProdPaperSubtotalKg())
                .prodTm3Kg(e.getProdTm3Kg())
                .prodTm4Kg(e.getProdTm4Kg())
                .prodTm5Kg(e.getProdTm5Kg())
                .prodTissueSubtotalKg(e.getProdTissueSubtotalKg())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
