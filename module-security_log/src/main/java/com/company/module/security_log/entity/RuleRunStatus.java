package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 룰별 분석 실행 상태 (업로드 1건 x 룰 1개)
 */
@Getter
@RequiredArgsConstructor
public enum RuleRunStatus {
    DONE("분석 완료"),
    FAILED("분석 실패");

    private final String label;
}
