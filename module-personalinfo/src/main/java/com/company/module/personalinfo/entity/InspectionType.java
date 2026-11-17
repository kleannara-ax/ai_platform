package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 위수탁 점검 구분 */
@Getter
@RequiredArgsConstructor
public enum InspectionType {
    /** 정기 점검 (체크리스트 1.관리체계 / 2.개인정보처리시스템) */
    REGULAR("정기 점검"),
    /** 계약 변경·만료 시 점검 (체크리스트 3.계약변경 만료시 체크) */
    TERMINATION("계약변경·만료 점검");

    private final String label;
}
