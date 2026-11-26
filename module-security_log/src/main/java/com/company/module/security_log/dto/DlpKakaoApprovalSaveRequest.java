package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 카카오톡 사용 승인 대장 등록·수정 (신청 상태는 '신청중' 고정) */
@Getter
@Setter
public class DlpKakaoApprovalSaveRequest {

    @Size(max = 200, message = "사업부는 200자까지 입력할 수 있습니다.")
    private String divisionName;

    @NotBlank(message = "부서를 입력해 주세요.")
    @Size(max = 200, message = "부서는 200자까지 입력할 수 있습니다.")
    private String deptName;

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 200, message = "이름은 200자까지 입력할 수 있습니다.")
    private String userName;

    @NotNull(message = "신청 시작일을 입력해 주세요.")
    private LocalDate startDate;

    @NotNull(message = "신청 종료일을 입력해 주세요.")
    private LocalDate endDate;

    @Size(max = 100, message = "전자결재 문서번호는 100자까지 입력할 수 있습니다.")
    private String approvalDocNo;

    @Size(max = 500, message = "사유는 500자까지 입력할 수 있습니다.")
    private String reason;
}
