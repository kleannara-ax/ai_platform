package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyRatioBurner;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 연료비 배분비율(버너 LNG) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioBurnerResponse {

    private Long ratioBurnerId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal lngPm3;
    private BigDecimal lngTm3;
    private BigDecimal lngTm4;
    private BigDecimal lngTm5;
    private BigDecimal lngPricePrevYear;
    private BigDecimal lngPrice;
    private BigDecimal lngPriceChangeRate;
    private BigDecimal lngCostTotal;
    private BigDecimal lngUnitPm3;
    private BigDecimal lngUnitTm3;
    private BigDecimal lngUnitTm4;
    private BigDecimal lngUnitTm5;
    private BigDecimal lngUnitTissueSubtotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyRatioBurnerResponse from(SteamEnergyRatioBurner e) {
        return from(e, null);
    }

    public static SteamEnergyRatioBurnerResponse from(SteamEnergyRatioBurner e, Boolean isUpdate) {
        return SteamEnergyRatioBurnerResponse.builder()
                .ratioBurnerId(e.getRatioBurnerId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .lngPm3(e.getLngPm3())
                .lngTm3(e.getLngTm3())
                .lngTm4(e.getLngTm4())
                .lngTm5(e.getLngTm5())
                .lngPricePrevYear(e.getLngPricePrevYear())
                .lngPrice(e.getLngPrice())
                .lngPriceChangeRate(e.getLngPriceChangeRate())
                .lngCostTotal(e.getLngCostTotal())
                .lngUnitPm3(e.getLngUnitPm3())
                .lngUnitTm3(e.getLngUnitTm3())
                .lngUnitTm4(e.getLngUnitTm4())
                .lngUnitTm5(e.getLngUnitTm5())
                .lngUnitTissueSubtotal(e.getLngUnitTissueSubtotal())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
