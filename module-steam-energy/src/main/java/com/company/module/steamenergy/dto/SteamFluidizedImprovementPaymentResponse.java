package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedImprovementPayment;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 유동상 설비개선 월별 비용 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedImprovementPaymentResponse {

    private Long improvementPaymentId;
    private Integer yearNo;
    private String recordKey;
    private String payMonth;
    private BigDecimal paymentAmount;
    private BigDecimal spentAmount;
    private String vendorName;
    private String usageDesc;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedImprovementPaymentResponse from(SteamFluidizedImprovementPayment e) {
        return from(e, null);
    }

    public static SteamFluidizedImprovementPaymentResponse from(SteamFluidizedImprovementPayment e, Boolean isUpdate) {
        return SteamFluidizedImprovementPaymentResponse.builder()
                .improvementPaymentId(e.getImprovementPaymentId())
                .yearNo(e.getYearNo())
                .recordKey(e.getRecordKey())
                .payMonth(e.getPayMonth())
                .paymentAmount(e.getPaymentAmount())
                .spentAmount(e.getSpentAmount())
                .vendorName(e.getVendorName())
                .usageDesc(e.getUsageDesc())
                .sortOrder(e.getSortOrder())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
