package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/** 정기 파기확인 주기 (월 / 분기 / 반기) */
@Getter
@RequiredArgsConstructor
public enum DestructionCycle {
    MONTH("월", 1),
    QUARTER("분기", 3),
    HALF("반기", 6);

    private final String label;
    /** 한 기간의 개월 수 */
    private final int months;

    /** 문자열 → 주기 (비었거나 모르는 값이면 분기) */
    public static DestructionCycle of(String value) {
        if (value == null || value.isBlank()) {
            return QUARTER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return QUARTER;
        }
    }
}
