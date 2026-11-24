package com.company.module.epr.dto;

import com.company.module.epr.entity.EprProductMaster;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 제품 마스터 목록 한 줄 */
@Getter
@Builder
public class ProductMasterResponse {

    private String productCode;
    private String partPrefix;
    private String partName;
    private String productName;
    private Integer eaPerBag;
    private Integer bagPerBox;
    private Integer eaPerBox;
    private Integer bagPerPlt;
    private Integer boxPerPlt;
    private BigDecimal bagWeight;
    private BigDecimal boxWeight;
    private String companyType;
    private LocalDateTime updatedAt;

    /** partName 이 null 이면 파트 코드가 없는 제품이므로 "미분류(코드)" 로 표시한다. */
    public static ProductMasterResponse from(EprProductMaster product, String partName) {
        String prefix = product.getPartPrefix();
        return ProductMasterResponse.builder()
                .productCode(product.getProductCode())
                .partPrefix(prefix)
                .partName(partName == null ? "미분류(" + prefix + ")" : partName)
                .productName(product.getProductName())
                .eaPerBag(product.getEaPerBag())
                .bagPerBox(product.getBagPerBox())
                .eaPerBox(product.getEaPerBox())
                .bagPerPlt(product.getBagPerPlt())
                .boxPerPlt(product.getBoxPerPlt())
                .bagWeight(product.getBagWeight())
                .boxWeight(product.getBoxWeight())
                .companyType(product.getCompanyType())
                .updatedAt(product.getUpdatedAt() != null ? product.getUpdatedAt() : product.getCreatedAt())
                .build();
    }
}
