package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 계약 첨부파일 구분 */
@Getter
@RequiredArgsConstructor
public enum ContractFileType {
    CONTRACT("위수탁 계약서", null),
    SUBCONTRACT_CONSENT("재위탁 동의서", null),
    SECURITY_PLEDGE("정보보호 및 비밀유지 확약서", "서명일"),
    DESTRUCTION_CONFIRM("개인정보 파기 확인서", "파기 완료일"),
    ETC("기타", null);

    private final String label;
    /** 서류 일자 항목 이름 (없으면 서류 일자를 받지 않음) */
    private final String documentDateLabel;

    public boolean hasDocumentDate() {
        return documentDateLabel != null;
    }
}
