package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * IP 주소의 사용 상태.
 */
@Getter
public enum IpStatus {

    IN_USE("사용중"),     // 사용자/부서에 할당되어 사용 중
    AVAILABLE("가용"),    // 미할당 (사용 가능)
    RECLAIMED("회수");    // 회수되어 더 이상 사용하지 않음

    private final String label;

    IpStatus(String label) {
        this.label = label;
    }
}
