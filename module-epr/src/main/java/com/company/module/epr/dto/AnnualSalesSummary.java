package com.company.module.epr.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 연도별 판매·신고량 비교 한 줄 (전년 자료가 없으면 증감은 null) */
public record AnnualSalesSummary(Integer reportYear,
        Integer acceptedRows,
        Integer uniqueSkuCount,
        Integer unregisteredSkuCount,
        Integer missingWeightSkuCount,
        BigDecimal totalSalesEa,
        BigDecimal totalReportWeightKg,
        BigDecimal salesChangeEa,
        BigDecimal reportWeightChangeKg,
        LocalDateTime calculatedAt) {
}
