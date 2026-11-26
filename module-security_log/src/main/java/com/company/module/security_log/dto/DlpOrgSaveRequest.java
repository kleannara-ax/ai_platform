package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 사업부·부서 추가·수정 (추가: parentOrgId 없음 = 사업부, 있음 = 그 사업부 아래 부서) */
@Getter
@Setter
public class DlpOrgSaveRequest {

    private Long parentOrgId;

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 200, message = "이름은 200자까지 입력할 수 있습니다.")
    private String orgName;

    @Pattern(regexp = "^[YN]$", message = "사용 여부는 Y 또는 N 입니다.")
    private String useYn;
}
