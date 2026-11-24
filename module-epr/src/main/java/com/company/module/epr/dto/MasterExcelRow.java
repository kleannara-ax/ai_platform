package com.company.module.epr.dto;

import java.math.BigDecimal;

/** 제품 마스터 엑셀 한 행 */
public record MasterExcelRow(int rowNumber, String productCode, String productName, Integer eaPerBag,
        Integer bagPerBox, Integer eaPerBox, Integer bagPerPlt, Integer boxPerPlt,
        BigDecimal bagWeight, BigDecimal boxWeight, String companyType) {
}
