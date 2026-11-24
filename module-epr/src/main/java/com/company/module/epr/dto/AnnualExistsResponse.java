package com.company.module.epr.dto;

/** 해당 연도 판매 실적 저장 여부 */
public record AnnualExistsResponse(int year, boolean exists) {
}
