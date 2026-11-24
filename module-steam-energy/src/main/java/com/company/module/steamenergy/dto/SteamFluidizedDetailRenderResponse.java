package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 계산된 유동상 세부 운영내역 — 화면에 그릴 표 두 구역.
 * 구역마다 range(셀 범위), cells(셀 주소 → 표시 문자열), merges(병합 범위 목록)가 들어 있다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailRenderResponse {

    /** 본표 (3. 세부 운영내역) */
    private Map<String, Object> main;
    /** 요약 구역 */
    private Map<String, Object> summary;

    @SuppressWarnings("unchecked")
    public static SteamFluidizedDetailRenderResponse of(Map<String, Object> rendered) {
        return new SteamFluidizedDetailRenderResponse(
                (Map<String, Object>) rendered.get("main"), (Map<String, Object>) rendered.get("summary"));
    }
}
