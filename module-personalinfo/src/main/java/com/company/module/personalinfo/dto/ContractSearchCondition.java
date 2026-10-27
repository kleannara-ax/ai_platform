package com.company.module.personalinfo.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 계약 목록 검색 조건 (쿼리 파라미터 바인딩) */
@Getter
@Setter
public class ContractSearchCondition {

    /** 업체명/서비스/위탁업무/재위탁업체 통합 검색어 */
    private String keyword;

    private String deptName;

    private String managerName;

    /** Y / N */
    private String ismsScopeYn;

    /** ACTIVE / EXPIRING / EXPIRED / TERMINATED / NO_END_DATE */
    private String status;

    /** PASS / NEED_ACTION / INCOMPLETE / NONE(미점검) */
    private String inspectionResult;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDateTo;
}
