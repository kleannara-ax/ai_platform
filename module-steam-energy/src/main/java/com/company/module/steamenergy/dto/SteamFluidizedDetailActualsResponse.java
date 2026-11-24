package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/** 유동상 세부 운영내역에서 계산한 월별 실적 — 월 키(YYYY-MM) → (항목 셀 주소 → 값) */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailActualsResponse {

    private Map<String, Map<String, String>> months;

    @SuppressWarnings("unchecked")
    public static SteamFluidizedDetailActualsResponse of(Map<String, Object> actuals) {
        return new SteamFluidizedDetailActualsResponse((Map<String, Map<String, String>>) actuals.get("months"));
    }
}
