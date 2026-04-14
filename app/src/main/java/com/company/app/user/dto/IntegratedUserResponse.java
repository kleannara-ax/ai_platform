package com.company.app.user.dto;

import com.company.core.user.entity.CoreUser;
import com.company.module.user.entity.UserProfile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 + 프로필 통합 응답 DTO
 */
@Getter
@Builder
public class IntegratedUserResponse {

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

    public static IntegratedUserResponse from(CoreUser user, UserProfile profile, String deptName) {
        var b = IntegratedUserResponse.builder()
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
            b.deptCode(profile.getDeptCode())
             .deptName(deptName)
             .position(profile.getPosition())
             .jobTitle(profile.getJobTitle())
             .employeeNo(profile.getEmployeeNo())
             .joinDate(profile.getJoinDate())
             .officePhone(profile.getOfficePhone())
             .internalExt(profile.getInternalExt());
        }

        return b.build();
    }
}
