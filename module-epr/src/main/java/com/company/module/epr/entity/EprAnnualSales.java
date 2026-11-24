package com.company.module.epr.entity;

import com.company.module.epr.dto.SalesCalculationResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 연도별 판매 실적 집계 — 신고 연도당 1건. 같은 연도를 다시 올리면 이 행을 고치고 상세는 새로 쓴다.
 */
@Entity
@Table(name = "epr_annual_sales")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EprAnnualSales extends EprBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ANNUAL_SALES_ID")
    private Long annualSalesId;

    @Column(name = "REPORT_YEAR", nullable = false)
    private Integer reportYear;

    @Column(name = "ACCEPTED_ROWS", nullable = false)
    private Integer acceptedRows;

    @Column(name = "UNIQUE_SKU_COUNT", nullable = false)
    private Integer uniqueSkuCount;

    @Column(name = "UNREGISTERED_SKU_COUNT", nullable = false)
    private Integer unregisteredSkuCount;

    @Column(name = "MISSING_WEIGHT_SKU_COUNT", nullable = false)
    private Integer missingWeightSkuCount;

    @Column(name = "TOTAL_SALES_EA", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalSalesEa;

    @Column(name = "TOTAL_REPORT_WEIGHT_KG", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalReportWeightKg;

    @Column(name = "CALCULATED_AT", nullable = false)
    private LocalDateTime calculatedAt;

    public static EprAnnualSales create(int reportYear, SalesCalculationResult result, Long createdBy) {
        EprAnnualSales annual = new EprAnnualSales();
        annual.reportYear = reportYear;
        annual.applyResult(result);
        annual.markCreatedBy(createdBy);
        return annual;
    }

    public void update(SalesCalculationResult result, Long updatedBy) {
        applyResult(result);
        markUpdatedBy(updatedBy);
    }

    public void delete(Long deletedBy) {
        markDeleted(deletedBy);
    }

    public void restore(SalesCalculationResult result, Long updatedBy) {
        applyResult(result);
        markRestored(updatedBy);
    }

    private void applyResult(SalesCalculationResult result) {
        this.acceptedRows = result.acceptedRows();
        this.uniqueSkuCount = result.uniqueSkuCount();
        this.unregisteredSkuCount = result.unregisteredSkuCount();
        this.missingWeightSkuCount = result.missingWeightSkuCount();
        this.totalSalesEa = result.totalSalesEa();
        this.totalReportWeightKg = result.grandTotalWeightKg();
        this.calculatedAt = result.calculatedAt();
    }
}
