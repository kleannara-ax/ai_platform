package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 소각로 도급비용 — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 운영실적 › 소각로 도급비용 (unit-fluidized-summary.html)
 * <p>Table: steam_fluidized_contract_daily
 * <p>PK: FLUIDIZED_CONTRACT_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_fluidized_contract_daily")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedContractDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FLUIDIZED_CONTRACT_ID")
    private Long fluidizedContractId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "STEAM_TON", precision = 18, scale = 6)
    private BigDecimal steamTon;

    @Column(name = "STEAM_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal steamUnitPrice;

    @Column(name = "STOP_HOURS", precision = 18, scale = 6)
    private BigDecimal stopHours;

    @Column(name = "FIXED_COST_PER_HOUR", precision = 18, scale = 6)
    private BigDecimal fixedCostPerHour;

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
    public void update(BigDecimal steamTon, BigDecimal steamUnitPrice, BigDecimal stopHours, BigDecimal fixedCostPerHour, Long updatedBy) {
        this.steamTon = steamTon;
        this.steamUnitPrice = steamUnitPrice;
        this.stopHours = stopHours;
        this.fixedCostPerHour = fixedCostPerHour;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, updatedBy);
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
