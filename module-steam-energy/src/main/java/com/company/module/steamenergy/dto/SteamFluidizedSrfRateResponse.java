package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedSrfRate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 유동상 SRF 처리단가 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedSrfRateResponse {

    private Long srfRateId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal vendor01Rate;
    private BigDecimal vendor02Rate;
    private BigDecimal vendor03Rate;
    private BigDecimal vendor04Rate;
    private BigDecimal vendor05Rate;
    private BigDecimal vendor06Rate;
    private BigDecimal vendor07Rate;
    private BigDecimal vendor08Rate;
    private BigDecimal vendor09Rate;
    private BigDecimal vendor10Rate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedSrfRateResponse from(SteamFluidizedSrfRate e) {
        return from(e, null);
    }

    public static SteamFluidizedSrfRateResponse from(SteamFluidizedSrfRate e, Boolean isUpdate) {
        return SteamFluidizedSrfRateResponse.builder()
                .srfRateId(e.getSrfRateId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .vendor01Rate(e.getVendor01Rate())
                .vendor02Rate(e.getVendor02Rate())
                .vendor03Rate(e.getVendor03Rate())
                .vendor04Rate(e.getVendor04Rate())
                .vendor05Rate(e.getVendor05Rate())
                .vendor06Rate(e.getVendor06Rate())
                .vendor07Rate(e.getVendor07Rate())
                .vendor08Rate(e.getVendor08Rate())
                .vendor09Rate(e.getVendor09Rate())
                .vendor10Rate(e.getVendor10Rate())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
