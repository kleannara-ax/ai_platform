package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 접속 계정 검토 상태 (접속 행위 분석 > 계정별 검토)
 * - 검토 행이 없으면 '미검토'
 */
@Getter
@RequiredArgsConstructor
public enum AccountReviewStatus {
    NORMAL("정상 확인"),
    CONFIRMED("이상 확인(조치 필요)"),
    RESOLVED("조치완료");

    private final String label;

    /** 소명·조치 내용이 반드시 필요한 상태 */
    public boolean commentRequired() {
        return this != NORMAL;
    }
}
