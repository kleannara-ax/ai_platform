package com.company.module.kims.entity.enums;

import lombok.Getter;

/**
 * 업무요청 "반납"(IpRequestKind.PC_RETURN)의 세부 반납유형.
 * <ul>
 *   <li>RETURN  : 반납 — PC/장비까지 함께 반납. 사용자·장치·PC 스펙·자산정보 모두 비우고,
 *                 부서는 요청자의 부서를 기반으로 "{부서}보관" 값을 자동 채움(수정 가능)</li>
 *   <li>RECLAIM : 회수 — IP만 회수. PC 정보(사용자/장치/스펙)는 그대로 유지하고, 부서는 공란 처리</li>
 * </ul>
 */
@Getter
public enum IpReturnType {

    RETURN("반납"),
    RECLAIM("회수");

    private final String label;

    IpReturnType(String label) {
        this.label = label;
    }
}
