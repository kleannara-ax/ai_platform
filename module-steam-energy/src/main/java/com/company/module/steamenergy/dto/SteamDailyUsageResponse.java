package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamDailyUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일일 스팀 사용 실적 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamDailyUsageResponse {

    private Long usageId;
    private LocalDate usageDate;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private BigDecimal steamAmount;
    private BigDecimal runtimeHours;
    private BigDecimal productionKg;
    private BigDecimal unitRate;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** INSERT / UPDATE 구분 */
    private Boolean isUpdate;

    public static SteamDailyUsageResponse from(SteamDailyUsage u) {
        return from(u, null, null, null);
    }

    public static SteamDailyUsageResponse from(SteamDailyUsage u, String equipmentCode,
                                               String equipmentName, Boolean isUpdate) {
        return SteamDailyUsageResponse.builder()
                .usageId(u.getUsageId())
                .usageDate(u.getUsageDate())
                .equipmentId(u.getEquipmentId())
                .equipmentCode(equipmentCode)
                .equipmentName(equipmentName)
                .steamAmount(u.getSteamAmount())
                .runtimeHours(u.getRuntimeHours())
                .productionKg(u.getProductionKg())
                .unitRate(u.getUnitRate())
                .remark(u.getRemark())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
