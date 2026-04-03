package com.company.core.user.dto;

import com.company.core.user.entity.CoreUser;
import com.company.core.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 응답 DTO
 * 프로필 정보 포함 (부서, 직급, 직책, 사번 등)
 */
@Getter
@Builder
public class UserResponse {

    private Long userId;
    private String loginId;
    private String userName;
    private String email;
    private String phone;
    private Role role;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── 프로필 정보 ──
    private Long deptId;
    private String deptName;
    private String position;
    private String jobTitle;
    private String employeeNo;
    private LocalDate joinDate;
    private String officePhone;
    private String internalExt;

    public static UserResponse from(CoreUser user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
