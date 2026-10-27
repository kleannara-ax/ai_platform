package com.company.module.personalinfo.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 계약 종료 처리 요청 */
@Getter
@Setter
public class ContractTerminateRequest {

    /** 종료일 (미입력 시 오늘) */
    private LocalDate terminatedDate;

    @Size(max = 500)
    private String reason;
}
