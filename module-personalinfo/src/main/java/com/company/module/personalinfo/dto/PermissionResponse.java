package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

/** 화면 버튼 노출 제어용 현재 사용자 권한 */
@Getter
@Builder(toBuilder = true)
public class PermissionResponse {

    private boolean canRead;
    private boolean canWrite;
    private boolean canDelete;
    private boolean canSendMail;
    /** 전체 부서 계약 조회 가능 (관리자) */
    private boolean allDepartments;
    /** 비관리자의 소속부서 (없으면 null → 볼 수 있는 계약 없음) */
    private String deptName;
}
