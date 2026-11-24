package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 복합보일러 운휴·디스퍼져 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamComboDailyDowntimeSaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
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
}
