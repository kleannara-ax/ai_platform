package com.company.module.epr.dto;

import java.math.BigDecimal;

/** 판매수량 엑셀 한 행 */
public record SalesExcelRow(int rowNumber, String productCode, String productName, BigDecimal salesEa) {
}
