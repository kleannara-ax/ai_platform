package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 사업부·부서 목록 (사업부마다 아래 부서) */
@Getter
@Builder
public class DlpOrgResponse {

    private Long orgId;
    private String orgName;
    private String useYn;
    /** 이 이름을 쓰는 승인 대장 건수 */
    private long usedCount;
    /** 아래 부서 (부서 행이면 null) */
    private List<DlpOrgResponse> depts;
}
