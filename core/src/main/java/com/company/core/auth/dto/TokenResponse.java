package com.company.core.auth.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 토큰 응답 DTO
 */
@Getter
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;

    public static TokenResponse of(String accessToken, String refreshToken, Long expiresIn) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }
}
