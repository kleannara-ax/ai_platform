package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamSrfInvoiceBsc;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SRF 인보이스 BSC 단가 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceBscResponse {

    private Long invoiceBscId;
    private Integer yearNo;
    private BigDecimal bscRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamSrfInvoiceBscResponse from(SteamSrfInvoiceBsc e) {
        return from(e, null);
    }

    public static SteamSrfInvoiceBscResponse from(SteamSrfInvoiceBsc e, Boolean isUpdate) {
        return SteamSrfInvoiceBscResponse.builder()
                .invoiceBscId(e.getInvoiceBscId())
                .yearNo(e.getYearNo())
                .bscRate(e.getBscRate())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
