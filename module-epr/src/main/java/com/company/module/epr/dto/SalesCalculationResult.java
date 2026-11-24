package com.company.module.epr.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 판매수량 업로드 계산 결과 · 저장된 연도 조회 결과 */
public record SalesCalculationResult(Integer reportYear,
        LocalDateTime calculatedAt,
        int acceptedRows,
        BigDecimal totalSalesEa,
        int uniqueSkuCount,
        int unregisteredSkuCount,
        int missingWeightSkuCount,
        BigDecimal grandTotalWeightKg,
        List<PartSummary> partSummaries,
        List<SkuCalculation> skuDetails,
        List<UnregisteredSale> unregisteredSales,
        List<UploadIssue> issues) {
}
