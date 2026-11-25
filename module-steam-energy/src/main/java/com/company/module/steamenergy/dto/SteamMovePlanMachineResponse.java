package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamMovePlanMachine;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 이동계획 호기별 생산계획 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanMachineResponse {

    private Long movePlanMachineId;
    private Integer yearNo;
    private Integer monthNo;
    private String machineCode;
    private BigDecimal productionTon;
    private BigDecimal runtimeHours;
    private String planKind;
    private BigDecimal unitOverride;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamMovePlanMachineResponse from(SteamMovePlanMachine e) {
        return from(e, null);
    }

    public static SteamMovePlanMachineResponse from(SteamMovePlanMachine e, Boolean isUpdate) {
        return SteamMovePlanMachineResponse.builder()
                .movePlanMachineId(e.getMovePlanMachineId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .machineCode(e.getMachineCode())
                .productionTon(e.getProductionTon())
                .runtimeHours(e.getRuntimeHours())
                .planKind(e.getPlanKind())
                .unitOverride(e.getUnitOverride())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
