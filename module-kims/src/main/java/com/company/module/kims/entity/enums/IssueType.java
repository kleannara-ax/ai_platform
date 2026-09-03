package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * PC관련 불편사항(요청유형 {@link RequestType#PROGRAM})의 세부 불편유형.
 * <p>업무요청 등록 시 사용자가 드롭다운에서 선택한다. (DB 에는 이름 문자열로 저장)
 */
@Getter
public enum IssueType {

    PC_USE("PC 사용불편문의 (안켜짐, 느림 등)"),
    MONITOR("모니터 관련 문의 (안켜짐, 어두워짐 등)"),
    NETWORK("인터넷 관련 문의 (네트워크 안됨, 느림)"),
    PROGRAM_INSTALL("프로그램 설치 문의"),
    PC_NEW("PC 신규 설치 문의"),
    PRINTER("프린터 관련 문의 (스캔, 인쇄안됨, 신규설치 등)"),
    LABEL_PRINTER("라벨프린터 관련 문의"),
    KIOSK("키오스크 관련 문의"),
    PDA("PDA 관련 문의"),
    ETC("기타 문의");

    /** 화면에 표시할 한글 명칭 */
    private final String label;

    IssueType(String label) {
        this.label = label;
    }
}
