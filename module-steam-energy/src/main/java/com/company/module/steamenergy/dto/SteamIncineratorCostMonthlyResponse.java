package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamIncineratorCostMonthly;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 소각로 총 운영비용 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorCostMonthlyResponse {

    private Long costMonthlyId;
    private Integer yearNo;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamIncineratorCostMonthlyResponse from(SteamIncineratorCostMonthly e) {
        return from(e, null);
    }

    public static SteamIncineratorCostMonthlyResponse from(SteamIncineratorCostMonthly e, Boolean isUpdate) {
        return SteamIncineratorCostMonthlyResponse.builder()
                .costMonthlyId(e.getCostMonthlyId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .opBurnBurn1(e.getOpBurnBurn1())
                .opBurnBurn2(e.getOpBurnBurn2())
                .opBurnRate1(e.getOpBurnRate1())
                .opBurnRate2(e.getOpBurnRate2())
                .opCostUnit(e.getOpCostUnit())
                .opCostDeduct(e.getOpCostDeduct())
                .selfSrfInQty(e.getSelfSrfInQty())
                .selfSrfInRev(e.getSelfSrfInRev())
                .selfSrfFloorRecycle(e.getSelfSrfFloorRecycle())
                .selfSrfFloorBury(e.getSelfSrfFloorBury())
                .selfSrfFlySolid(e.getSelfSrfFlySolid())
                .selfSrfFlyBury(e.getSelfSrfFlyBury())
                .selfSrfCostFloor(e.getSelfSrfCostFloor())
                .selfSrfCostFly(e.getSelfSrfCostFly())
                .selfSrfCostMisc(e.getSelfSrfCostMisc())
                .selfUtilP1(e.getSelfUtilP1())
                .selfUtilP2(e.getSelfUtilP2())
                .selfUtilPrate(e.getSelfUtilPrate())
                .selfUtilNaohQty(e.getSelfUtilNaohQty())
                .selfUtilNaohUnit(e.getSelfUtilNaohUnit())
                .selfUtilWater(e.getSelfUtilWater())
                .selfUtilWasteCharge(e.getSelfUtilWasteCharge())
                .selfUtilEtc(e.getSelfUtilEtc())
                .selfTotalTotal(e.getSelfTotalTotal())
                .selfTotalBurnRate(e.getSelfTotalBurnRate())
                .selfTotalSteamRate(e.getSelfTotalSteamRate())
                .matOilU1(e.getMatOilU1())
                .matOilU2(e.getMatOilU2())
                .matOilUnit(e.getMatOilUnit())
                .matOilCkQty(e.getMatOilCkQty())
                .matOilCkUnit(e.getMatOilCkUnit())
                .matChemUQty(e.getMatChemUQty())
                .matChemUUnit(e.getMatChemUUnit())
                .matChemSQty(e.getMatChemSQty())
                .matChemSUnit(e.getMatChemSUnit())
                .matMiscAQty(e.getMatMiscAQty())
                .matMiscAUnit(e.getMatMiscAUnit())
                .matMiscKQty(e.getMatMiscKQty())
                .matMiscKUnit(e.getMatMiscKUnit())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
