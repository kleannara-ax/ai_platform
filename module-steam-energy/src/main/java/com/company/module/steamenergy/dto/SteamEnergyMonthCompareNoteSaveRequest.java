package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


/**
 * 회계비용 마감비교 비고 저장 요청 — 연도 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamEnergyMonthCompareNoteSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    private String noteFuel;
    private String noteDeduct;
    private String noteActual;
    private String noteSteamRatio;
    private String notePlan;
    private String noteProd;
    private String noteProduct;
    private String noteUnit;
    private String noteProfit;
    private String noteAux;
}
