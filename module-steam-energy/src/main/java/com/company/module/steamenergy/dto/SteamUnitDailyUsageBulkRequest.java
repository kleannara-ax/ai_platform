package com.company.module.steamenergy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 원단위 일지(호기별) 한 달치 일괄 저장 요청 — 엑셀 가져오기용.
 *
 * <p>clearMissing 이면 그 달의 기존 행 중 목록에 없는 (일자·호기)는 값만 비운다(행은 남긴다).
 */
@Getter
@Setter
public class SteamUnitDailyUsageBulkRequest {

    @NotNull(message = "연도(year)는 필수입니다.")
    private Integer year;

    @NotNull(message = "월(month)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer month;

    private boolean clearMissing;

    @Valid
    private List<SteamUnitDailyUsageSaveRequest> rows = new ArrayList<>();
}
