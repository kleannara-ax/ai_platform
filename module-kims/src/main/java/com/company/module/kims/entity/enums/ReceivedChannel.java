package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 업무 요청의 접수 채널.
 * <p>향후 QR 등으로 확장 가능하도록 Enum 으로 관리한다.
 */
@Getter
public enum ReceivedChannel {

    PHONE("전화"),    // 전화로 접수 후 담당자가 등록
    MANUAL("직접/수기"), // 방문/구두 등 직접 수기 등록
    QR("QR");         // (향후) QR 코드를 통한 사용자 직접 등록

    private final String label;

    ReceivedChannel(String label) {
        this.label = label;
    }
}
