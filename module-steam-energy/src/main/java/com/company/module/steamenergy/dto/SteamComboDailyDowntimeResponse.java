package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamComboDailyDowntime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 복합보일러 운휴·디스퍼져 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboDailyDowntimeResponse {

    private Long comboDowntimeId;
    private LocalDate usageDate;
    private String changeCombo;
    private String changeJesco;
    private BigDecimal down1;
    private BigDecimal down2;
    private BigDecimal disperserSteam;
    private BigDecimal runtimeMin;
    private BigDecimal ventProduction;
    private BigDecimal ventInternal;
    private BigDecimal ventUsage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamComboDailyDowntimeResponse from(SteamComboDailyDowntime e) {
        return from(e, null);
    }

    public static SteamComboDailyDowntimeResponse from(SteamComboDailyDowntime e, Boolean isUpdate) {
        return SteamComboDailyDowntimeResponse.builder()
                .comboDowntimeId(e.getComboDowntimeId())
                .usageDate(e.getUsageDate())
                .changeCombo(e.getChangeCombo())
                .changeJesco(e.getChangeJesco())
                .down1(e.getDown1())
                .down2(e.getDown2())
                .disperserSteam(e.getDisperserSteam())
                .runtimeMin(e.getRuntimeMin())
                .ventProduction(e.getVentProduction())
                .ventInternal(e.getVentInternal())
                .ventUsage(e.getVentUsage())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
