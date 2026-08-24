package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 전산업무 요청의 유형.
 * <p>업무 도메인에 맞춰 5가지로 구분한다. (요청 등록/검색/집계 기준)
 */
@Getter
public enum RequestType {

    SUPPLY("전산소모품 지급"),   // 소모품 지급 요청
    IP("IP 변경/생성"),          // IP 생성/변경/회수 요청
    PROGRAM("PC관련 불편사항 조치"),  // PC/프로그램 관련 불편사항 조치 (세부 불편유형은 IssueType, 기존 '기타 불편사항 조치' 통합)
    INTERNET("인터넷 공사");     // 신규 설치/자리이동/LAN 포트 등 공사

    /** 화면에 표시할 한글 명칭 */
    private final String label;

    RequestType(String label) {
        this.label = label;
    }
}
