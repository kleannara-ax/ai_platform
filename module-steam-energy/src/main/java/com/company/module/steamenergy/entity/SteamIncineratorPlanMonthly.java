package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 소각로 사업계획대비 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 폐합성수지 소각로 › 사업계획대비 (incinerator-plan-vs.html), 도급내역의 월 인건비
 * <p>Table: steam_incinerator_plan_monthly
 * <p>PK: PLAN_MONTHLY_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_incinerator_plan_monthly")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorPlanMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PLAN_MONTHLY_ID")
    private Long planMonthlyId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "CONTRACT_LABOR", precision = 18, scale = 6)
    private BigDecimal contractLabor;

    @Column(name = "LABOR_PLAN", precision = 18, scale = 6)
    private BigDecimal laborPlan;

    @Column(name = "LABOR_ACTUAL", precision = 18, scale = 6)
    private BigDecimal laborActual;

    @Column(name = "OPERATION_PLAN", precision = 18, scale = 6)
    private BigDecimal operationPlan;

    @Column(name = "OPERATION_ACTUAL", precision = 18, scale = 6)
    private BigDecimal operationActual;

    @Column(name = "TOTAL_PLAN", precision = 18, scale = 6)
    private BigDecimal totalPlan;

    @Column(name = "NOTE_U1", length = 500)
    private String noteU1;

    @Column(name = "NOTE_U2", length = 500)
    private String noteU2;

    @Column(name = "BRK_MONTH_PLAN", precision = 18, scale = 6)
    private BigDecimal brkMonthPlan;

    @Column(name = "BRK_MONTH_ACTUAL", precision = 18, scale = 6)
    private BigDecimal brkMonthActual;

    @Column(name = "BRK_YTD_PLAN", precision = 18, scale = 6)
    private BigDecimal brkYtdPlan;

    @Column(name = "BRK_YTD_ACTUAL", precision = 18, scale = 6)
    private BigDecimal brkYtdActual;

    @Column(name = "BRK_PREV_ACTUAL", precision = 18, scale = 6)
    private BigDecimal brkPrevActual;

    @Column(name = "BRK_NOTE", length = 500)
    private String brkNote;

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
    public void update(BigDecimal contractLabor, BigDecimal laborPlan, BigDecimal laborActual, BigDecimal operationPlan, BigDecimal operationActual, BigDecimal totalPlan, String noteU1, String noteU2, BigDecimal brkMonthPlan, BigDecimal brkMonthActual, BigDecimal brkYtdPlan, BigDecimal brkYtdActual, BigDecimal brkPrevActual, String brkNote, Long updatedBy) {
        this.contractLabor = contractLabor;
        this.laborPlan = laborPlan;
        this.laborActual = laborActual;
        this.operationPlan = operationPlan;
        this.operationActual = operationActual;
        this.totalPlan = totalPlan;
        this.noteU1 = noteU1;
        this.noteU2 = noteU2;
        this.brkMonthPlan = brkMonthPlan;
        this.brkMonthActual = brkMonthActual;
        this.brkYtdPlan = brkYtdPlan;
        this.brkYtdActual = brkYtdActual;
        this.brkPrevActual = brkPrevActual;
        this.brkNote = brkNote;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
