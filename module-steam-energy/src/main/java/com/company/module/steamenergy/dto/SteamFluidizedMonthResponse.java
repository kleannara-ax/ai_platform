package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedMonth;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 유동상 월별 계획·비용 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedMonthResponse {

    private Long fluidizedMonthId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal planOperating;
    private BigDecimal planPower;
    private BigDecimal planWaste;
    private BigDecimal planSrfIncome;
    private String summaryRemark;
    private String monthNote;
    private BigDecimal laborCost;
    private BigDecimal operatingPayCost;
    private BigDecimal improvementPayCost;
    private BigDecimal improvementCost;
    private BigDecimal deductionCost;
    private BigDecimal extraPayment;
    private BigDecimal wastewaterQty;
    private BigDecimal soilQty;
    private BigDecimal soilCost;
    private BigDecimal contractCost;
    private BigDecimal operatingCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedMonthResponse from(SteamFluidizedMonth e) {
        return from(e, null);
    }

    public static SteamFluidizedMonthResponse from(SteamFluidizedMonth e, Boolean isUpdate) {
        return SteamFluidizedMonthResponse.builder()
                .fluidizedMonthId(e.getFluidizedMonthId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .planOperating(e.getPlanOperating())
                .planPower(e.getPlanPower())
                .planWaste(e.getPlanWaste())
                .planSrfIncome(e.getPlanSrfIncome())
                .summaryRemark(e.getSummaryRemark())
                .monthNote(e.getMonthNote())
                .laborCost(e.getLaborCost())
                .operatingPayCost(e.getOperatingPayCost())
                .improvementPayCost(e.getImprovementPayCost())
                .improvementCost(e.getImprovementCost())
                .deductionCost(e.getDeductionCost())
                .extraPayment(e.getExtraPayment())
                .wastewaterQty(e.getWastewaterQty())
                .soilQty(e.getSoilQty())
                .soilCost(e.getSoilCost())
                .contractCost(e.getContractCost())
                .operatingCost(e.getOperatingCost())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
