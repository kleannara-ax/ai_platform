package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SRF 인보이스 Adjustment Factor — 연도별 · 표 구분 (afbsc / afbsc-rebase / afasc)·항목 (a, b, c, d, e, e-bottom, e-fly, f, g, h)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: SRF Boiler invoice › Adjustment Factor (srf-boiler-invoice-adjustment.html)
 * <p>Table: steam_srf_invoice_adjustment
 * <p>PK: INVOICE_ADJUSTMENT_ID, UNIQUE: (YEAR_NO, SECTION, ITEM_CODE)
 */
@Entity
@Table(name = "steam_srf_invoice_adjustment")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INVOICE_ADJUSTMENT_ID")
    private Long invoiceAdjustmentId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "SECTION", length = 20, nullable = false)
    private String section;

    @Column(name = "ITEM_CODE", length = 20, nullable = false)
    private String itemCode;

    @Column(name = "COEFFICIENT", precision = 18, scale = 6)
    private BigDecimal coefficient;

    @Column(name = "BASE_VALUE", precision = 18, scale = 6)
    private BigDecimal baseValue;

    @Column(name = "PRESENT_VALUE", precision = 18, scale = 6)
    private BigDecimal presentValue;

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
    public void update(BigDecimal coefficient, BigDecimal baseValue, BigDecimal presentValue, Long updatedBy) {
        this.coefficient = coefficient;
        this.baseValue = baseValue;
        this.presentValue = presentValue;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, updatedBy);
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
