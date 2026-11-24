package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamSrfInvoiceMonthly;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SRF 인보이스 월별 운영내역 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceMonthlyResponse {

    private Long invoiceMonthlyId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal steamQty;
    private BigDecimal steamUnit;
    private BigDecimal powerQty;
    private BigDecimal powerUnit;
    private BigDecimal lngQty;
    private BigDecimal lngUnit;
    private BigDecimal processQty;
    private BigDecimal processUnit;
    private BigDecimal softQty;
    private BigDecimal softUnit;
    private BigDecimal softenQty;
    private BigDecimal softenUnit;
    private BigDecimal lifeQty;
    private BigDecimal lifeUnit;
    private BigDecimal etcSpecial;
    private BigDecimal etcBsc7;
    private BigDecimal etcSrfShare;
    private BigDecimal etcInspect;
    private BigDecimal etcOver7;
    private BigDecimal etcAirOver;
    private BigDecimal etcWaste;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamSrfInvoiceMonthlyResponse from(SteamSrfInvoiceMonthly e) {
        return from(e, null);
    }

    public static SteamSrfInvoiceMonthlyResponse from(SteamSrfInvoiceMonthly e, Boolean isUpdate) {
        return SteamSrfInvoiceMonthlyResponse.builder()
                .invoiceMonthlyId(e.getInvoiceMonthlyId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .steamQty(e.getSteamQty())
                .steamUnit(e.getSteamUnit())
                .powerQty(e.getPowerQty())
                .powerUnit(e.getPowerUnit())
                .lngQty(e.getLngQty())
                .lngUnit(e.getLngUnit())
                .processQty(e.getProcessQty())
                .processUnit(e.getProcessUnit())
                .softQty(e.getSoftQty())
                .softUnit(e.getSoftUnit())
                .softenQty(e.getSoftenQty())
                .softenUnit(e.getSoftenUnit())
                .lifeQty(e.getLifeQty())
                .lifeUnit(e.getLifeUnit())
                .etcSpecial(e.getEtcSpecial())
                .etcBsc7(e.getEtcBsc7())
                .etcSrfShare(e.getEtcSrfShare())
                .etcInspect(e.getEtcInspect())
                .etcOver7(e.getEtcOver7())
                .etcAirOver(e.getEtcAirOver())
                .etcWaste(e.getEtcWaste())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
