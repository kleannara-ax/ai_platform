package com.company.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서 생성 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCreateRequest {

    @NotBlank(message = "부서명은 필수입니다.")
    @Size(max = 100, message = "부서명은 100자 이하여야 합니다.")
    private String deptName;

    @NotBlank(message = "부서 코드는 필수입니다.")
    @Size(max = 20, message = "부서 코드는 20자 이하여야 합니다.")
    private String deptCode;

    private Long parentDeptId;

    private Integer sortOrder;
}
