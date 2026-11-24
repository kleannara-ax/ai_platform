package com.company.module.personalinfo.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 계약 종료 처리 요청 */
@Getter
@Setter
public class ContractTerminateRequest {

    /** 종료일 (미입력 시 원계약 종료일, 그것도 없으면 오늘). 원계약 시작일보다 빠를 수 없음 */
    private LocalDate terminatedDate;

    @Size(max = 500)
    private String reason;

    /** 만료 서류(만료 점검 체크리스트·확약서·파기 확인서)를 다 받지 못했을 때 그 사유 (미수취 서류가 있으면 필수) */
    @Size(max = 500)
    private String expiryDocsNote;

    /** 미수취 만료 서류 독촉 메일 계속 발송 (Y/N, 기본 Y) */
    private String expiryDocsRemindYn;
}
