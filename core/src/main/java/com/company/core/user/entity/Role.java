package com.company.core.user.entity;

/**
 * 사용자 역할 정의
 * Spring Security의 Role 기반 권한과 매핑된다.
 */
public enum Role {

    ROLE_ADMIN("관리자"),
    ROLE_MANAGER("매니저"),
    ROLE_FIRE_MANAGER("소방관리자"),
    ROLE_USER("일반 사용자");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
