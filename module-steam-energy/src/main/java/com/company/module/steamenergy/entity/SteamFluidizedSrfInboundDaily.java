package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 SRF 입고내역 — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › SRF(비성형) 입고내역 (unit-fluidized-srf-inbound.html)
 * <p>Table: steam_fluidized_srf_inbound_daily
 * <p>PK: SRF_INBOUND_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_fluidized_srf_inbound_daily")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedSrfInboundDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SRF_INBOUND_ID")
    private Long srfInboundId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "VENDOR01_KG", precision = 18, scale = 6)
    private BigDecimal vendor01Kg;

    @Column(name = "VENDOR02_KG", precision = 18, scale = 6)
    private BigDecimal vendor02Kg;

    @Column(name = "VENDOR03_KG", precision = 18, scale = 6)
    private BigDecimal vendor03Kg;

    @Column(name = "VENDOR04_KG", precision = 18, scale = 6)
    private BigDecimal vendor04Kg;

    @Column(name = "VENDOR05_KG", precision = 18, scale = 6)
    private BigDecimal vendor05Kg;

    @Column(name = "VENDOR06_KG", precision = 18, scale = 6)
    private BigDecimal vendor06Kg;

    @Column(name = "VENDOR07_KG", precision = 18, scale = 6)
    private BigDecimal vendor07Kg;

    @Column(name = "VENDOR08_KG", precision = 18, scale = 6)
    private BigDecimal vendor08Kg;

    @Column(name = "VENDOR09_KG", precision = 18, scale = 6)
    private BigDecimal vendor09Kg;

    @Column(name = "VENDOR10_KG", precision = 18, scale = 6)
    private BigDecimal vendor10Kg;

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
    public void update(BigDecimal vendor01Kg, BigDecimal vendor02Kg, BigDecimal vendor03Kg, BigDecimal vendor04Kg, BigDecimal vendor05Kg, BigDecimal vendor06Kg, BigDecimal vendor07Kg, BigDecimal vendor08Kg, BigDecimal vendor09Kg, BigDecimal vendor10Kg, Long updatedBy) {
        this.vendor01Kg = vendor01Kg;
        this.vendor02Kg = vendor02Kg;
        this.vendor03Kg = vendor03Kg;
        this.vendor04Kg = vendor04Kg;
        this.vendor05Kg = vendor05Kg;
        this.vendor06Kg = vendor06Kg;
        this.vendor07Kg = vendor07Kg;
        this.vendor08Kg = vendor08Kg;
        this.vendor09Kg = vendor09Kg;
        this.vendor10Kg = vendor10Kg;
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
