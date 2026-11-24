package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SRF 인보이스 BSC 단가 — 연도별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: SRF Boiler invoice › BSC (srf-boiler-invoice-bsc.html)
 * <p>Table: steam_srf_invoice_bsc
 * <p>PK: INVOICE_BSC_ID, UNIQUE: (YEAR_NO)
 */
@Entity
@Table(name = "steam_srf_invoice_bsc")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceBsc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INVOICE_BSC_ID")
    private Long invoiceBscId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "BSC_RATE", precision = 18, scale = 6)
    private BigDecimal bscRate;

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
    public void update(BigDecimal bscRate, Long updatedBy) {
        this.bscRate = bscRate;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, updatedBy);
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
