package com.company.module.user.dto;

import com.company.module.user.entity.Department;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 부서 응답 DTO
 */
@Getter
@Builder
public class DepartmentResponse {

    private Long deptId;
    private String deptName;
    private String deptCode;
    private Long parentDeptId;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public static DepartmentResponse from(Department dept) {
        return DepartmentResponse.builder()
                .deptId(dept.getDeptId())
                .deptName(dept.getDeptName())
                .deptCode(dept.getDeptCode())
                .parentDeptId(dept.getParentDeptId())
                .sortOrder(dept.getSortOrder())
                .enabled(dept.getEnabled())
                .createdAt(dept.getCreatedAt())
                .build();
    }
}
