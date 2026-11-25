package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamMovePlanMonth;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 이동계획 월 기준값 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanMonthResponse {

    private Long movePlanMonthId;
    private Integer yearNo;
    private Integer monthNo;
    private Integer actualUntilDay;
    private String adjustYn;
    private BigDecimal waste2BillCapTon;
    private BigDecimal comboGuaranteeTon;
    private BigDecimal comboBscPrice;
    private BigDecimal comboAscPrice;
    private BigDecimal fluidBillTonHour;
    private BigDecimal fluidImprovementCost;
    private BigDecimal lngM3PerTon;
    private BigDecimal lngUnitPrice;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamMovePlanMonthResponse from(SteamMovePlanMonth e) {
        return from(e, null);
    }

    public static SteamMovePlanMonthResponse from(SteamMovePlanMonth e, Boolean isUpdate) {
        return SteamMovePlanMonthResponse.builder()
                .movePlanMonthId(e.getMovePlanMonthId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .actualUntilDay(e.getActualUntilDay())
                .adjustYn(e.getAdjustYn())
                .waste2BillCapTon(e.getWaste2BillCapTon())
                .comboGuaranteeTon(e.getComboGuaranteeTon())
                .comboBscPrice(e.getComboBscPrice())
                .comboAscPrice(e.getComboAscPrice())
                .fluidBillTonHour(e.getFluidBillTonHour())
                .fluidImprovementCost(e.getFluidImprovementCost())
                .lngM3PerTon(e.getLngM3PerTon())
                .lngUnitPrice(e.getLngUnitPrice())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
