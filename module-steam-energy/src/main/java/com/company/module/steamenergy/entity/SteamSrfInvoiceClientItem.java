package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SRF 인보이스 Client Invoice — 연/월별 · 구분 (utilities: 유틸리티 10줄 / other-out: 깨끗한나라→베올리아 3줄 / other-in: 깨끗한나라←베올리아 3줄)·줄 번호 (0부터)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: SRF Boiler invoice › Client Invoice (srf-boiler-invoice-client.html)
 * <p>Table: steam_srf_invoice_client_item
 * <p>PK: CLIENT_ITEM_ID, UNIQUE: (YEAR_NO, MONTH_NO, SECTION, LINE_NO)
 */
@Entity
@Table(name = "steam_srf_invoice_client_item")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceClientItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CLIENT_ITEM_ID")
    private Long clientItemId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "SECTION", length = 20, nullable = false)
    private String section;

    @Column(name = "LINE_NO", nullable = false)
    private Integer lineNo;

    @Column(name = "ITEM_LABEL", length = 200)
    private String itemLabel;

    @Column(name = "UNIT_LABEL", length = 50)
    private String unitLabel;

    @Column(name = "QTY", precision = 18, scale = 6)
    private BigDecimal qty;

    @Column(name = "UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "NOTE", length = 500)
    private String note;

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
    public void update(String itemLabel, String unitLabel, BigDecimal qty, BigDecimal unitPrice, String note, Long updatedBy) {
        this.itemLabel = itemLabel;
        this.unitLabel = unitLabel;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.note = note;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, updatedBy);
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
