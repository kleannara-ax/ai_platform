package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 폐합성소각로 운전일지 — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 폐합성수지 소각로 › 운전일지 (waste-incinerator-log.html)
 * <p>Table: steam_waste_incin_daily_log
 * <p>PK: WASTE_INCIN_LOG_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_waste_incin_daily_log")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamWasteIncinDailyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "WASTE_INCIN_LOG_ID")
    private Long wasteIncinLogId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "BURN_U1_SELF", precision = 18, scale = 6)
    private BigDecimal burnU1Self;

    @Column(name = "BURN_U1_RECYCLE", precision = 18, scale = 6)
    private BigDecimal burnU1Recycle;

    @Column(name = "BURN_U1_GENERATED", precision = 18, scale = 6)
    private BigDecimal burnU1Generated;

    @Column(name = "BURN_U1_RUN_HOURS", precision = 18, scale = 6)
    private BigDecimal burnU1RunHours;

    @Column(name = "BURN_U1_EXT_STOCK", precision = 18, scale = 6)
    private BigDecimal burnU1ExtStock;

    @Column(name = "BURN_U2_SELF", precision = 18, scale = 6)
    private BigDecimal burnU2Self;

    @Column(name = "BURN_U2_RECYCLE", precision = 18, scale = 6)
    private BigDecimal burnU2Recycle;

    @Column(name = "BURN_U2_GENERATED", precision = 18, scale = 6)
    private BigDecimal burnU2Generated;

    @Column(name = "BURN_U2_RUN_HOURS", precision = 18, scale = 6)
    private BigDecimal burnU2RunHours;

    @Column(name = "BURN_U2_SELF_STOCK", precision = 18, scale = 6)
    private BigDecimal burnU2SelfStock;

    @Column(name = "BURN_EXT_TOTAL", precision = 18, scale = 6)
    private BigDecimal burnExtTotal;

    @Column(name = "STEAM_U1", precision = 18, scale = 6)
    private BigDecimal steamU1;

    @Column(name = "STEAM_U2_FIRST", precision = 18, scale = 6)
    private BigDecimal steamU2First;

    @Column(name = "STEAM_U2_SECOND", precision = 18, scale = 6)
    private BigDecimal steamU2Second;

    @Column(name = "STEAM_AGENCY", precision = 18, scale = 6)
    private BigDecimal steamAgency;

    @Column(name = "STEAM_PRESSURE", length = 20)
    private String steamPressure;

    @Column(name = "WATER_U1_FIRST", precision = 18, scale = 6)
    private BigDecimal waterU1First;

    @Column(name = "WATER_U1_SECOND", precision = 18, scale = 6)
    private BigDecimal waterU1Second;

    @Column(name = "WATER_U2_FIRST", precision = 18, scale = 6)
    private BigDecimal waterU2First;

    @Column(name = "WATER_U2_SECOND", precision = 18, scale = 6)
    private BigDecimal waterU2Second;

    @Column(name = "POWER_METER", precision = 18, scale = 6)
    private BigDecimal powerMeter;

    @Column(name = "POWER_U1", precision = 18, scale = 6)
    private BigDecimal powerU1;

    @Column(name = "POWER_U2", precision = 18, scale = 6)
    private BigDecimal powerU2;

    @Column(name = "POWER_EP", precision = 18, scale = 6)
    private BigDecimal powerEp;

    @Column(name = "FLY_ASH_U1", precision = 18, scale = 6)
    private BigDecimal flyAshU1;

    @Column(name = "FLY_ASH_U2", precision = 18, scale = 6)
    private BigDecimal flyAshU2;

    @Column(name = "BOTTOM_ASH_U1", precision = 18, scale = 6)
    private BigDecimal bottomAshU1;

    @Column(name = "BOTTOM_ASH_U2", precision = 18, scale = 6)
    private BigDecimal bottomAshU2;

    @Column(name = "UREA_IN", precision = 18, scale = 6)
    private BigDecimal ureaIn;

    @Column(name = "UREA_USE", precision = 18, scale = 6)
    private BigDecimal ureaUse;

    @Column(name = "UREA_STOCK", precision = 18, scale = 6)
    private BigDecimal ureaStock;

    @Column(name = "BOILER_CHEM_IN", precision = 18, scale = 6)
    private BigDecimal boilerChemIn;

    @Column(name = "BOILER_CHEM_USE", precision = 18, scale = 6)
    private BigDecimal boilerChemUse;

    @Column(name = "BOILER_CHEM_STOCK", precision = 18, scale = 6)
    private BigDecimal boilerChemStock;

    @Column(name = "SALT_IN", precision = 18, scale = 6)
    private BigDecimal saltIn;

    @Column(name = "SALT_USE", precision = 18, scale = 6)
    private BigDecimal saltUse;

    @Column(name = "SALT_FLUIDIZED", precision = 18, scale = 6)
    private BigDecimal saltFluidized;

    @Column(name = "SALT_STOCK", precision = 18, scale = 6)
    private BigDecimal saltStock;

    @Column(name = "CAUSTIC_IN", precision = 18, scale = 6)
    private BigDecimal causticIn;

    @Column(name = "CAUSTIC_USE", precision = 18, scale = 6)
    private BigDecimal causticUse;

    @Column(name = "CAUSTIC_STOCK", precision = 18, scale = 6)
    private BigDecimal causticStock;

    @Column(name = "CARBON_IN", precision = 18, scale = 6)
    private BigDecimal carbonIn;

    @Column(name = "CARBON_USE", precision = 18, scale = 6)
    private BigDecimal carbonUse;

    @Column(name = "CARBON_STOCK", precision = 18, scale = 6)
    private BigDecimal carbonStock;

    @Column(name = "OIL_IN", precision = 18, scale = 6)
    private BigDecimal oilIn;

    @Column(name = "OIL_U1", precision = 18, scale = 6)
    private BigDecimal oilU1;

    @Column(name = "OIL_U2", precision = 18, scale = 6)
    private BigDecimal oilU2;

    @Column(name = "OIL_SLUDGE1", precision = 18, scale = 6)
    private BigDecimal oilSludge1;

    @Column(name = "OIL_SLUDGE2", precision = 18, scale = 6)
    private BigDecimal oilSludge2;

    @Column(name = "OIL_STOCK", precision = 18, scale = 6)
    private BigDecimal oilStock;

    @Column(name = "CLINKER_SIDE_WALL", precision = 18, scale = 6)
    private BigDecimal clinkerSideWall;

    @Column(name = "CLINKER_DROP", precision = 18, scale = 6)
    private BigDecimal clinkerDrop;

    @Column(name = "CLINKER_IN", precision = 18, scale = 6)
    private BigDecimal clinkerIn;

    @Column(name = "CLINKER_USE", precision = 18, scale = 6)
    private BigDecimal clinkerUse;

    @Column(name = "CLINKER_STOCK", precision = 18, scale = 6)
    private BigDecimal clinkerStock;

    @Column(name = "TMS_U1_DUST", precision = 18, scale = 6)
    private BigDecimal tmsU1Dust;

    @Column(name = "TMS_U1_CO", precision = 18, scale = 6)
    private BigDecimal tmsU1Co;

    @Column(name = "TMS_U1_NOX", precision = 18, scale = 6)
    private BigDecimal tmsU1Nox;

    @Column(name = "TMS_U1_SOX", precision = 18, scale = 6)
    private BigDecimal tmsU1Sox;

    @Column(name = "TMS_U1_HCL", precision = 18, scale = 6)
    private BigDecimal tmsU1Hcl;

    @Column(name = "TMS_U1_TEMP", precision = 18, scale = 6)
    private BigDecimal tmsU1Temp;

    @Column(name = "TMS_U2_DUST", precision = 18, scale = 6)
    private BigDecimal tmsU2Dust;

    @Column(name = "TMS_U2_CO", precision = 18, scale = 6)
    private BigDecimal tmsU2Co;

    @Column(name = "TMS_U2_NOX", precision = 18, scale = 6)
    private BigDecimal tmsU2Nox;

    @Column(name = "TMS_U2_SOX", precision = 18, scale = 6)
    private BigDecimal tmsU2Sox;

    @Column(name = "TMS_U2_HCL", precision = 18, scale = 6)
    private BigDecimal tmsU2Hcl;

    @Column(name = "TMS_U2_TEMP", precision = 18, scale = 6)
    private BigDecimal tmsU2Temp;

    @Column(name = "INBOUND_VENDOR01", precision = 18, scale = 6)
    private BigDecimal inboundVendor01;

    @Column(name = "INBOUND_VENDOR02", precision = 18, scale = 6)
    private BigDecimal inboundVendor02;

    @Column(name = "INBOUND_VENDOR03", precision = 18, scale = 6)
    private BigDecimal inboundVendor03;

    @Column(name = "INBOUND_VENDOR04", precision = 18, scale = 6)
    private BigDecimal inboundVendor04;

    @Column(name = "INBOUND_VENDOR05", precision = 18, scale = 6)
    private BigDecimal inboundVendor05;

    @Column(name = "INBOUND_VENDOR06", precision = 18, scale = 6)
    private BigDecimal inboundVendor06;

    @Column(name = "INBOUND_VENDOR07", precision = 18, scale = 6)
    private BigDecimal inboundVendor07;

    @Column(name = "INBOUND_VENDOR08", precision = 18, scale = 6)
    private BigDecimal inboundVendor08;

    @Column(name = "INBOUND_VENDOR09", precision = 18, scale = 6)
    private BigDecimal inboundVendor09;

    @Column(name = "INBOUND_VENDOR10", precision = 18, scale = 6)
    private BigDecimal inboundVendor10;

    @Column(name = "INBOUND_VENDOR11", precision = 18, scale = 6)
    private BigDecimal inboundVendor11;

    @Column(name = "INBOUND_VENDOR12", precision = 18, scale = 6)
    private BigDecimal inboundVendor12;

    @Column(name = "INBOUND_VENDOR13", precision = 18, scale = 6)
    private BigDecimal inboundVendor13;

    @Column(name = "INBOUND_VENDOR14", precision = 18, scale = 6)
    private BigDecimal inboundVendor14;

    @Column(name = "GEN_WOOD", precision = 18, scale = 6)
    private BigDecimal genWood;

    @Column(name = "GEN_SAENGBUL", precision = 18, scale = 6)
    private BigDecimal genSaengbul;

    @Column(name = "GEN_PLASTIC", precision = 18, scale = 6)
    private BigDecimal genPlastic;

    @Column(name = "GEN_REJECT", precision = 18, scale = 6)
    private BigDecimal genReject;

    @Column(name = "GEN_RAGGER", precision = 18, scale = 6)
    private BigDecimal genRagger;

    @Column(name = "GEN_STOCK", precision = 18, scale = 6)
    private BigDecimal genStock;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(KST);
        if (this.deletedYn == null) this.deletedYn = "N";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(KST);
    }

    /** 행 전체 값 갱신. null 은 빈 값으로 저장한다. */
    public void update(BigDecimal burnU1Self, BigDecimal burnU1Recycle, BigDecimal burnU1Generated, BigDecimal burnU1RunHours, BigDecimal burnU1ExtStock, BigDecimal burnU2Self, BigDecimal burnU2Recycle, BigDecimal burnU2Generated, BigDecimal burnU2RunHours, BigDecimal burnU2SelfStock, BigDecimal burnExtTotal, BigDecimal steamU1, BigDecimal steamU2First, BigDecimal steamU2Second, BigDecimal steamAgency, String steamPressure, BigDecimal waterU1First, BigDecimal waterU1Second, BigDecimal waterU2First, BigDecimal waterU2Second, BigDecimal powerMeter, BigDecimal powerU1, BigDecimal powerU2, BigDecimal powerEp, BigDecimal flyAshU1, BigDecimal flyAshU2, BigDecimal bottomAshU1, BigDecimal bottomAshU2, BigDecimal ureaIn, BigDecimal ureaUse, BigDecimal ureaStock, BigDecimal boilerChemIn, BigDecimal boilerChemUse, BigDecimal boilerChemStock, BigDecimal saltIn, BigDecimal saltUse, BigDecimal saltFluidized, BigDecimal saltStock, BigDecimal causticIn, BigDecimal causticUse, BigDecimal causticStock, BigDecimal carbonIn, BigDecimal carbonUse, BigDecimal carbonStock, BigDecimal oilIn, BigDecimal oilU1, BigDecimal oilU2, BigDecimal oilSludge1, BigDecimal oilSludge2, BigDecimal oilStock, BigDecimal clinkerSideWall, BigDecimal clinkerDrop, BigDecimal clinkerIn, BigDecimal clinkerUse, BigDecimal clinkerStock, BigDecimal tmsU1Dust, BigDecimal tmsU1Co, BigDecimal tmsU1Nox, BigDecimal tmsU1Sox, BigDecimal tmsU1Hcl, BigDecimal tmsU1Temp, BigDecimal tmsU2Dust, BigDecimal tmsU2Co, BigDecimal tmsU2Nox, BigDecimal tmsU2Sox, BigDecimal tmsU2Hcl, BigDecimal tmsU2Temp, BigDecimal inboundVendor01, BigDecimal inboundVendor02, BigDecimal inboundVendor03, BigDecimal inboundVendor04, BigDecimal inboundVendor05, BigDecimal inboundVendor06, BigDecimal inboundVendor07, BigDecimal inboundVendor08, BigDecimal inboundVendor09, BigDecimal inboundVendor10, BigDecimal inboundVendor11, BigDecimal inboundVendor12, BigDecimal inboundVendor13, BigDecimal inboundVendor14, BigDecimal genWood, BigDecimal genSaengbul, BigDecimal genPlastic, BigDecimal genReject, BigDecimal genRagger, BigDecimal genStock, Long updatedBy) {
        this.burnU1Self = burnU1Self;
        this.burnU1Recycle = burnU1Recycle;
        this.burnU1Generated = burnU1Generated;
        this.burnU1RunHours = burnU1RunHours;
        this.burnU1ExtStock = burnU1ExtStock;
        this.burnU2Self = burnU2Self;
        this.burnU2Recycle = burnU2Recycle;
        this.burnU2Generated = burnU2Generated;
        this.burnU2RunHours = burnU2RunHours;
        this.burnU2SelfStock = burnU2SelfStock;
        this.burnExtTotal = burnExtTotal;
        this.steamU1 = steamU1;
        this.steamU2First = steamU2First;
        this.steamU2Second = steamU2Second;
        this.steamAgency = steamAgency;
        this.steamPressure = steamPressure;
        this.waterU1First = waterU1First;
        this.waterU1Second = waterU1Second;
        this.waterU2First = waterU2First;
        this.waterU2Second = waterU2Second;
        this.powerMeter = powerMeter;
        this.powerU1 = powerU1;
        this.powerU2 = powerU2;
        this.powerEp = powerEp;
        this.flyAshU1 = flyAshU1;
        this.flyAshU2 = flyAshU2;
        this.bottomAshU1 = bottomAshU1;
        this.bottomAshU2 = bottomAshU2;
        this.ureaIn = ureaIn;
        this.ureaUse = ureaUse;
        this.ureaStock = ureaStock;
        this.boilerChemIn = boilerChemIn;
        this.boilerChemUse = boilerChemUse;
        this.boilerChemStock = boilerChemStock;
        this.saltIn = saltIn;
        this.saltUse = saltUse;
        this.saltFluidized = saltFluidized;
        this.saltStock = saltStock;
        this.causticIn = causticIn;
        this.causticUse = causticUse;
        this.causticStock = causticStock;
        this.carbonIn = carbonIn;
        this.carbonUse = carbonUse;
        this.carbonStock = carbonStock;
        this.oilIn = oilIn;
        this.oilU1 = oilU1;
        this.oilU2 = oilU2;
        this.oilSludge1 = oilSludge1;
        this.oilSludge2 = oilSludge2;
        this.oilStock = oilStock;
        this.clinkerSideWall = clinkerSideWall;
        this.clinkerDrop = clinkerDrop;
        this.clinkerIn = clinkerIn;
        this.clinkerUse = clinkerUse;
        this.clinkerStock = clinkerStock;
        this.tmsU1Dust = tmsU1Dust;
        this.tmsU1Co = tmsU1Co;
        this.tmsU1Nox = tmsU1Nox;
        this.tmsU1Sox = tmsU1Sox;
        this.tmsU1Hcl = tmsU1Hcl;
        this.tmsU1Temp = tmsU1Temp;
        this.tmsU2Dust = tmsU2Dust;
        this.tmsU2Co = tmsU2Co;
        this.tmsU2Nox = tmsU2Nox;
        this.tmsU2Sox = tmsU2Sox;
        this.tmsU2Hcl = tmsU2Hcl;
        this.tmsU2Temp = tmsU2Temp;
        this.inboundVendor01 = inboundVendor01;
        this.inboundVendor02 = inboundVendor02;
        this.inboundVendor03 = inboundVendor03;
        this.inboundVendor04 = inboundVendor04;
        this.inboundVendor05 = inboundVendor05;
        this.inboundVendor06 = inboundVendor06;
        this.inboundVendor07 = inboundVendor07;
        this.inboundVendor08 = inboundVendor08;
        this.inboundVendor09 = inboundVendor09;
        this.inboundVendor10 = inboundVendor10;
        this.inboundVendor11 = inboundVendor11;
        this.inboundVendor12 = inboundVendor12;
        this.inboundVendor13 = inboundVendor13;
        this.inboundVendor14 = inboundVendor14;
        this.genWood = genWood;
        this.genSaengbul = genSaengbul;
        this.genPlastic = genPlastic;
        this.genReject = genReject;
        this.genRagger = genRagger;
        this.genStock = genStock;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now(KST);
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
