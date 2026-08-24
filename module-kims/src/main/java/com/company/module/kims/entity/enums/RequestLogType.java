package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 처리 로그(RequestLog)의 종류.
 * <p>어떤 행위로 인해 로그가 남았는지 구분한다.
 */
@Getter
public enum RequestLogType {

    CREATED("등록"),          // 요청이 처음 등록될 때
    STATUS_CHANGED("상태변경"), // 처리상태가 변경될 때
    PROCESS_NOTE("처리내용");   // 처리 메모/소모품 지급 등 부가 기록

    /** 화면에 표시할 한글 명칭 */
    private final String label;

    RequestLogType(String label) {
        this.label = label;
    }
}
