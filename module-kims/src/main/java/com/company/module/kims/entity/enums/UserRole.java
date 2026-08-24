package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 사용자 권한.
 * <p>향후 관리자/전산담당자/일반사용자 권한 분리를 위한 기본 구조.
 * Spring Security 의 hasRole('XXX') 과 매칭되도록 권한명 그대로 사용한다.
 */
@Getter
public enum UserRole {

    ADMIN("관리자"),   // 모든 권한
    STAFF("전산담당자"), // 배정 요청 처리, 각 내역 입력
    USER("일반사용자");  // 요청 등록/본인 요청 조회

    private final String label;

    UserRole(String label) {
        this.label = label;
    }
}
