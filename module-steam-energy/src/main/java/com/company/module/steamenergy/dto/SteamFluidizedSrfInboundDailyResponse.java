package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedSrfInboundDaily;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 유동상 SRF 입고내역 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedSrfInboundDailyResponse {

    private Long srfInboundId;
    private LocalDate usageDate;
    private BigDecimal vendor01Kg;
    private BigDecimal vendor02Kg;
    private BigDecimal vendor03Kg;
    private BigDecimal vendor04Kg;
    private BigDecimal vendor05Kg;
    private BigDecimal vendor06Kg;
    private BigDecimal vendor07Kg;
    private BigDecimal vendor08Kg;
    private BigDecimal vendor09Kg;
    private BigDecimal vendor10Kg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedSrfInboundDailyResponse from(SteamFluidizedSrfInboundDaily e) {
        return from(e, null);
    }

    public static SteamFluidizedSrfInboundDailyResponse from(SteamFluidizedSrfInboundDaily e, Boolean isUpdate) {
        return SteamFluidizedSrfInboundDailyResponse.builder()
                .srfInboundId(e.getSrfInboundId())
                .usageDate(e.getUsageDate())
                .vendor01Kg(e.getVendor01Kg())
                .vendor02Kg(e.getVendor02Kg())
                .vendor03Kg(e.getVendor03Kg())
                .vendor04Kg(e.getVendor04Kg())
                .vendor05Kg(e.getVendor05Kg())
                .vendor06Kg(e.getVendor06Kg())
                .vendor07Kg(e.getVendor07Kg())
                .vendor08Kg(e.getVendor08Kg())
                .vendor09Kg(e.getVendor09Kg())
                .vendor10Kg(e.getVendor10Kg())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
