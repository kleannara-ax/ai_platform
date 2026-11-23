package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamSrfInvoiceSummary;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SRF 인보이스 Summary 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceSummaryResponse {

    private Long invoiceSummaryId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal bscRate;
    private BigDecimal bscFactor;
    private BigDecimal afbscrBsc;
    private BigDecimal bscSteamAmount;
    private BigDecimal ascRate;
    private BigDecimal ascFactor;
    private BigDecimal ascSteamAmount;
    private BigDecimal others0;
    private BigDecimal others1;
    private BigDecimal others2;
    private BigDecimal others3;
    private BigDecimal payableAdjust;
    private BigDecimal liquidatedDamage;
    private BigDecimal ascRefundAnnual;
    private BigDecimal utilitiesDeduction;
    private BigDecimal otherDeduction1;
    private BigDecimal otherDeduction2;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamSrfInvoiceSummaryResponse from(SteamSrfInvoiceSummary e) {
        return from(e, null);
    }

    public static SteamSrfInvoiceSummaryResponse from(SteamSrfInvoiceSummary e, Boolean isUpdate) {
        return SteamSrfInvoiceSummaryResponse.builder()
                .invoiceSummaryId(e.getInvoiceSummaryId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .bscRate(e.getBscRate())
                .bscFactor(e.getBscFactor())
                .afbscrBsc(e.getAfbscrBsc())
                .bscSteamAmount(e.getBscSteamAmount())
                .ascRate(e.getAscRate())
                .ascFactor(e.getAscFactor())
                .ascSteamAmount(e.getAscSteamAmount())
                .others0(e.getOthers0())
                .others1(e.getOthers1())
                .others2(e.getOthers2())
                .others3(e.getOthers3())
                .payableAdjust(e.getPayableAdjust())
                .liquidatedDamage(e.getLiquidatedDamage())
                .ascRefundAnnual(e.getAscRefundAnnual())
                .utilitiesDeduction(e.getUtilitiesDeduction())
                .otherDeduction1(e.getOtherDeduction1())
                .otherDeduction2(e.getOtherDeduction2())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
