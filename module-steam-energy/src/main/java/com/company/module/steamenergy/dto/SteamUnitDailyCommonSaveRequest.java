package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 원단위 일지(일자별 공통 계측값) 저장 요청 — 일자 업서트.
 */
@Getter
@Setter
public class SteamUnitDailyCommonSaveRequest {

    @NotNull(message = "일자(usageDate)는 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal disperserTotal;
    private BigDecimal tocSteam;
    private BigDecimal pica121Vent;
}
