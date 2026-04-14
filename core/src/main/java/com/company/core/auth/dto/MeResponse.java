package com.company.core.auth.dto;

import com.company.core.security.CustomUserDetails;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

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
    private String role;      // 하위호환: 대표 역할
    private List<String> roles; // 다중 역할 목록

    public static MeResponse from(CustomUserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList());

        String role = roles.isEmpty() ? "ROLE_USER" : roles.get(0);

        return MeResponse.builder()
                .userId(userDetails.getUserId())
                .loginId(userDetails.getLoginId())
                .userName(userDetails.getUserDisplayName())
                .role(role)
                .roles(roles)
                .build();
    }
}
