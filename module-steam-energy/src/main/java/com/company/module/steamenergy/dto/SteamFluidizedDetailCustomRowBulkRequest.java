package com.company.module.steamenergy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 유동상 세부 운영내역 (추가 행) 일괄 저장 요청 — 한 해(또는 한 달) 범위. 엑셀 가져오기·화면 일괄 저장용. */
@Getter
@Setter
public class SteamFluidizedDetailCustomRowBulkRequest {

    @NotNull(message = "연도(year)는 필수입니다.")
    private Integer year;

    /** 있으면 그 달만 범위로 한다 */
    private Integer month;

    /** true 면 범위 안에서 목록에 없는 행을 삭제한다 */
    private boolean clearMissing;

    @Valid
    private List<SteamFluidizedDetailCustomRowSaveRequest> rows = new ArrayList<>();
}
