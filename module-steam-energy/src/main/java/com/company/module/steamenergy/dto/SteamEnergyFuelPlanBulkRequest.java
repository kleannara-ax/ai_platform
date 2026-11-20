package com.company.module.steamenergy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 연료비 사업계획 한 해치 일괄 저장 요청 — 엑셀 가져오기용. */
@Getter
@Setter
public class SteamEnergyFuelPlanBulkRequest {

    @NotNull(message = "연도(year)는 필수입니다.")
    private Integer year;

    /** true 면 목록에 없는 달의 값을 비운다 (파일이 그 해의 기준) */
    private boolean clearMissing;

    @Valid
    private List<SteamEnergyFuelPlanSaveRequest> rows = new ArrayList<>();
}
