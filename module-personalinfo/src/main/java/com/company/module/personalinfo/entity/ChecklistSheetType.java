package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 체크리스트 엑셀 시트 구분 */
@Getter
@RequiredArgsConstructor
public enum ChecklistSheetType {
    MANAGEMENT("1. 관리체계"),
    SYSTEM("2. 개인정보처리시스템"),
    TERMINATION("3. 계약변경 만료시 체크");

    private final String label;
}
