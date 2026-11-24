package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 소각로 계획대비 가동실적 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 폐합성수지 소각로 › 계획대비 가동실적 (incinerator-operation.html)
 * <p>Table: steam_incinerator_operation_monthly
 * <p>PK: OPERATION_MONTHLY_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_incinerator_operation_monthly")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorOperationMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OPERATION_MONTHLY_ID")
    private Long operationMonthlyId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "U1_PLAN", precision = 18, scale = 6)
    private BigDecimal u1Plan;

    @Column(name = "U1_ACTUAL", precision = 18, scale = 6)
    private BigDecimal u1Actual;

    @Column(name = "U2_PLAN", precision = 18, scale = 6)
    private BigDecimal u2Plan;

    @Column(name = "U2_ACTUAL", precision = 18, scale = 6)
    private BigDecimal u2Actual;

    @Column(name = "STEAM_PLAN", precision = 18, scale = 6)
    private BigDecimal steamPlan;

    @Column(name = "STEAM_ACTUAL", precision = 18, scale = 6)
    private BigDecimal steamActual;

    @Column(name = "RATE_PLAN", precision = 24, scale = 12)
    private BigDecimal ratePlan;

    @Column(name = "RATE_ACTUAL", precision = 24, scale = 12)
    private BigDecimal rateActual;

    @Column(name = "COST_PLAN", precision = 18, scale = 6)
    private BigDecimal costPlan;

    @Column(name = "COST_ACTUAL", precision = 18, scale = 6)
    private BigDecimal costActual;

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
    public void update(BigDecimal u1Plan, BigDecimal u1Actual, BigDecimal u2Plan, BigDecimal u2Actual, BigDecimal steamPlan, BigDecimal steamActual, BigDecimal ratePlan, BigDecimal rateActual, BigDecimal costPlan, BigDecimal costActual, Long updatedBy) {
        this.u1Plan = u1Plan;
        this.u1Actual = u1Actual;
        this.u2Plan = u2Plan;
        this.u2Actual = u2Actual;
        this.steamPlan = steamPlan;
        this.steamActual = steamActual;
        this.ratePlan = ratePlan;
        this.rateActual = rateActual;
        this.costPlan = costPlan;
        this.costActual = costActual;
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
