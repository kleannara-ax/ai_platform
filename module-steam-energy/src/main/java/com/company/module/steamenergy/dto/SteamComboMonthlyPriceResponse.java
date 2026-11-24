package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamComboMonthlyPrice;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 복합보일러 월 연료단가 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboMonthlyPriceResponse {

    private Long comboPriceId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal fuelPriceLngBoiler;
    private BigDecimal fuelPriceFluidized;
    private BigDecimal fuelPriceCombo;
    private BigDecimal fuelPriceWaste;
    private BigDecimal fuelPriceExternal;
    private BigDecimal ventLossUnitPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamComboMonthlyPriceResponse from(SteamComboMonthlyPrice e) {
        return from(e, null);
    }

    public static SteamComboMonthlyPriceResponse from(SteamComboMonthlyPrice e, Boolean isUpdate) {
        return SteamComboMonthlyPriceResponse.builder()
                .comboPriceId(e.getComboPriceId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .fuelPriceLngBoiler(e.getFuelPriceLngBoiler())
                .fuelPriceFluidized(e.getFuelPriceFluidized())
                .fuelPriceCombo(e.getFuelPriceCombo())
                .fuelPriceWaste(e.getFuelPriceWaste())
                .fuelPriceExternal(e.getFuelPriceExternal())
                .ventLossUnitPrice(e.getVentLossUnitPrice())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
