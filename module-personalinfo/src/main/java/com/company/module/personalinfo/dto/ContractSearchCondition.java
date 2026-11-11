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

    /** 기준 연도 (요약 카드에서 선택). 있으면 원계약 기간이 이 연도에 걸친 계약만 */
    private Integer baseYear;

    /** 기준 연도 구분: ACTIVE(계약중) / ENDED(종료). baseYear 가 있을 때만 적용 */
    private String yearStatus;

    /**
     * 기준 연도 점검 조건 (baseYear 가 있을 때만 적용)
     * INSPECTED / NOT_INSPECTED : 기준 연도 수탁사 정기 점검 완료 / 미완료
     * TERM_UPLOADED / TERM_MISSING : 계약종결 체크(계약변경·만료 점검) 업로드 / 미업로드
     */
    private String yearCheck;
}
