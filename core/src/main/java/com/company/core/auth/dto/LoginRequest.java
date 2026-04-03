package com.company.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 로그인 요청 DTO
 */
@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "로그인 ID는 필수입니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

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
}
