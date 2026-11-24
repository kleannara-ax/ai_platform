package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 스팀사용량 원단위(기타 포함) 저장 요청 — (연, 월) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyRatioSteamUnitSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    private BigDecimal unitPm2;
    private BigDecimal unitPm3;
    private BigDecimal unitPaperSubtotal;
    private BigDecimal unitTm3;
    private BigDecimal unitTm4;
    private BigDecimal unitTm5;
    private BigDecimal unitTissueSubtotal;
}
