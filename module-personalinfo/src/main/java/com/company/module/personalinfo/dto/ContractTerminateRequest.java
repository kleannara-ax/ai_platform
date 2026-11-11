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
}
