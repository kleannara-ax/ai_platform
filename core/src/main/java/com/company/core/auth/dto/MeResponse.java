package com.company.core.auth.dto;

import com.company.core.security.CustomUserDetails;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 현재 로그인 사용자 정보 응답 DTO
 * 세션 복원 시 사용
 *
 * <p>다중 역할(Multi-Role) 지원: {@code roles}에 사용자가 보유한 전체 역할이 담긴다.
 * {@code role}은 하위호환용 필드로, roles의 첫번째 값이 채워진다
 * (프론트엔드가 아직 roles 배열을 사용하지 않는 구간을 위한 것으로, 신규 로직은
 * 반드시 roles 배열 기준으로 동작해야 한다).
 */
@Getter
@Builder
public class MeResponse {

    private Long userId;
    private String loginId;
    private String userName;
    private String role;
    private List<String> roles;

    public static MeResponse from(CustomUserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .distinct()
                .collect(Collectors.toList());
        if (roles.isEmpty()) {
            roles = List.of("ROLE_USER");
        }

        return MeResponse.builder()
                .userId(userDetails.getUserId())
                .loginId(userDetails.getLoginId())
                .userName(userDetails.getUserDisplayName())
                .role(roles.get(0))
                .roles(roles)
                .build();
    }
}
