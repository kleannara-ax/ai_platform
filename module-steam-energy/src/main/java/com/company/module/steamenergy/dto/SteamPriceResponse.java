package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamPrice;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 시설별 단가 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamPriceResponse {

    private Long priceId;
    private Integer yearNo;
    private Integer monthNo;
    private String facility;
    private String itemCode;
    private String itemName;
    private String inputType;
    private String unit;
    private BigDecimal priceValue;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** INSERT / UPDATE 구분 */
    private Boolean isUpdate;

    public static SteamPriceResponse from(SteamPrice p) {
        return from(p, null);
    }

    public static SteamPriceResponse from(SteamPrice p, Boolean isUpdate) {
        return SteamPriceResponse.builder()
                .priceId(p.getPriceId())
                .yearNo(p.getYearNo())
                .monthNo(p.getMonthNo())
                .facility(p.getFacility())
                .itemCode(p.getItemCode())
                .itemName(p.getItemName())
                .inputType(p.getInputType())
                .unit(p.getUnit())
                .priceValue(p.getPriceValue())
                .remark(p.getRemark())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
