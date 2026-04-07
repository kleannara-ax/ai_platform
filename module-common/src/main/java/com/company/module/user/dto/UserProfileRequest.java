package com.company.module.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 프로필 생성/수정 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    private String deptCode;
    private String position;
    private String jobTitle;
    private String employeeNo;
    private LocalDate joinDate;
    private String officePhone;
    private String internalExt;
}
