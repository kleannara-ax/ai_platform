package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 인터넷 공사 진행 상태.
 */
@Getter
public enum InternetWorkStatus {

    REQUESTED("접수"),       // 공사 요청 접수
    IN_PROGRESS("진행중"),   // 공사 진행 중
    COMPLETED("완료"),       // 공사 완료 (완료일 자동 입력)
    CANCELED("취소");        // 취소

    private final String label;

    InternetWorkStatus(String label) {
        this.label = label;
    }
}
