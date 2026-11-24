package com.company.module.epr.dto;

import java.math.BigDecimal;

/** 파트별 신고중량 집계 */
public record PartSummary(String partPrefix,
        String partName,
        long skuCount,
        BigDecimal totalSalesEa,
        BigDecimal totalConvertedBag,
        BigDecimal totalBagReportWeightKg,
        BigDecimal totalConvertedBox,
        BigDecimal totalBoxReportWeightKg,
        BigDecimal totalReportWeightKg) {
}
