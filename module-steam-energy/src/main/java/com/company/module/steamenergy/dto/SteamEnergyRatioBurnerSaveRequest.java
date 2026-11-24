package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 연료비 배분비율(버너 LNG) 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyRatioBurnerSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
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
}
