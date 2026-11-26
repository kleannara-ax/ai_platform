package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 개인정보 다운로드 검토 상태 (자사몰·임직원몰 개인정보 다운로드 내역)
 * - PENDING 외에는 다운로드 사유·확인 내용 필수
 */
@Getter
@RequiredArgsConstructor
public enum PiiReviewStatus {
    PENDING("미검토"),
    NORMAL("정상 업무(사유 확인)"),
    CONFIRMED("사유 불명확·조치 필요"),
    RESOLVED("조치완료");

    private final String label;

    public boolean commentRequired() {
        return this != PENDING;
    }
}
