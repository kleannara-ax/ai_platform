package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 담당자 이력 구분 */
@Getter
@RequiredArgsConstructor
public enum ContactChangeType {
    CREATE("계약 등록"),
    UPDATE("담당자 변경"),
    INITIAL("기존 데이터 이관");

    private final String label;
}
