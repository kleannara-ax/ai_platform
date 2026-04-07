package com.company.module.user.dto;

import com.company.module.user.entity.UserProfile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 프로필 응답 DTO
 */
@Getter
@Builder
public class UserProfileResponse {

    private Long profileId;
    private Long userId;
    private String deptCode;
    private String position;
    private String jobTitle;
    private String employeeNo;
    private LocalDate joinDate;
    private String officePhone;
    private String internalExt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserProfileResponse from(UserProfile profile) {
        return UserProfileResponse.builder()
                .profileId(profile.getProfileId())
                .userId(profile.getUserId())
                .deptCode(profile.getDeptCode())
                .position(profile.getPosition())
                .jobTitle(profile.getJobTitle())
                .employeeNo(profile.getEmployeeNo())
                .joinDate(profile.getJoinDate())
                .officePhone(profile.getOfficePhone())
                .internalExt(profile.getInternalExt())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
