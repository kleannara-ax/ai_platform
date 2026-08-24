package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 업무 요청의 처리상태.
 * <p>요청 등록 시 기본값은 {@link #RECEIVED}(접수) 이며,
 * 담당자가 처리 과정에 따라 상태를 변경한다.
 */
@Getter
public enum RequestStatus {

    RECEIVED("접수"),       // 요청이 막 등록된 상태(기본값)
    IN_PROGRESS("처리중"),  // 담당자가 처리에 착수한 상태
    ON_HOLD("보류"),        // 일시 중단/대기 상태
    COMPLETED("완료"),      // 처리가 끝난 상태 (completedAt 자동 입력)
    REJECTED("반려"),       // 요청을 받아들이지 않은 상태
    CANCELED("취소");       // 요청자가 취소했거나 무효 처리된 상태

    /** 화면에 표시할 한글 명칭 */
    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }
}
