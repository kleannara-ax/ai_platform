package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamComboPlan;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 복합보일러 연간 계획 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboPlanResponse {

    private Long comboPlanId;
    private Integer yearNo;
    private String targetYm;
    private BigDecimal comboBasic;
    private BigDecimal comboSpecial;
    private BigDecimal comboActual;
    private BigDecimal jescoPlan;
    private BigDecimal jescoActual;
    private BigDecimal lngBoiler;
    private BigDecimal lngCombo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamComboPlanResponse from(SteamComboPlan e) {
        return from(e, null);
    }

    public static SteamComboPlanResponse from(SteamComboPlan e, Boolean isUpdate) {
        return SteamComboPlanResponse.builder()
                .comboPlanId(e.getComboPlanId())
                .yearNo(e.getYearNo())
                .targetYm(e.getTargetYm())
                .comboBasic(e.getComboBasic())
                .comboSpecial(e.getComboSpecial())
                .comboActual(e.getComboActual())
                .jescoPlan(e.getJescoPlan())
                .jescoActual(e.getJescoActual())
                .lngBoiler(e.getLngBoiler())
                .lngCombo(e.getLngCombo())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
