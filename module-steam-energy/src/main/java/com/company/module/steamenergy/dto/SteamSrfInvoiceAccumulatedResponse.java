package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamSrfInvoiceAccumulated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SRF 인보이스 누계 스팀량 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceAccumulatedResponse {

    private Long invoiceAccumulatedId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal contractVolume;
    private BigDecimal bscVolume;
    private BigDecimal ascVolume;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamSrfInvoiceAccumulatedResponse from(SteamSrfInvoiceAccumulated e) {
        return from(e, null);
    }

    public static SteamSrfInvoiceAccumulatedResponse from(SteamSrfInvoiceAccumulated e, Boolean isUpdate) {
        return SteamSrfInvoiceAccumulatedResponse.builder()
                .invoiceAccumulatedId(e.getInvoiceAccumulatedId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .contractVolume(e.getContractVolume())
                .bscVolume(e.getBscVolume())
                .ascVolume(e.getAscVolume())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
