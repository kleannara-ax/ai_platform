package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 폐합성소각로 운전일지 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamWasteIncinDailyLogSaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal burnU1Self;
    private BigDecimal burnU1Recycle;
    private BigDecimal burnU1Generated;
    private BigDecimal burnU1RunHours;
    private BigDecimal burnU1ExtStock;
    private BigDecimal burnU2Self;
    private BigDecimal burnU2Recycle;
    private BigDecimal burnU2Generated;
    private BigDecimal burnU2RunHours;
    private BigDecimal burnU2SelfStock;
    private BigDecimal burnExtTotal;
    private BigDecimal steamU1;
    private BigDecimal steamU2First;
    private BigDecimal steamU2Second;
    private BigDecimal steamAgency;
    private String steamPressure;
    private BigDecimal waterU1First;
    private BigDecimal waterU1Second;
    private BigDecimal waterU2First;
    private BigDecimal waterU2Second;
    private BigDecimal powerMeter;
    private BigDecimal powerU1;
    private BigDecimal powerU2;
    private BigDecimal powerEp;
    private BigDecimal flyAshU1;
    private BigDecimal flyAshU2;
    private BigDecimal bottomAshU1;
    private BigDecimal bottomAshU2;
    private BigDecimal ureaIn;
    private BigDecimal ureaUse;
    private BigDecimal ureaStock;
    private BigDecimal boilerChemIn;
    private BigDecimal boilerChemUse;
    private BigDecimal boilerChemStock;
    private BigDecimal saltIn;
    private BigDecimal saltUse;
    private BigDecimal saltFluidized;
    private BigDecimal saltStock;
    private BigDecimal causticIn;
    private BigDecimal causticUse;
    private BigDecimal causticStock;
    private BigDecimal carbonIn;
    private BigDecimal carbonUse;
    private BigDecimal carbonStock;
    private BigDecimal oilIn;
    private BigDecimal oilU1;
    private BigDecimal oilU2;
    private BigDecimal oilSludge1;
    private BigDecimal oilSludge2;
    private BigDecimal oilStock;
    private BigDecimal clinkerSideWall;
    private BigDecimal clinkerDrop;
    private BigDecimal clinkerIn;
    private BigDecimal clinkerUse;
    private BigDecimal clinkerStock;
    private BigDecimal tmsU1Dust;
    private BigDecimal tmsU1Co;
    private BigDecimal tmsU1Nox;
    private BigDecimal tmsU1Sox;
    private BigDecimal tmsU1Hcl;
    private BigDecimal tmsU1Temp;
    private BigDecimal tmsU2Dust;
    private BigDecimal tmsU2Co;
    private BigDecimal tmsU2Nox;
    private BigDecimal tmsU2Sox;
    private BigDecimal tmsU2Hcl;
    private BigDecimal tmsU2Temp;
    private BigDecimal inboundVendor01;
    private BigDecimal inboundVendor02;
    private BigDecimal inboundVendor03;
    private BigDecimal inboundVendor04;
    private BigDecimal inboundVendor05;
    private BigDecimal inboundVendor06;
    private BigDecimal inboundVendor07;
    private BigDecimal inboundVendor08;
    private BigDecimal inboundVendor09;
    private BigDecimal inboundVendor10;
    private BigDecimal inboundVendor11;
    private BigDecimal inboundVendor12;
    private BigDecimal inboundVendor13;
    private BigDecimal inboundVendor14;
    private BigDecimal genWood;
    private BigDecimal genSaengbul;
    private BigDecimal genPlastic;
    private BigDecimal genReject;
    private BigDecimal genRagger;
    private BigDecimal genStock;
}
