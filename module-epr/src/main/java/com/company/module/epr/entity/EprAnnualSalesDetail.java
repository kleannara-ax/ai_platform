package com.company.module.epr.entity;

import com.company.module.epr.dto.SkuCalculation;
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

/**
 * 연도별 판매 실적의 SKU 상세 (1:N). 같은 연도를 다시 올리면 기존 상세는 소프트 삭제하고 새로 쓴다.
 * 계산 당시의 마스터 값(입수량·포장중량)을 그대로 남겨 나중에 마스터가 바뀌어도 신고값이 유지된다.
 */
@Entity
@Table(name = "epr_annual_sales_detail")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EprAnnualSalesDetail extends EprBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ANNUAL_SALES_DETAIL_ID")
    private Long annualSalesDetailId;

    @Column(name = "ANNUAL_SALES_ID", nullable = false)
    private Long annualSalesId;

    @Column(name = "REPORT_YEAR", nullable = false)
    private Integer reportYear;

    @Column(name = "PRODUCT_CODE", nullable = false, length = 30)
    private String productCode;

    @Column(name = "PART_PREFIX", nullable = false, length = 10)
    private String partPrefix;

    @Column(name = "PART_NAME", nullable = false, length = 255)
    private String partName;

    @Column(name = "MASTER_PRODUCT_NAME", nullable = false, length = 255)
    private String masterProductName;

    @Column(name = "UPLOADED_PRODUCT_NAME", nullable = false, length = 255)
    private String uploadedProductName;

    @Column(name = "SALES_EA", nullable = false, precision = 18, scale = 4)
    private BigDecimal salesEa;

    @Column(name = "EA_PER_BAG", nullable = false)
    private Integer eaPerBag;

    @Column(name = "BAG_WEIGHT_G", nullable = false, precision = 15, scale = 4)
    private BigDecimal bagWeightG;

    @Column(name = "CONVERTED_BAG", nullable = false, precision = 18, scale = 6)
    private BigDecimal convertedBag;

    @Column(name = "BAG_REPORT_WEIGHT_KG", nullable = false, precision = 18, scale = 2)
    private BigDecimal bagReportWeightKg;

    @Column(name = "EA_PER_BOX", nullable = false)
    private Integer eaPerBox;

    @Column(name = "BOX_WEIGHT_G", nullable = false, precision = 15, scale = 4)
    private BigDecimal boxWeightG;

    @Column(name = "CONVERTED_BOX", nullable = false, precision = 18, scale = 6)
    private BigDecimal convertedBox;

    @Column(name = "BOX_REPORT_WEIGHT_KG", nullable = false, precision = 18, scale = 2)
    private BigDecimal boxReportWeightKg;

    @Column(name = "TOTAL_REPORT_WEIGHT_KG", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalReportWeightKg;

    @Column(name = "STATUS", nullable = false, length = 30)
    private String status;

    @Column(name = "SOURCE_ROWS", nullable = false)
    private Integer sourceRows;

    public static EprAnnualSalesDetail of(EprAnnualSales annual, SkuCalculation calculation, Long createdBy) {
        EprAnnualSalesDetail detail = new EprAnnualSalesDetail();
        detail.annualSalesId = annual.getAnnualSalesId();
        detail.reportYear = annual.getReportYear();
        detail.productCode = calculation.productCode();
        detail.partPrefix = calculation.partPrefix();
        detail.partName = calculation.partName();
        detail.masterProductName = calculation.masterProductName() == null ? "" : calculation.masterProductName();
        detail.uploadedProductName = calculation.uploadedProductName() == null ? "" : calculation.uploadedProductName();
        detail.salesEa = calculation.salesEa();
        detail.eaPerBag = calculation.eaPerBag();
        detail.bagWeightG = calculation.bagWeightG();
        detail.convertedBag = calculation.convertedBag();
        detail.bagReportWeightKg = calculation.bagReportWeightKg();
        detail.eaPerBox = calculation.eaPerBox();
        detail.boxWeightG = calculation.boxWeightG();
        detail.convertedBox = calculation.convertedBox();
        detail.boxReportWeightKg = calculation.boxReportWeightKg();
        detail.totalReportWeightKg = calculation.totalReportWeightKg();
        detail.status = calculation.status();
        detail.sourceRows = calculation.sourceRows();
        detail.markCreatedBy(createdBy);
        return detail;
    }

    public void delete(Long deletedBy) {
        markDeleted(deletedBy);
    }

    public void restore(Long updatedBy) {
        markRestored(updatedBy);
    }
}
