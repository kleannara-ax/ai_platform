package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 이동계획 일자별 운휴 저장 요청 — (PLAN_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamMovePlanDaySaveRequest {

    @NotNull(message = "일자(planDate)는 필수입니다.")
    private LocalDate planDate;

    @DecimalMin(value = "0", message = "downPm2 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "downPm2 은(는) 24 이하여야 합니다.")
    private BigDecimal downPm2;
    @DecimalMin(value = "0", message = "downPm3 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "downPm3 은(는) 24 이하여야 합니다.")
    private BigDecimal downPm3;
    @DecimalMin(value = "0", message = "downTm3 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "downTm3 은(는) 24 이하여야 합니다.")
    private BigDecimal downTm3;
    @DecimalMin(value = "0", message = "downTm4 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "downTm4 은(는) 24 이하여야 합니다.")
    private BigDecimal downTm4;
    @DecimalMin(value = "0", message = "downTm5 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "downTm5 은(는) 24 이하여야 합니다.")
    private BigDecimal downTm5;
    @DecimalMin(value = "0", message = "stopWaste1 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "stopWaste1 은(는) 24 이하여야 합니다.")
    private BigDecimal stopWaste1;
    @DecimalMin(value = "0", message = "stopWaste2 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "stopWaste2 은(는) 24 이하여야 합니다.")
    private BigDecimal stopWaste2;
    @DecimalMin(value = "0", message = "stopCombo 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "stopCombo 은(는) 24 이하여야 합니다.")
    private BigDecimal stopCombo;
    @DecimalMin(value = "0", message = "stopFluid 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "stopFluid 은(는) 24 이하여야 합니다.")
    private BigDecimal stopFluid;
    @DecimalMin(value = "0", message = "stopLng20 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "stopLng20 은(는) 24 이하여야 합니다.")
    private BigDecimal stopLng20;
    @DecimalMin(value = "0", message = "stopLng50 은(는) 0 이상이어야 합니다.")
    @DecimalMax(value = "24", message = "stopLng50 은(는) 24 이하여야 합니다.")
    private BigDecimal stopLng50;
    @Size(max = 500)
    private String note;
}
