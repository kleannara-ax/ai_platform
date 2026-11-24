package com.company.module.epr.dto;

import java.math.BigDecimal;

/** 포장중량 엑셀 한 행 */
public record WeightExcelRow(int rowNumber, String productCode, String productName,
        BigDecimal bagWeight, BigDecimal boxWeight) {
}
