package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamWasteIncinDailyLog;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 폐합성소각로 운전일지 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamWasteIncinDailyLogResponse {

    private Long wasteIncinLogId;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamWasteIncinDailyLogResponse from(SteamWasteIncinDailyLog e) {
        return from(e, null);
    }

    public static SteamWasteIncinDailyLogResponse from(SteamWasteIncinDailyLog e, Boolean isUpdate) {
        return SteamWasteIncinDailyLogResponse.builder()
                .wasteIncinLogId(e.getWasteIncinLogId())
                .usageDate(e.getUsageDate())
                .burnU1Self(e.getBurnU1Self())
                .burnU1Recycle(e.getBurnU1Recycle())
                .burnU1Generated(e.getBurnU1Generated())
                .burnU1RunHours(e.getBurnU1RunHours())
                .burnU1ExtStock(e.getBurnU1ExtStock())
                .burnU2Self(e.getBurnU2Self())
                .burnU2Recycle(e.getBurnU2Recycle())
                .burnU2Generated(e.getBurnU2Generated())
                .burnU2RunHours(e.getBurnU2RunHours())
                .burnU2SelfStock(e.getBurnU2SelfStock())
                .burnExtTotal(e.getBurnExtTotal())
                .steamU1(e.getSteamU1())
                .steamU2First(e.getSteamU2First())
                .steamU2Second(e.getSteamU2Second())
                .steamAgency(e.getSteamAgency())
                .steamPressure(e.getSteamPressure())
                .waterU1First(e.getWaterU1First())
                .waterU1Second(e.getWaterU1Second())
                .waterU2First(e.getWaterU2First())
                .waterU2Second(e.getWaterU2Second())
                .powerMeter(e.getPowerMeter())
                .powerU1(e.getPowerU1())
                .powerU2(e.getPowerU2())
                .powerEp(e.getPowerEp())
                .flyAshU1(e.getFlyAshU1())
                .flyAshU2(e.getFlyAshU2())
                .bottomAshU1(e.getBottomAshU1())
                .bottomAshU2(e.getBottomAshU2())
                .ureaIn(e.getUreaIn())
                .ureaUse(e.getUreaUse())
                .ureaStock(e.getUreaStock())
                .boilerChemIn(e.getBoilerChemIn())
                .boilerChemUse(e.getBoilerChemUse())
                .boilerChemStock(e.getBoilerChemStock())
                .saltIn(e.getSaltIn())
                .saltUse(e.getSaltUse())
                .saltFluidized(e.getSaltFluidized())
                .saltStock(e.getSaltStock())
                .causticIn(e.getCausticIn())
                .causticUse(e.getCausticUse())
                .causticStock(e.getCausticStock())
                .carbonIn(e.getCarbonIn())
                .carbonUse(e.getCarbonUse())
                .carbonStock(e.getCarbonStock())
                .oilIn(e.getOilIn())
                .oilU1(e.getOilU1())
                .oilU2(e.getOilU2())
                .oilSludge1(e.getOilSludge1())
                .oilSludge2(e.getOilSludge2())
                .oilStock(e.getOilStock())
                .clinkerSideWall(e.getClinkerSideWall())
                .clinkerDrop(e.getClinkerDrop())
                .clinkerIn(e.getClinkerIn())
                .clinkerUse(e.getClinkerUse())
                .clinkerStock(e.getClinkerStock())
                .tmsU1Dust(e.getTmsU1Dust())
                .tmsU1Co(e.getTmsU1Co())
                .tmsU1Nox(e.getTmsU1Nox())
                .tmsU1Sox(e.getTmsU1Sox())
                .tmsU1Hcl(e.getTmsU1Hcl())
                .tmsU1Temp(e.getTmsU1Temp())
                .tmsU2Dust(e.getTmsU2Dust())
                .tmsU2Co(e.getTmsU2Co())
                .tmsU2Nox(e.getTmsU2Nox())
                .tmsU2Sox(e.getTmsU2Sox())
                .tmsU2Hcl(e.getTmsU2Hcl())
                .tmsU2Temp(e.getTmsU2Temp())
                .inboundVendor01(e.getInboundVendor01())
                .inboundVendor02(e.getInboundVendor02())
                .inboundVendor03(e.getInboundVendor03())
                .inboundVendor04(e.getInboundVendor04())
                .inboundVendor05(e.getInboundVendor05())
                .inboundVendor06(e.getInboundVendor06())
                .inboundVendor07(e.getInboundVendor07())
                .inboundVendor08(e.getInboundVendor08())
                .inboundVendor09(e.getInboundVendor09())
                .inboundVendor10(e.getInboundVendor10())
                .inboundVendor11(e.getInboundVendor11())
                .inboundVendor12(e.getInboundVendor12())
                .inboundVendor13(e.getInboundVendor13())
                .inboundVendor14(e.getInboundVendor14())
                .genWood(e.getGenWood())
                .genSaengbul(e.getGenSaengbul())
                .genPlastic(e.getGenPlastic())
                .genReject(e.getGenReject())
                .genRagger(e.getGenRagger())
                .genStock(e.getGenStock())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
