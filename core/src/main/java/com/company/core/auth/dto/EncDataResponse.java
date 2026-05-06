package com.company.core.auth.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 암호화 데이터 처리 응답 DTO
 * POST /api/login/sendEncData
 *
 * SSO 검증 성공 시 JWT 토큰과 함께 로그인 정보를 반환
 */
@Getter
@Builder
public class EncDataResponse {

    /** JWT Access Token */
    private final String accessToken;

    /** JWT Refresh Token */
    private final String refreshToken;

    /** 토큰 타입 (Bearer) */
    private final String tokenType;

    /** Access Token 만료시간 (초) */
    private final Long expiresIn;

    /** SSO 인증된 사용자 ID (sproId) */
    private final String sproId;

    /** 처리 결과 메시지 */
    private final String resultMessage;

    public static EncDataResponse of(String accessToken, String refreshToken,
                                     Long expiresIn, String sproId, String message) {
        return EncDataResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .sproId(sproId)
                .resultMessage(message)
                .build();
    }
}
