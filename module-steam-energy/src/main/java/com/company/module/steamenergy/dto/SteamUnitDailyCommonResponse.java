package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamUnitDailyCommon;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 원단위 일지(일자별 공통 계측값) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamUnitDailyCommonResponse {

    private Long unitCommonId;
    private LocalDate usageDate;
    private BigDecimal disperserTotal;
    private BigDecimal tocSteam;
    private BigDecimal pica121Vent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamUnitDailyCommonResponse from(SteamUnitDailyCommon e) {
        return from(e, null);
    }

    public static SteamUnitDailyCommonResponse from(SteamUnitDailyCommon e, Boolean isUpdate) {
        return SteamUnitDailyCommonResponse.builder()
                .unitCommonId(e.getUnitCommonId())
                .usageDate(e.getUsageDate())
                .disperserTotal(e.getDisperserTotal())
                .tocSteam(e.getTocSteam())
                .pica121Vent(e.getPica121Vent())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
