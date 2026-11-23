package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamSrfInvoiceOperationDaily;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** SRF 인보이스 운영내역 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceOperationDailyResponse {

    private Long invoiceOperationId;
    private LocalDate usageDate;
    private BigDecimal powerKwh;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamSrfInvoiceOperationDailyResponse from(SteamSrfInvoiceOperationDaily e) {
        return from(e, null);
    }

    public static SteamSrfInvoiceOperationDailyResponse from(SteamSrfInvoiceOperationDaily e, Boolean isUpdate) {
        return SteamSrfInvoiceOperationDailyResponse.builder()
                .invoiceOperationId(e.getInvoiceOperationId())
                .usageDate(e.getUsageDate())
                .powerKwh(e.getPowerKwh())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
