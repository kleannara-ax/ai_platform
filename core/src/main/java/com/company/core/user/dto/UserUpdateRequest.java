package com.company.core.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 수정 요청 DTO (프로필 정보 포함)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(max = 100, message = "사용자명은 100자 이하여야 합니다.")
    private String userName;

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    private String phone;

    // ── 프로필 정보 ──
    private Long deptId;
    private String position;
    private String jobTitle;
    private String employeeNo;
    private LocalDate joinDate;
    private String officePhone;
    private String internalExt;
}
