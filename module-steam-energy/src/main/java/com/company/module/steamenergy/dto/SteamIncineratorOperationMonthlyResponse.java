package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamIncineratorOperationMonthly;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 소각로 계획대비 가동실적 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorOperationMonthlyResponse {

    private Long operationMonthlyId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal u1Plan;
    private BigDecimal u1Actual;
    private BigDecimal u2Plan;
    private BigDecimal u2Actual;
    private BigDecimal steamPlan;
    private BigDecimal steamActual;
    private BigDecimal ratePlan;
    private BigDecimal rateActual;
    private BigDecimal costPlan;
    private BigDecimal costActual;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamIncineratorOperationMonthlyResponse from(SteamIncineratorOperationMonthly e) {
        return from(e, null);
    }

    public static SteamIncineratorOperationMonthlyResponse from(SteamIncineratorOperationMonthly e, Boolean isUpdate) {
        return SteamIncineratorOperationMonthlyResponse.builder()
                .operationMonthlyId(e.getOperationMonthlyId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .u1Plan(e.getU1Plan())
                .u1Actual(e.getU1Actual())
                .u2Plan(e.getU2Plan())
                .u2Actual(e.getU2Actual())
                .steamPlan(e.getSteamPlan())
                .steamActual(e.getSteamActual())
                .ratePlan(e.getRatePlan())
                .rateActual(e.getRateActual())
                .costPlan(e.getCostPlan())
                .costActual(e.getCostActual())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
