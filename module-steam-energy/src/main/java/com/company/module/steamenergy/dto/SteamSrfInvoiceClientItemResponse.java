package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamSrfInvoiceClientItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SRF 인보이스 Client Invoice 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceClientItemResponse {

    private Long clientItemId;
    private Integer yearNo;
    private Integer monthNo;
    private String section;
    private Integer lineNo;
    private String itemLabel;
    private String unitLabel;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamSrfInvoiceClientItemResponse from(SteamSrfInvoiceClientItem e) {
        return from(e, null);
    }

    public static SteamSrfInvoiceClientItemResponse from(SteamSrfInvoiceClientItem e, Boolean isUpdate) {
        return SteamSrfInvoiceClientItemResponse.builder()
                .clientItemId(e.getClientItemId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .section(e.getSection())
                .lineNo(e.getLineNo())
                .itemLabel(e.getItemLabel())
                .unitLabel(e.getUnitLabel())
                .qty(e.getQty())
                .unitPrice(e.getUnitPrice())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
