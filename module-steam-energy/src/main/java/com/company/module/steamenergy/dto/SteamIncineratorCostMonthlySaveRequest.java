package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 소각로 총 운영비용 저장 요청 — (YEAR_NO, MONTH_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamIncineratorCostMonthlySaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal opBurnBurn1;
    private BigDecimal opBurnBurn2;
    private BigDecimal opBurnRate1;
    private BigDecimal opBurnRate2;
    private BigDecimal opCostUnit;
    private BigDecimal opCostDeduct;
    private BigDecimal selfSrfInQty;
    private BigDecimal selfSrfInRev;
    private BigDecimal selfSrfFloorRecycle;
    private BigDecimal selfSrfFloorBury;
    private BigDecimal selfSrfFlySolid;
    private BigDecimal selfSrfFlyBury;
    private BigDecimal selfSrfCostFloor;
    private BigDecimal selfSrfCostFly;
    private BigDecimal selfSrfCostMisc;
    private BigDecimal selfUtilP1;
    private BigDecimal selfUtilP2;
    private BigDecimal selfUtilPrate;
    private BigDecimal selfUtilNaohQty;
    private BigDecimal selfUtilNaohUnit;
    private BigDecimal selfUtilWater;
    private BigDecimal selfUtilWasteCharge;
    private BigDecimal selfUtilEtc;
    private BigDecimal selfTotalTotal;
    private BigDecimal selfTotalBurnRate;
    private BigDecimal selfTotalSteamRate;
    private BigDecimal matOilU1;
    private BigDecimal matOilU2;
    private BigDecimal matOilUnit;
    private BigDecimal matOilCkQty;
    private BigDecimal matOilCkUnit;
    private BigDecimal matChemUQty;
    private BigDecimal matChemUUnit;
    private BigDecimal matChemSQty;
    private BigDecimal matChemSUnit;
    private BigDecimal matMiscAQty;
    private BigDecimal matMiscAUnit;
    private BigDecimal matMiscKQty;
    private BigDecimal matMiscKUnit;
}
