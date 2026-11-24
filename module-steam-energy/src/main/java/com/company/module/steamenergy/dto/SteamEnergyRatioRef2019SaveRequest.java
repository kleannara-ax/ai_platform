package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 연료비 배분비율(2019 참고) 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyRatioRef2019SaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal t18Pm1;
    private BigDecimal t18Pm2;
    private BigDecimal t18Pm3;
    private BigDecimal t18PaperSubtotal;
    private BigDecimal t18Tm1;
    private BigDecimal t18Tm3;
    private BigDecimal t18Tm4;
    private BigDecimal t18Tm5;
    private BigDecimal t18TissueSubtotal;
    private BigDecimal t18Total;
    private BigDecimal t19Pm1;
    private BigDecimal t19Pm2;
    private BigDecimal t19Pm3;
    private BigDecimal t19PaperSubtotal;
    private BigDecimal t19Tm1;
    private BigDecimal t19Tm3;
    private BigDecimal t19Tm4;
    private BigDecimal t19Tm5;
    private BigDecimal t19TissueSubtotal;
    private BigDecimal t19Total;
    private BigDecimal t20Pm1;
    private BigDecimal t20Pm2;
    private BigDecimal t20Pm3;
    private BigDecimal t20PaperSubtotal;
    private BigDecimal t20Tm1;
    private BigDecimal t20Tm3;
    private BigDecimal t20Tm4;
    private BigDecimal t20Tm5;
    private BigDecimal t20TissueSubtotal;
    private BigDecimal t20Total;
    private BigDecimal t20TotalEnergyCost;
}
