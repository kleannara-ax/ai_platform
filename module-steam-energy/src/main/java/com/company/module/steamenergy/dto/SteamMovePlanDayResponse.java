package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamMovePlanDay;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 이동계획 일자별 운휴 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanDayResponse {

    private Long movePlanDayId;
    private LocalDate planDate;
    private BigDecimal downPm2;
    private BigDecimal downPm3;
    private BigDecimal downTm3;
    private BigDecimal downTm4;
    private BigDecimal downTm5;
    private BigDecimal stopWaste1;
    private BigDecimal stopWaste2;
    private BigDecimal stopCombo;
    private BigDecimal stopFluid;
    private BigDecimal stopLng20;
    private BigDecimal stopLng50;
    private BigDecimal idleWaste1;
    private BigDecimal idleWaste2;
    private BigDecimal idleCombo;
    private BigDecimal idleFluid;
    private BigDecimal idleLng20;
    private BigDecimal idleLng50;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamMovePlanDayResponse from(SteamMovePlanDay e) {
        return from(e, null);
    }

    public static SteamMovePlanDayResponse from(SteamMovePlanDay e, Boolean isUpdate) {
        return SteamMovePlanDayResponse.builder()
                .movePlanDayId(e.getMovePlanDayId())
                .planDate(e.getPlanDate())
                .downPm2(e.getDownPm2())
                .downPm3(e.getDownPm3())
                .downTm3(e.getDownTm3())
                .downTm4(e.getDownTm4())
                .downTm5(e.getDownTm5())
                .stopWaste1(e.getStopWaste1())
                .stopWaste2(e.getStopWaste2())
                .stopCombo(e.getStopCombo())
                .stopFluid(e.getStopFluid())
                .stopLng20(e.getStopLng20())
                .stopLng50(e.getStopLng50())
                .idleWaste1(e.getIdleWaste1())
                .idleWaste2(e.getIdleWaste2())
                .idleCombo(e.getIdleCombo())
                .idleFluid(e.getIdleFluid())
                .idleLng20(e.getIdleLng20())
                .idleLng50(e.getIdleLng50())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
