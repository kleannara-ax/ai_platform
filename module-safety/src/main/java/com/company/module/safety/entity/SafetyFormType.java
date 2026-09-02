package com.company.module.safety.entity;

import java.util.Arrays;

/**
 * 매뉴얼 서식 유형.
 *
 * <p>서식마다 상세 표의 열 구성과 머리말 항목이 다르다. 열 구성 자체는
 * {@link SafetyManualColumn} 에 데이터로 들어가므로, 이 열거형은 "어떤 기본 열로 시작할지"와
 * 화면 표시명을 정하는 데만 쓴다.
 */
public enum SafetyFormType {

    /** 안전작업 매뉴얼 — 공정 사진/설명/위험요인/보호구/비고 */
    WORK_METHOD("안전작업 매뉴얼"),

    /** 작업 위험성 평가서 — 작업 순서/발생 가능한 위험/체크/대책/체크 */
    RISK_ASSESSMENT("작업 위험성 평가서");

    private final String displayName;

    SafetyFormType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 저장된 코드 문자열을 열거형으로. 값이 없거나 알 수 없으면 기본 서식으로 본다. */
    public static SafetyFormType of(String code) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(WORK_METHOD);
    }
}
