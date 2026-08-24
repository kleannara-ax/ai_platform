package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * IP 변경 이력(IpHistory)의 종류.
 */
@Getter
public enum IpChangeType {

    CREATED("신규생성"),     // 새 IP 생성/등록
    MODIFIED("정보변경"),    // 위치/장비 등 정보 변경
    USER_CHANGED("사용자변경"), // 사용부서/사용자 변경
    RECLAIMED("회수"),       // IP 회수 (IP만 미사용)
    RETURNED("반납"),        // PC+IP 반납 (PC/장비도 함께 반납)
    TEMP_CREATED("임시생성"),  // 임시 IP 신규 부여/등록
    TEMP_MODIFIED("임시변경"), // 임시 IP 이동/변경
    DELETED("IP삭제");         // IP 삭제(사용자 공란 — 회수와 별개로 관리대장에서 삭제 처리된 건)

    private final String label;

    IpChangeType(String label) {
        this.label = label;
    }
}
