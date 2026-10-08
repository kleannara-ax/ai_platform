package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 탐지 결과 검토 상태
 */
@Getter
@RequiredArgsConstructor
public enum ReviewStatus {
    PENDING("미검토"),
    CONFIRMED("이상징후 확인(조치필요)"),
    RESOLVED("조치완료"),
    FALSE_POSITIVE("오탐");

    private final String label;
}
