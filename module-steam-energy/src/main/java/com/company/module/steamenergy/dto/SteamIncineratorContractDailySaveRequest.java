package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 소각로 도급내역 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamIncineratorContractDailySaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal u1Steam;
    private BigDecimal u1Unit;
    private BigDecimal u1Runtime;
    private BigDecimal u1Fixed;
    private BigDecimal u2Steam;
    private BigDecimal u2Unit;
    private BigDecimal u2Runtime;
    private BigDecimal u2Fixed;
    private BigDecimal realSteam1;
    private BigDecimal realUnit1;
    private BigDecimal realSteam2;
    private BigDecimal realUnit2;
}
