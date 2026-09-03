package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 인터넷 공사/설치 유형.
 */
@Getter
public enum InternetWorkType {

    NEW_INSTALL("신규 인터넷 설치"),  // 신규 인터넷 설치
    RELOCATION("자리이동 연결"),      // 자리 이동에 따른 인터넷 연결
    LAN_PORT("LAN 포트 활성화"),      // LAN 포트 활성화
    ETC("기타");                      // 기타 공사

    private final String label;

    InternetWorkType(String label) {
        this.label = label;
    }
}
