package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 점검 결과 (업로드한 체크리스트 평가값으로 자동 판정) */
@Getter
@RequiredArgsConstructor
public enum InspectionResult {
    /** 모든 평가 항목이 Y 또는 NA */
    PASS("적합"),
    /** N(미충족) 항목이 1건 이상 */
    NEED_ACTION("조치 필요"),
    /** 평가가 비어 있는 항목이 있음 */
    INCOMPLETE("평가 미완료");

    private final String label;
}
