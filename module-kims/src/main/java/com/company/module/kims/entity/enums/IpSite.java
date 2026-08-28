package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * PC(IP) 관리대장의 사업장 구분.
 * <p>기존(리팩터링 이전)에는 사업장 구분이 없었고 모든 PC가 사실상 청주공장 데이터였으므로,
 * 기존 데이터는 전부 {@link #CHEONGJU} 로 취급한다(마이그레이션 기본값).
 * {@link #SEOUL} 은 신규 도입된 사업장으로, 기존 청주 데이터를 전혀 공유하지 않는
 * 별도의 PC(IP) 관리대장을 구성한다.
 */
@Getter
public enum IpSite {

    CHEONGJU("청주공장"),
    SEOUL("서울");

    private final String label;

    IpSite(String label) {
        this.label = label;
    }
}
