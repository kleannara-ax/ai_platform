package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 알림 메일 종류 */
@Getter
@RequiredArgsConstructor
public enum AlertType {
    EXPIRY("만료 알림"),
    EXPIRY_DOCS("만료 서류 독촉"),
    ACTION_OVERDUE("조치 지연"),
    REGULAR_INSPECTION("정기 점검 요청"),
    REGULAR_SUMMARY("정기 점검 요약");

    private final String label;
}
