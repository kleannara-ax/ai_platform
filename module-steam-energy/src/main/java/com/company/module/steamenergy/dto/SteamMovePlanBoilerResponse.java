package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamMovePlanBoiler;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 이동계획 보일러별 기준값 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanBoilerResponse {

    private Long movePlanBoilerId;
    private Integer yearNo;
    private Integer monthNo;
    private String boilerCode;
    private String useYn;
    private BigDecimal maxTonDay;
    private BigDecimal minTonDay;
    private BigDecimal steamUnitPrice;
    private BigDecimal idleHourPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamMovePlanBoilerResponse from(SteamMovePlanBoiler e) {
        return from(e, null);
    }

    public static SteamMovePlanBoilerResponse from(SteamMovePlanBoiler e, Boolean isUpdate) {
        return SteamMovePlanBoilerResponse.builder()
                .movePlanBoilerId(e.getMovePlanBoilerId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .boilerCode(e.getBoilerCode())
                .useYn(e.getUseYn())
                .maxTonDay(e.getMaxTonDay())
                .minTonDay(e.getMinTonDay())
                .steamUnitPrice(e.getSteamUnitPrice())
                .idleHourPrice(e.getIdleHourPrice())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
