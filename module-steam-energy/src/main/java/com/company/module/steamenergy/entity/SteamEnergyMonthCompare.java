package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 회계비용 마감비교 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 월별 회계비용 마감 vs 실마감 차이 (energy-month-compare.html)
 * <p>Table: steam_energy_month_compare
 * <p>PK: MONTH_COMPARE_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_month_compare")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyMonthCompare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MONTH_COMPARE_ID")
    private Long monthCompareId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "ZESCO_STEAM_BUY", precision = 18, scale = 6)
    private BigDecimal zescoSteamBuy;

    @Column(name = "LNG_BUY", precision = 18, scale = 6)
    private BigDecimal lngBuy;

    @Column(name = "ZESCO_OFFSET", precision = 18, scale = 6)
    private BigDecimal zescoOffset;

    @Column(name = "ZESCO_PENALTY", precision = 18, scale = 6)
    private BigDecimal zescoPenalty;

    @Column(name = "FUEL_PLAN", precision = 18, scale = 6)
    private BigDecimal fuelPlan;

    @Column(name = "INCINERATOR_STEAM_BUY", precision = 18, scale = 6)
    private BigDecimal incineratorSteamBuy;

    @Column(name = "FLUID_STEAM_BUY", precision = 18, scale = 6)
    private BigDecimal fluidSteamBuy;

    @Column(name = "FUEL_ACTUAL", precision = 18, scale = 6)
    private BigDecimal fuelActual;

    @Column(name = "FUEL_STEAM_UNIT", precision = 18, scale = 6)
    private BigDecimal fuelSteamUnit;

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
    public void update(BigDecimal zescoSteamBuy, BigDecimal lngBuy, BigDecimal zescoOffset, BigDecimal zescoPenalty, BigDecimal fuelPlan, BigDecimal incineratorSteamBuy, BigDecimal fluidSteamBuy, BigDecimal fuelActual, BigDecimal fuelSteamUnit, Long updatedBy) {
        this.zescoSteamBuy = zescoSteamBuy;
        this.lngBuy = lngBuy;
        this.zescoOffset = zescoOffset;
        this.zescoPenalty = zescoPenalty;
        this.fuelPlan = fuelPlan;
        this.incineratorSteamBuy = incineratorSteamBuy;
        this.fluidSteamBuy = fluidSteamBuy;
        this.fuelActual = fuelActual;
        this.fuelSteamUnit = fuelSteamUnit;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, updatedBy);
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
