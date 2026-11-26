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
    REGEX("정규식"),
    /**
     * 내부 IP 외 접속: 줄에서 처음 나오는 IPv4 가 [관리 › 내부 IP 설정]의 사용 중 대역에 없으면 일치.
     * 패턴 = 대상 줄 조건(정규식, 모든 줄은 ".*")
     */
    EXTERNAL_IP("내부 IP 외 접속");

    private final String label;
}
