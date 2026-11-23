package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamIncineratorPlanMonthly;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 소각로 사업계획대비 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorPlanMonthlyResponse {

    private Long planMonthlyId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal contractLabor;
    private BigDecimal laborPlan;
    private BigDecimal laborActual;
    private BigDecimal operationPlan;
    private BigDecimal operationActual;
    private BigDecimal totalPlan;
    private String noteU1;
    private String noteU2;
    private BigDecimal brkMonthPlan;
    private BigDecimal brkMonthActual;
    private BigDecimal brkYtdPlan;
    private BigDecimal brkYtdActual;
    private BigDecimal brkPrevActual;
    private String brkNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamIncineratorPlanMonthlyResponse from(SteamIncineratorPlanMonthly e) {
        return from(e, null);
    }

    public static SteamIncineratorPlanMonthlyResponse from(SteamIncineratorPlanMonthly e, Boolean isUpdate) {
        return SteamIncineratorPlanMonthlyResponse.builder()
                .planMonthlyId(e.getPlanMonthlyId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .contractLabor(e.getContractLabor())
                .laborPlan(e.getLaborPlan())
                .laborActual(e.getLaborActual())
                .operationPlan(e.getOperationPlan())
                .operationActual(e.getOperationActual())
                .totalPlan(e.getTotalPlan())
                .noteU1(e.getNoteU1())
                .noteU2(e.getNoteU2())
                .brkMonthPlan(e.getBrkMonthPlan())
                .brkMonthActual(e.getBrkMonthActual())
                .brkYtdPlan(e.getBrkYtdPlan())
                .brkYtdActual(e.getBrkYtdActual())
                .brkPrevActual(e.getBrkPrevActual())
                .brkNote(e.getBrkNote())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
