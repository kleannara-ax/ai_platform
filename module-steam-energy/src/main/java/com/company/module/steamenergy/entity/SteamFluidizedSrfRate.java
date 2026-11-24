package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 SRF 처리단가 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › SRF(비성형) 입고내역 › 처리단가 (unit-fluidized-srf-inbound.html)
 * <p>Table: steam_fluidized_srf_rate
 * <p>PK: SRF_RATE_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_fluidized_srf_rate")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedSrfRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SRF_RATE_ID")
    private Long srfRateId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "VENDOR01_RATE", precision = 18, scale = 6)
    private BigDecimal vendor01Rate;

    @Column(name = "VENDOR02_RATE", precision = 18, scale = 6)
    private BigDecimal vendor02Rate;

    @Column(name = "VENDOR03_RATE", precision = 18, scale = 6)
    private BigDecimal vendor03Rate;

    @Column(name = "VENDOR04_RATE", precision = 18, scale = 6)
    private BigDecimal vendor04Rate;

    @Column(name = "VENDOR05_RATE", precision = 18, scale = 6)
    private BigDecimal vendor05Rate;

    @Column(name = "VENDOR06_RATE", precision = 18, scale = 6)
    private BigDecimal vendor06Rate;

    @Column(name = "VENDOR07_RATE", precision = 18, scale = 6)
    private BigDecimal vendor07Rate;

    @Column(name = "VENDOR08_RATE", precision = 18, scale = 6)
    private BigDecimal vendor08Rate;

    @Column(name = "VENDOR09_RATE", precision = 18, scale = 6)
    private BigDecimal vendor09Rate;

    @Column(name = "VENDOR10_RATE", precision = 18, scale = 6)
    private BigDecimal vendor10Rate;

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
    public void update(BigDecimal vendor01Rate, BigDecimal vendor02Rate, BigDecimal vendor03Rate, BigDecimal vendor04Rate, BigDecimal vendor05Rate, BigDecimal vendor06Rate, BigDecimal vendor07Rate, BigDecimal vendor08Rate, BigDecimal vendor09Rate, BigDecimal vendor10Rate, Long updatedBy) {
        this.vendor01Rate = vendor01Rate;
        this.vendor02Rate = vendor02Rate;
        this.vendor03Rate = vendor03Rate;
        this.vendor04Rate = vendor04Rate;
        this.vendor05Rate = vendor05Rate;
        this.vendor06Rate = vendor06Rate;
        this.vendor07Rate = vendor07Rate;
        this.vendor08Rate = vendor08Rate;
        this.vendor09Rate = vendor09Rate;
        this.vendor10Rate = vendor10Rate;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, updatedBy);
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
