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
    REGULAR_SUMMARY("정기 점검 요약"),
    AUTO_RENEW_NOTICE("자동연장 확인"),
    PERIODIC_DESTRUCTION_REQUEST("정기 파기확인 요청"),
    PERIODIC_DESTRUCTION_OVERDUE("정기 파기확인 독촉");

    private final String label;
}
