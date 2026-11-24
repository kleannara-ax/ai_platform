package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 소각로 총 운영비용 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 폐합성수지 소각로 › 총 운영비용 (incinerator-total-cost.html)
 * <p>Table: steam_incinerator_cost_monthly
 * <p>PK: COST_MONTHLY_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_incinerator_cost_monthly")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorCostMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COST_MONTHLY_ID")
    private Long costMonthlyId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "OP_BURN_BURN_1", precision = 18, scale = 6)
    private BigDecimal opBurnBurn1;

    @Column(name = "OP_BURN_BURN_2", precision = 18, scale = 6)
    private BigDecimal opBurnBurn2;

    @Column(name = "OP_BURN_RATE_1", precision = 18, scale = 6)
    private BigDecimal opBurnRate1;

    @Column(name = "OP_BURN_RATE_2", precision = 18, scale = 6)
    private BigDecimal opBurnRate2;

    @Column(name = "OP_COST_UNIT", precision = 18, scale = 6)
    private BigDecimal opCostUnit;

    @Column(name = "OP_COST_DEDUCT", precision = 18, scale = 6)
    private BigDecimal opCostDeduct;

    @Column(name = "SELF_SRF_IN_QTY", precision = 18, scale = 6)
    private BigDecimal selfSrfInQty;

    @Column(name = "SELF_SRF_IN_REV", precision = 18, scale = 6)
    private BigDecimal selfSrfInRev;

    @Column(name = "SELF_SRF_FLOOR_RECYCLE", precision = 18, scale = 6)
    private BigDecimal selfSrfFloorRecycle;

    @Column(name = "SELF_SRF_FLOOR_BURY", precision = 18, scale = 6)
    private BigDecimal selfSrfFloorBury;

    @Column(name = "SELF_SRF_FLY_SOLID", precision = 18, scale = 6)
    private BigDecimal selfSrfFlySolid;

    @Column(name = "SELF_SRF_FLY_BURY", precision = 18, scale = 6)
    private BigDecimal selfSrfFlyBury;

    @Column(name = "SELF_SRF_COST_FLOOR", precision = 18, scale = 6)
    private BigDecimal selfSrfCostFloor;

    @Column(name = "SELF_SRF_COST_FLY", precision = 18, scale = 6)
    private BigDecimal selfSrfCostFly;

    @Column(name = "SELF_SRF_COST_MISC", precision = 18, scale = 6)
    private BigDecimal selfSrfCostMisc;

    @Column(name = "SELF_UTIL_P1", precision = 18, scale = 6)
    private BigDecimal selfUtilP1;

    @Column(name = "SELF_UTIL_P2", precision = 18, scale = 6)
    private BigDecimal selfUtilP2;

    @Column(name = "SELF_UTIL_PRATE", precision = 18, scale = 6)
    private BigDecimal selfUtilPrate;

    @Column(name = "SELF_UTIL_NAOH_QTY", precision = 18, scale = 6)
    private BigDecimal selfUtilNaohQty;

    @Column(name = "SELF_UTIL_NAOH_UNIT", precision = 18, scale = 6)
    private BigDecimal selfUtilNaohUnit;

    @Column(name = "SELF_UTIL_WATER", precision = 18, scale = 6)
    private BigDecimal selfUtilWater;

    @Column(name = "SELF_UTIL_WASTE_CHARGE", precision = 18, scale = 6)
    private BigDecimal selfUtilWasteCharge;

    @Column(name = "SELF_UTIL_ETC", precision = 18, scale = 6)
    private BigDecimal selfUtilEtc;

    @Column(name = "SELF_TOTAL_TOTAL", precision = 18, scale = 6)
    private BigDecimal selfTotalTotal;

    @Column(name = "SELF_TOTAL_BURN_RATE", precision = 18, scale = 6)
    private BigDecimal selfTotalBurnRate;

    @Column(name = "SELF_TOTAL_STEAM_RATE", precision = 18, scale = 6)
    private BigDecimal selfTotalSteamRate;

    @Column(name = "MAT_OIL_U1", precision = 18, scale = 6)
    private BigDecimal matOilU1;

    @Column(name = "MAT_OIL_U2", precision = 18, scale = 6)
    private BigDecimal matOilU2;

    @Column(name = "MAT_OIL_UNIT", precision = 18, scale = 6)
    private BigDecimal matOilUnit;

    @Column(name = "MAT_OIL_CK_QTY", precision = 18, scale = 6)
    private BigDecimal matOilCkQty;

    @Column(name = "MAT_OIL_CK_UNIT", precision = 18, scale = 6)
    private BigDecimal matOilCkUnit;

    @Column(name = "MAT_CHEM_U_QTY", precision = 18, scale = 6)
    private BigDecimal matChemUQty;

    @Column(name = "MAT_CHEM_U_UNIT", precision = 18, scale = 6)
    private BigDecimal matChemUUnit;

    @Column(name = "MAT_CHEM_S_QTY", precision = 18, scale = 6)
    private BigDecimal matChemSQty;

    @Column(name = "MAT_CHEM_S_UNIT", precision = 18, scale = 6)
    private BigDecimal matChemSUnit;

    @Column(name = "MAT_MISC_A_QTY", precision = 18, scale = 6)
    private BigDecimal matMiscAQty;

    @Column(name = "MAT_MISC_A_UNIT", precision = 18, scale = 6)
    private BigDecimal matMiscAUnit;

    @Column(name = "MAT_MISC_K_QTY", precision = 18, scale = 6)
    private BigDecimal matMiscKQty;

    @Column(name = "MAT_MISC_K_UNIT", precision = 18, scale = 6)
    private BigDecimal matMiscKUnit;

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
    public void update(BigDecimal opBurnBurn1, BigDecimal opBurnBurn2, BigDecimal opBurnRate1, BigDecimal opBurnRate2, BigDecimal opCostUnit, BigDecimal opCostDeduct, BigDecimal selfSrfInQty, BigDecimal selfSrfInRev, BigDecimal selfSrfFloorRecycle, BigDecimal selfSrfFloorBury, BigDecimal selfSrfFlySolid, BigDecimal selfSrfFlyBury, BigDecimal selfSrfCostFloor, BigDecimal selfSrfCostFly, BigDecimal selfSrfCostMisc, BigDecimal selfUtilP1, BigDecimal selfUtilP2, BigDecimal selfUtilPrate, BigDecimal selfUtilNaohQty, BigDecimal selfUtilNaohUnit, BigDecimal selfUtilWater, BigDecimal selfUtilWasteCharge, BigDecimal selfUtilEtc, BigDecimal selfTotalTotal, BigDecimal selfTotalBurnRate, BigDecimal selfTotalSteamRate, BigDecimal matOilU1, BigDecimal matOilU2, BigDecimal matOilUnit, BigDecimal matOilCkQty, BigDecimal matOilCkUnit, BigDecimal matChemUQty, BigDecimal matChemUUnit, BigDecimal matChemSQty, BigDecimal matChemSUnit, BigDecimal matMiscAQty, BigDecimal matMiscAUnit, BigDecimal matMiscKQty, BigDecimal matMiscKUnit, Long updatedBy) {
        this.opBurnBurn1 = opBurnBurn1;
        this.opBurnBurn2 = opBurnBurn2;
        this.opBurnRate1 = opBurnRate1;
        this.opBurnRate2 = opBurnRate2;
        this.opCostUnit = opCostUnit;
        this.opCostDeduct = opCostDeduct;
        this.selfSrfInQty = selfSrfInQty;
        this.selfSrfInRev = selfSrfInRev;
        this.selfSrfFloorRecycle = selfSrfFloorRecycle;
        this.selfSrfFloorBury = selfSrfFloorBury;
        this.selfSrfFlySolid = selfSrfFlySolid;
        this.selfSrfFlyBury = selfSrfFlyBury;
        this.selfSrfCostFloor = selfSrfCostFloor;
        this.selfSrfCostFly = selfSrfCostFly;
        this.selfSrfCostMisc = selfSrfCostMisc;
        this.selfUtilP1 = selfUtilP1;
        this.selfUtilP2 = selfUtilP2;
        this.selfUtilPrate = selfUtilPrate;
        this.selfUtilNaohQty = selfUtilNaohQty;
        this.selfUtilNaohUnit = selfUtilNaohUnit;
        this.selfUtilWater = selfUtilWater;
        this.selfUtilWasteCharge = selfUtilWasteCharge;
        this.selfUtilEtc = selfUtilEtc;
        this.selfTotalTotal = selfTotalTotal;
        this.selfTotalBurnRate = selfTotalBurnRate;
        this.selfTotalSteamRate = selfTotalSteamRate;
        this.matOilU1 = matOilU1;
        this.matOilU2 = matOilU2;
        this.matOilUnit = matOilUnit;
        this.matOilCkQty = matOilCkQty;
        this.matOilCkUnit = matOilCkUnit;
        this.matChemUQty = matChemUQty;
        this.matChemUUnit = matChemUUnit;
        this.matChemSQty = matChemSQty;
        this.matChemSUnit = matChemSUnit;
        this.matMiscAQty = matMiscAQty;
        this.matMiscAUnit = matMiscAUnit;
        this.matMiscKQty = matMiscKQty;
        this.matMiscKUnit = matMiscKUnit;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
