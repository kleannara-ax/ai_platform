package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 연료비 배분비율(호기별) — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 연료비 배분비율 › 호기별 연료비 배분 (energy-fuel-ratio.html t04~t06)
 * <p>Table: steam_energy_ratio_machine
 * <p>PK: RATIO_MACHINE_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_ratio_machine")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioMachine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RATIO_MACHINE_ID")
    private Long ratioMachineId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "FUEL_PM2", precision = 18, scale = 6)
    private BigDecimal fuelPm2;

    @Column(name = "FUEL_PM3", precision = 18, scale = 6)
    private BigDecimal fuelPm3;

    @Column(name = "FUEL_PAPER_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal fuelPaperSubtotal;

    @Column(name = "FUEL_TM3", precision = 18, scale = 6)
    private BigDecimal fuelTm3;

    @Column(name = "FUEL_TM4", precision = 18, scale = 6)
    private BigDecimal fuelTm4;

    @Column(name = "FUEL_TM5", precision = 18, scale = 6)
    private BigDecimal fuelTm5;

    @Column(name = "FUEL_TISSUE_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal fuelTissueSubtotal;

    @Column(name = "FUEL_STEAM_PROD_TON", precision = 18, scale = 6)
    private BigDecimal fuelSteamProdTon;

    @Column(name = "USAGE_PM2", precision = 18, scale = 6)
    private BigDecimal usagePm2;

    @Column(name = "USAGE_PM3", precision = 18, scale = 6)
    private BigDecimal usagePm3;

    @Column(name = "USAGE_TM3", precision = 18, scale = 6)
    private BigDecimal usageTm3;

    @Column(name = "USAGE_TM4", precision = 18, scale = 6)
    private BigDecimal usageTm4;

    @Column(name = "USAGE_TM5", precision = 18, scale = 6)
    private BigDecimal usageTm5;

    @Column(name = "USAGE_TOTAL_PROD_STEAM", precision = 18, scale = 6)
    private BigDecimal usageTotalProdSteam;

    @Column(name = "USAGE_ETC", precision = 18, scale = 6)
    private BigDecimal usageEtc;

    @Column(name = "PROD_PM2_KG", precision = 18, scale = 6)
    private BigDecimal prodPm2Kg;

    @Column(name = "PROD_PM3_KG", precision = 18, scale = 6)
    private BigDecimal prodPm3Kg;

    @Column(name = "PROD_PAPER_SUBTOTAL_KG", precision = 18, scale = 6)
    private BigDecimal prodPaperSubtotalKg;

    @Column(name = "PROD_TM3_KG", precision = 18, scale = 6)
    private BigDecimal prodTm3Kg;

    @Column(name = "PROD_TM4_KG", precision = 18, scale = 6)
    private BigDecimal prodTm4Kg;

    @Column(name = "PROD_TM5_KG", precision = 18, scale = 6)
    private BigDecimal prodTm5Kg;

    @Column(name = "PROD_TISSUE_SUBTOTAL_KG", precision = 18, scale = 6)
    private BigDecimal prodTissueSubtotalKg;

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
    public void update(BigDecimal fuelPm2, BigDecimal fuelPm3, BigDecimal fuelPaperSubtotal, BigDecimal fuelTm3, BigDecimal fuelTm4, BigDecimal fuelTm5, BigDecimal fuelTissueSubtotal, BigDecimal fuelSteamProdTon, BigDecimal usagePm2, BigDecimal usagePm3, BigDecimal usageTm3, BigDecimal usageTm4, BigDecimal usageTm5, BigDecimal usageTotalProdSteam, BigDecimal usageEtc, BigDecimal prodPm2Kg, BigDecimal prodPm3Kg, BigDecimal prodPaperSubtotalKg, BigDecimal prodTm3Kg, BigDecimal prodTm4Kg, BigDecimal prodTm5Kg, BigDecimal prodTissueSubtotalKg, Long updatedBy) {
        this.fuelPm2 = fuelPm2;
        this.fuelPm3 = fuelPm3;
        this.fuelPaperSubtotal = fuelPaperSubtotal;
        this.fuelTm3 = fuelTm3;
        this.fuelTm4 = fuelTm4;
        this.fuelTm5 = fuelTm5;
        this.fuelTissueSubtotal = fuelTissueSubtotal;
        this.fuelSteamProdTon = fuelSteamProdTon;
        this.usagePm2 = usagePm2;
        this.usagePm3 = usagePm3;
        this.usageTm3 = usageTm3;
        this.usageTm4 = usageTm4;
        this.usageTm5 = usageTm5;
        this.usageTotalProdSteam = usageTotalProdSteam;
        this.usageEtc = usageEtc;
        this.prodPm2Kg = prodPm2Kg;
        this.prodPm3Kg = prodPm3Kg;
        this.prodPaperSubtotalKg = prodPaperSubtotalKg;
        this.prodTm3Kg = prodTm3Kg;
        this.prodTm4Kg = prodTm4Kg;
        this.prodTm5Kg = prodTm5Kg;
        this.prodTissueSubtotalKg = prodTissueSubtotalKg;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
