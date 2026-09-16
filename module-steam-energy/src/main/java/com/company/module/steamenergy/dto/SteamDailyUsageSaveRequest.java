package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일일 스팀 사용 실적 저장 요청 DTO (일자/설비 기준 업서트)
 */
@Getter
@Setter
public class SteamDailyUsageSaveRequest {

    @NotNull(message = "사용일자(usageDate)는 필수입니다.")
    private LocalDate usageDate;

    @NotNull(message = "설비ID(equipmentId)는 필수입니다.")
    private Long equipmentId;

    private BigDecimal steamAmount;

    private BigDecimal runtimeHours;

    private BigDecimal productionKg;

    private BigDecimal unitRate;

    @Size(max = 500)
    private String remark;
}
