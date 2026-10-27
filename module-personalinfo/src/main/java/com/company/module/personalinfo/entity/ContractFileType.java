package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 계약 첨부파일 구분 */
@Getter
@RequiredArgsConstructor
public enum ContractFileType {
    CONTRACT("위수탁 계약서"),
    SUBCONTRACT_CONSENT("재위탁 동의서"),
    SECURITY_PLEDGE("정보보호 및 비밀유지 확약서"),
    DESTRUCTION_CONFIRM("개인정보 파기 확인서"),
    ETC("기타");

    private final String label;
}
