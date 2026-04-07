package com.company.core.auth.dto;

import com.company.core.security.CustomUserDetails;
import lombok.Builder;
import lombok.Getter;

/**
 * 현재 로그인 사용자 정보 응답 DTO
 * 세션 복원 시 사용
 */
@Getter
@Builder
public class MeResponse {

    private Long userId;
    private String loginId;
    private String userName;
    private String role;

    public static MeResponse from(CustomUserDetails userDetails) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER");

        return MeResponse.builder()
                .userId(userDetails.getUserId())
                .loginId(userDetails.getLoginId())
                .userName(userDetails.getUserDisplayName())
                .role(role)
                .build();
    }
}
