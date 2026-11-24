package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 접근 기록 행위 */
@Getter
@RequiredArgsConstructor
public enum AccessActionType {
    VIEW_CONTRACT("계약 상세 조회"),
    VIEW_INSPECTION("점검 상세 조회"),
    DOWNLOAD_FILE("계약서·증빙 다운로드"),
    DOWNLOAD_INSPECTION("점검 결과 다운로드"),
    DOWNLOAD_EXCEL("계약 목록 엑셀 다운로드"),
    DOWNLOAD_ACCESS_LOG("접근 기록 엑셀 다운로드");

    private final String label;
}
