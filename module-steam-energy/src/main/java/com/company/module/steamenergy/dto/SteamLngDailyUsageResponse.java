package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamLngDailyUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일별 LNG 연료 사용량 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamLngDailyUsageResponse {

    private Long lngUsageId;
    private LocalDate usageDate;
    private BigDecimal boiler20t1;
    private BigDecimal boiler20t2;
    private BigDecimal boiler50t;
    private BigDecimal boiler60t;
    private BigDecimal boilerCombined;
    private BigDecimal boilerNewIncinerator;
    private BigDecimal boilerUnitPrice;
    private BigDecimal burnerPm3;
    private BigDecimal burnerTm3;
    private BigDecimal burnerTm4;
    private BigDecimal burnerTm5;
    private BigDecimal burnerUnitPrice;
    private BigDecimal mixPm2;
    private BigDecimal mixPm3;
    private BigDecimal mixTm3;
    private BigDecimal mixTm4;
    private BigDecimal mixTm5;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamLngDailyUsageResponse from(SteamLngDailyUsage e) {
        return from(e, null);
    }

    public static SteamLngDailyUsageResponse from(SteamLngDailyUsage e, Boolean isUpdate) {
        return SteamLngDailyUsageResponse.builder()
                .lngUsageId(e.getLngUsageId())
                .usageDate(e.getUsageDate())
                .boiler20t1(e.getBoiler20t1())
                .boiler20t2(e.getBoiler20t2())
                .boiler50t(e.getBoiler50t())
                .boiler60t(e.getBoiler60t())
                .boilerCombined(e.getBoilerCombined())
                .boilerNewIncinerator(e.getBoilerNewIncinerator())
                .boilerUnitPrice(e.getBoilerUnitPrice())
                .burnerPm3(e.getBurnerPm3())
                .burnerTm3(e.getBurnerTm3())
                .burnerTm4(e.getBurnerTm4())
                .burnerTm5(e.getBurnerTm5())
                .burnerUnitPrice(e.getBurnerUnitPrice())
                .mixPm2(e.getMixPm2())
                .mixPm3(e.getMixPm3())
                .mixTm3(e.getMixTm3())
                .mixTm4(e.getMixTm4())
                .mixTm5(e.getMixTm5())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
