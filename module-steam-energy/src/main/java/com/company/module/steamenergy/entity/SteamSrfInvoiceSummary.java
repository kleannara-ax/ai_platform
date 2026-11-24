package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SRF 인보이스 Summary — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: SRF Boiler invoice › Summary(VK) (srf-boiler-invoice.html)
 * <p>Table: steam_srf_invoice_summary
 * <p>PK: INVOICE_SUMMARY_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_srf_invoice_summary")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INVOICE_SUMMARY_ID")
    private Long invoiceSummaryId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "BSC_RATE", precision = 18, scale = 6)
    private BigDecimal bscRate;

    @Column(name = "BSC_FACTOR", precision = 18, scale = 6)
    private BigDecimal bscFactor;

    @Column(name = "AFBSCR_BSC", precision = 18, scale = 6)
    private BigDecimal afbscrBsc;

    @Column(name = "BSC_STEAM_AMOUNT", precision = 18, scale = 6)
    private BigDecimal bscSteamAmount;

    @Column(name = "ASC_RATE", precision = 18, scale = 6)
    private BigDecimal ascRate;

    @Column(name = "ASC_FACTOR", precision = 18, scale = 6)
    private BigDecimal ascFactor;

    @Column(name = "ASC_STEAM_AMOUNT", precision = 18, scale = 6)
    private BigDecimal ascSteamAmount;

    @Column(name = "OTHERS_0", precision = 18, scale = 6)
    private BigDecimal others0;

    @Column(name = "OTHERS_1", precision = 18, scale = 6)
    private BigDecimal others1;

    @Column(name = "OTHERS_2", precision = 18, scale = 6)
    private BigDecimal others2;

    @Column(name = "OTHERS_3", precision = 18, scale = 6)
    private BigDecimal others3;

    @Column(name = "PAYABLE_ADJUST", precision = 18, scale = 6)
    private BigDecimal payableAdjust;

    @Column(name = "LIQUIDATED_DAMAGE", precision = 18, scale = 6)
    private BigDecimal liquidatedDamage;

    @Column(name = "ASC_REFUND_ANNUAL", precision = 18, scale = 6)
    private BigDecimal ascRefundAnnual;

    @Column(name = "UTILITIES_DEDUCTION", precision = 18, scale = 6)
    private BigDecimal utilitiesDeduction;

    @Column(name = "OTHER_DEDUCTION_1", precision = 18, scale = 6)
    private BigDecimal otherDeduction1;

    @Column(name = "OTHER_DEDUCTION_2", precision = 18, scale = 6)
    private BigDecimal otherDeduction2;

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
    public void update(BigDecimal bscRate, BigDecimal bscFactor, BigDecimal afbscrBsc, BigDecimal bscSteamAmount, BigDecimal ascRate, BigDecimal ascFactor, BigDecimal ascSteamAmount, BigDecimal others0, BigDecimal others1, BigDecimal others2, BigDecimal others3, BigDecimal payableAdjust, BigDecimal liquidatedDamage, BigDecimal ascRefundAnnual, BigDecimal utilitiesDeduction, BigDecimal otherDeduction1, BigDecimal otherDeduction2, Long updatedBy) {
        this.bscRate = bscRate;
        this.bscFactor = bscFactor;
        this.afbscrBsc = afbscrBsc;
        this.bscSteamAmount = bscSteamAmount;
        this.ascRate = ascRate;
        this.ascFactor = ascFactor;
        this.ascSteamAmount = ascSteamAmount;
        this.others0 = others0;
        this.others1 = others1;
        this.others2 = others2;
        this.others3 = others3;
        this.payableAdjust = payableAdjust;
        this.liquidatedDamage = liquidatedDamage;
        this.ascRefundAnnual = ascRefundAnnual;
        this.utilitiesDeduction = utilitiesDeduction;
        this.otherDeduction1 = otherDeduction1;
        this.otherDeduction2 = otherDeduction2;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
