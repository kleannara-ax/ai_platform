package com.company.core.user.dto;

import com.company.core.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 생성 요청 DTO (프로필 정보 포함)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    @NotBlank(message = "로그인 ID는 필수입니다.")
    @Size(min = 4, max = 50, message = "로그인 ID는 4~50자여야 합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 100, message = "비밀번호는 8~100자여야 합니다.")
    private String password;

    @NotBlank(message = "사용자명은 필수입니다.")
    @Size(max = 100, message = "사용자명은 100자 이하여야 합니다.")
    private String userName;

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    private String phone;

    private Role role;

    // ── 프로필 정보 ──
    private Long deptId;
    private String position;
    private String jobTitle;
    private String employeeNo;
    private LocalDate joinDate;
    private String officePhone;
    private String internalExt;
}
