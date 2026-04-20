package com.company.core.auth.controller;

import com.company.core.auth.dto.LoginRequest;
import com.company.core.auth.dto.MeResponse;
import com.company.core.auth.dto.TokenResponse;
import com.company.core.auth.service.AuthService;
import com.company.core.common.response.ApiResponse;
import com.company.core.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 API
 * URL Prefix: /api/auth
 * /api/auth/login, /api/auth/refresh → SecurityConfig에서 permitAll
 * /api/auth/me → 인증 필요 (JWT 토큰)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", tokenResponse));
    }

    /**
     * 토큰 갱신
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        TokenResponse tokenResponse = authService.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("토큰 갱신 성공", tokenResponse));
    }

    /**
     * 현재 로그인 사용자 정보 조회 (토큰 유효성 검증 겸용)
     * 브라우저 새로고침 시 세션 복원용
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MeResponse response = MeResponse.from(userDetails);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * CSRF 토큰 엔드포인트 (JWT 기반이므로 CSRF 비활성화 상태)
     * 소방 모듈 프론트엔드(csrf.js)가 호출하므로 404 방지를 위해 빈 응답 반환
     */
    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<Void>> csrf() {
        return ResponseEntity.ok(ApiResponse.success());
    }
}
