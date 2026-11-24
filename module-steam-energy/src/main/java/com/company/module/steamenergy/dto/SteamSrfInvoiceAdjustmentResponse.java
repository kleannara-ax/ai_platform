package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamSrfInvoiceAdjustment;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SRF 인보이스 Adjustment Factor 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceAdjustmentResponse {

    private Long invoiceAdjustmentId;
    private Integer yearNo;
    private String section;
    private String itemCode;
    private BigDecimal coefficient;
    private BigDecimal baseValue;
    private BigDecimal presentValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamSrfInvoiceAdjustmentResponse from(SteamSrfInvoiceAdjustment e) {
        return from(e, null);
    }

    public static SteamSrfInvoiceAdjustmentResponse from(SteamSrfInvoiceAdjustment e, Boolean isUpdate) {
        return SteamSrfInvoiceAdjustmentResponse.builder()
                .invoiceAdjustmentId(e.getInvoiceAdjustmentId())
                .yearNo(e.getYearNo())
                .section(e.getSection())
                .itemCode(e.getItemCode())
                .coefficient(e.getCoefficient())
                .baseValue(e.getBaseValue())
                .presentValue(e.getPresentValue())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
