package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * IP 주소의 사용 상태.
 */
@Getter
public enum IpStatus {

    IN_USE("사용중"),     // 사용자/부서에 할당되어 사용 중
    AVAILABLE("가용"),    // 미할당 (사용 가능)
    RECLAIMED("회수"),    // 회수되어 더 이상 사용하지 않음
    RETURNED("반납");     // PC+IP 반납 (창고/렌탈사 반납) — 사용자·부서·PC 정보 등 모두 비움

    private final String label;

    IpStatus(String label) {
        this.label = label;
    }
}
