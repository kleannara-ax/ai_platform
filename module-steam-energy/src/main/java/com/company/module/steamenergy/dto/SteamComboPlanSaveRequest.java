package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 복합보일러 연간 계획 저장 요청 — (YEAR_NO, TARGET_YM) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamComboPlanSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "대상 월 (YYYY-MM, 기준연도 화면의 월 칸)(targetYm)은(는) 필수입니다.")
    private String targetYm;

    private BigDecimal comboBasic;
    private BigDecimal comboSpecial;
    private BigDecimal comboActual;
    private BigDecimal jescoPlan;
    private BigDecimal jescoActual;
    private BigDecimal lngBoiler;
    private BigDecimal lngCombo;
}
