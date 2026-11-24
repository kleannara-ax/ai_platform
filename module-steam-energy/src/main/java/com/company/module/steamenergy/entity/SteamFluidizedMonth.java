package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 월별 계획·비용 — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 운영실적(사업계획대비·실적비교·도급비용 지급현황), 월별 운영내역 (unit-fluidized-summary.html, unit-fluidized-monthly.html)
 * <p>Table: steam_fluidized_month
 * <p>PK: FLUIDIZED_MONTH_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_fluidized_month")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FLUIDIZED_MONTH_ID")
    private Long fluidizedMonthId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "PLAN_OPERATING", precision = 18, scale = 6)
    private BigDecimal planOperating;

    @Column(name = "PLAN_POWER", precision = 18, scale = 6)
    private BigDecimal planPower;

    @Column(name = "PLAN_WASTE", precision = 18, scale = 6)
    private BigDecimal planWaste;

    @Column(name = "PLAN_SRF_INCOME", precision = 18, scale = 6)
    private BigDecimal planSrfIncome;

    @Column(name = "SUMMARY_REMARK", length = 1000)
    private String summaryRemark;

    @Column(name = "MONTH_NOTE", length = 1000)
    private String monthNote;

    @Column(name = "LABOR_COST", precision = 18, scale = 6)
    private BigDecimal laborCost;

    @Column(name = "OPERATING_PAY_COST", precision = 18, scale = 6)
    private BigDecimal operatingPayCost;

    @Column(name = "IMPROVEMENT_PAY_COST", precision = 18, scale = 6)
    private BigDecimal improvementPayCost;

    @Column(name = "IMPROVEMENT_COST", precision = 18, scale = 6)
    private BigDecimal improvementCost;

    @Column(name = "DEDUCTION_COST", precision = 18, scale = 6)
    private BigDecimal deductionCost;

    @Column(name = "EXTRA_PAYMENT", precision = 18, scale = 6)
    private BigDecimal extraPayment;

    @Column(name = "WASTEWATER_QTY", precision = 18, scale = 6)
    private BigDecimal wastewaterQty;

    @Column(name = "SOIL_QTY", precision = 18, scale = 6)
    private BigDecimal soilQty;

    @Column(name = "SOIL_COST", precision = 18, scale = 6)
    private BigDecimal soilCost;

    @Column(name = "CONTRACT_COST", precision = 18, scale = 6)
    private BigDecimal contractCost;

    @Column(name = "OPERATING_COST", precision = 18, scale = 6)
    private BigDecimal operatingCost;

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
    public void update(BigDecimal planOperating, BigDecimal planPower, BigDecimal planWaste, BigDecimal planSrfIncome, String summaryRemark, String monthNote, BigDecimal laborCost, BigDecimal operatingPayCost, BigDecimal improvementPayCost, BigDecimal improvementCost, BigDecimal deductionCost, BigDecimal extraPayment, BigDecimal wastewaterQty, BigDecimal soilQty, BigDecimal soilCost, BigDecimal contractCost, BigDecimal operatingCost, Long updatedBy) {
        this.planOperating = planOperating;
        this.planPower = planPower;
        this.planWaste = planWaste;
        this.planSrfIncome = planSrfIncome;
        this.summaryRemark = summaryRemark;
        this.monthNote = monthNote;
        this.laborCost = laborCost;
        this.operatingPayCost = operatingPayCost;
        this.improvementPayCost = improvementPayCost;
        this.improvementCost = improvementCost;
        this.deductionCost = deductionCost;
        this.extraPayment = extraPayment;
        this.wastewaterQty = wastewaterQty;
        this.soilQty = soilQty;
        this.soilCost = soilCost;
        this.contractCost = contractCost;
        this.operatingCost = operatingCost;
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
