package com.company.module.epr.dto;

/** 파트 코드와 등록 SKU 수 */
public record PartCategoryResponse(String partPrefix, String partName, long skuCount) {
}
