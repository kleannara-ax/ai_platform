package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 룰 매칭 방식
 */
@Getter
@RequiredArgsConstructor
public enum MatchType {
    KEYWORD("키워드 포함(대소문자 무시)"),
    REGEX("정규식");

    private final String label;
}
