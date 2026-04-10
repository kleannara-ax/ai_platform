package com.company.core.auth.service;

import com.company.core.auth.dto.LoginRequest;
import com.company.core.auth.dto.TokenResponse;
import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.security.CustomUserDetails;
import com.company.core.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 인증 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.access-token-expiration:3600000}")
    private long accessTokenExpiration;

    /**
     * 로그인 처리
     */
    public TokenResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getLoginId(),
                            request.getPassword()
                    )
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            log.info("로그인 성공: loginId={}", userDetails.getLoginId());

            String accessToken = jwtTokenProvider.createAccessToken(authentication);
            String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

            return TokenResponse.of(accessToken, refreshToken, accessTokenExpiration / 1000);

        } catch (DisabledException e) {
            log.warn("비활성화된 사용자 로그인 시도: loginId={}", request.getLoginId());
            throw new BusinessException(ErrorCode.USER_DISABLED);
        } catch (BadCredentialsException e) {
            log.warn("로그인 실패 (자격 증명 불일치): loginId={}", request.getLoginId());
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
    }

    /**
     * 토큰 갱신
     */
    public TokenResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        try {
            Authentication authentication = jwtTokenProvider.getAuthentication(refreshToken);
            String newAccessToken = jwtTokenProvider.createAccessToken(authentication);
            String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication);

            log.info("토큰 갱신 완료: loginId={}", authentication.getName());

            return TokenResponse.of(newAccessToken, newRefreshToken, accessTokenExpiration / 1000);
        } catch (Exception e) {
            log.warn("토큰 갱신 중 오류 발생: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
