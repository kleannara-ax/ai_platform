package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SRF 인보이스 월별 운영내역 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: SRF Boiler invoice › 월별 운영내역 (srf-boiler-invoice-monthly.html)
 * <p>Table: steam_srf_invoice_monthly
 * <p>PK: INVOICE_MONTHLY_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_srf_invoice_monthly")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INVOICE_MONTHLY_ID")
    private Long invoiceMonthlyId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "STEAM_QTY", precision = 18, scale = 6)
    private BigDecimal steamQty;

    @Column(name = "STEAM_UNIT", precision = 18, scale = 6)
    private BigDecimal steamUnit;

    @Column(name = "POWER_QTY", precision = 18, scale = 6)
    private BigDecimal powerQty;

    @Column(name = "POWER_UNIT", precision = 18, scale = 6)
    private BigDecimal powerUnit;

    @Column(name = "LNG_QTY", precision = 18, scale = 6)
    private BigDecimal lngQty;

    @Column(name = "LNG_UNIT", precision = 18, scale = 6)
    private BigDecimal lngUnit;

    @Column(name = "PROCESS_QTY", precision = 18, scale = 6)
    private BigDecimal processQty;

    @Column(name = "PROCESS_UNIT", precision = 18, scale = 6)
    private BigDecimal processUnit;

    @Column(name = "SOFT_QTY", precision = 18, scale = 6)
    private BigDecimal softQty;

    @Column(name = "SOFT_UNIT", precision = 18, scale = 6)
    private BigDecimal softUnit;

    @Column(name = "SOFTEN_QTY", precision = 18, scale = 6)
    private BigDecimal softenQty;

    @Column(name = "SOFTEN_UNIT", precision = 18, scale = 6)
    private BigDecimal softenUnit;

    @Column(name = "LIFE_QTY", precision = 18, scale = 6)
    private BigDecimal lifeQty;

    @Column(name = "LIFE_UNIT", precision = 18, scale = 6)
    private BigDecimal lifeUnit;

    @Column(name = "ETC_SPECIAL", precision = 18, scale = 6)
    private BigDecimal etcSpecial;

    @Column(name = "ETC_BSC7", precision = 18, scale = 6)
    private BigDecimal etcBsc7;

    @Column(name = "ETC_SRF_SHARE", precision = 18, scale = 6)
    private BigDecimal etcSrfShare;

    @Column(name = "ETC_INSPECT", precision = 18, scale = 6)
    private BigDecimal etcInspect;

    @Column(name = "ETC_OVER7", precision = 18, scale = 6)
    private BigDecimal etcOver7;

    @Column(name = "ETC_AIR_OVER", precision = 18, scale = 6)
    private BigDecimal etcAirOver;

    @Column(name = "ETC_WASTE", precision = 18, scale = 6)
    private BigDecimal etcWaste;

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
    public void update(BigDecimal steamQty, BigDecimal steamUnit, BigDecimal powerQty, BigDecimal powerUnit, BigDecimal lngQty, BigDecimal lngUnit, BigDecimal processQty, BigDecimal processUnit, BigDecimal softQty, BigDecimal softUnit, BigDecimal softenQty, BigDecimal softenUnit, BigDecimal lifeQty, BigDecimal lifeUnit, BigDecimal etcSpecial, BigDecimal etcBsc7, BigDecimal etcSrfShare, BigDecimal etcInspect, BigDecimal etcOver7, BigDecimal etcAirOver, BigDecimal etcWaste, Long updatedBy) {
        this.steamQty = steamQty;
        this.steamUnit = steamUnit;
        this.powerQty = powerQty;
        this.powerUnit = powerUnit;
        this.lngQty = lngQty;
        this.lngUnit = lngUnit;
        this.processQty = processQty;
        this.processUnit = processUnit;
        this.softQty = softQty;
        this.softUnit = softUnit;
        this.softenQty = softenQty;
        this.softenUnit = softenUnit;
        this.lifeQty = lifeQty;
        this.lifeUnit = lifeUnit;
        this.etcSpecial = etcSpecial;
        this.etcBsc7 = etcBsc7;
        this.etcSrfShare = etcSrfShare;
        this.etcInspect = etcInspect;
        this.etcOver7 = etcOver7;
        this.etcAirOver = etcAirOver;
        this.etcWaste = etcWaste;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
