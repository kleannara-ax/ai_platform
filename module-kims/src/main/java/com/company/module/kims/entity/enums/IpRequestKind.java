package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * IP 변경/생성 업무요청(요청유형 IP)의 세부 '요청목록'.
 * <p>완료 처리 시 자동 반영 방식이 종류별로 다르다.
 * <ul>
 *   <li>IP_CHANGE  : 변경자의 현재 PC를 새 IP로 이동(기존 IP 회수)</li>
 *   <li>IP_NEW     : 생성자에게 신규 IP 부여</li>
 *   <li>PC_CHANGE  : 변경자 PC의 선택 항목(부서/스펙 등) 수정</li>
 *   <li>ETC        : 기타 변경(비고 기반 수기 처리 — 자동 반영 없음)</li>
 * </ul>
 */
@Getter
public enum IpRequestKind {

    IP_CHANGE("IP변경"),
    IP_NEW("IP신규생성"),
    PC_CHANGE("PC변경"),
    ETC("기타 변경");

    private final String label;

    IpRequestKind(String label) {
        this.label = label;
    }
}
