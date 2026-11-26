package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 위험도 (탐지 룰 / 탐지 결과 공통)
 * level 값이 클수록 위험도가 높다.
 */
@Getter
@RequiredArgsConstructor
public enum Severity {
    LOW("낮음", 1),
    MEDIUM("보통", 2),
    HIGH("높음", 3),
    CRITICAL("심각", 4);

    private final String label;
    private final int level;

    public boolean isHigherThan(Severity other) {
        return other == null || this.level > other.level;
    }
}
