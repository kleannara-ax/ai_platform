package com.company.core.user.dto;

import com.company.core.user.entity.CoreUser;
import com.company.core.user.profile.UserProfileSnapshot;
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
    private String role;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── 프로필 정보 ──
    private String deptCode;
    private String deptName;
    private String position;
    private String jobTitle;
    private String employeeNo;
    private LocalDate joinDate;
    private String officePhone;
    private String internalExt;

    public static UserResponse from(CoreUser user) {
        return from(user, null);
    }

    public static UserResponse from(CoreUser user, UserProfileSnapshot profile) {
        var builder = UserResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt());

        if (profile != null) {
            builder.deptCode(profile.deptCode())
                    .deptName(profile.deptName())
                    .position(profile.position())
                    .jobTitle(profile.jobTitle())
                    .employeeNo(profile.employeeNo())
                    .joinDate(profile.joinDate())
                    .officePhone(profile.officePhone())
                    .internalExt(profile.internalExt());
        }

        return builder.build();
    }
}
