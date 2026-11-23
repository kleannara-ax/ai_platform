package com.company.module.steamenergy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 유동상·소각로·복합·외부 일일 스팀 구매량 한 달치 일괄 저장 요청 — 엑셀 가져오기용. */
@Getter
@Setter
public class SteamFuelDailyUsageBulkRequest {

    @NotNull(message = "연도(year)는 필수입니다.")
    private Integer year;

    @NotNull(message = "월(month)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer month;

    private boolean clearMissing;

    @Valid
    private List<SteamFuelDailyUsageSaveRequest> rows = new ArrayList<>();
}
