package com.company.module.epr.dto;

import java.math.BigDecimal;

/** SKU 한 건의 신고중량 계산 내역 */
public record SkuCalculation(String partPrefix,
        String partName,
        String productCode,
        String masterProductName,
        String companyType,
        String uploadedProductName,
        BigDecimal salesEa,
        Integer eaPerBag,
        BigDecimal bagWeightG,
        BigDecimal convertedBag,
        BigDecimal rawBagReportWeightG,
        BigDecimal bagReportWeightKg,
        Integer eaPerBox,
        BigDecimal boxWeightG,
        BigDecimal convertedBox,
        BigDecimal rawBoxReportWeightG,
        BigDecimal boxReportWeightKg,
        BigDecimal totalReportWeightKg,
        String status,
        int sourceRows) {
}
