package com.company.module.epr.dto;

import java.math.BigDecimal;

/** 마스터에 없는 제품코드의 판매수량 */
public record UnregisteredSale(String productCode, String uploadedProductName, BigDecimal salesEa, int sourceRows) {
}
