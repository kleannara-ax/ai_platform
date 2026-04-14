package com.company.core.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 사용자 생성 요청 DTO (프로필 정보 포함)
 */
@Getter
@Setter
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

    private String role;

    // ── 프로필 정보 ──
    private String deptCode;
    private String position;
    private String jobTitle;
    private String employeeNo;
    private LocalDate joinDate;
    private String officePhone;
    private String internalExt;

    /**
     * 로그인 ID 앞뒤 공백 제거
     */
    public void setLoginId(String loginId) {
        this.loginId = (loginId != null) ? loginId.trim() : null;
    }

    /**
     * 비밀번호 앞뒤 공백 제거
     */
    public void setPassword(String password) {
        this.password = (password != null) ? password.trim() : null;
    }

    /**
     * 사용자명 앞뒤 공백 제거
     */
    public void setUserName(String userName) {
        this.userName = (userName != null) ? userName.trim() : null;
    }

    /**
     * 빈 문자열 이메일을 null로 변환, 앞뒤 공백 제거
     */
    public void setEmail(String email) {
        if (email != null) email = email.trim();
        this.email = (email != null && email.isBlank()) ? null : email;
    }

    /**
     * 빈 문자열 전화번호를 null로 변환, 앞뒤 공백 제거
     */
    public void setPhone(String phone) {
        if (phone != null) phone = phone.trim();
        this.phone = (phone != null && phone.isBlank()) ? null : phone;
    }
}
