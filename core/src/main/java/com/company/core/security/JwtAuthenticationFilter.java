package com.company.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 * 모든 HTTP 요청에서 Authorization 헤더의 Bearer 토큰을 검증한다.
 *
 * <p>PS-INSP 모듈 전용 USERID 파라미터 자동 인증:
 * MES 등 외부 시스템에서 USERID 파라미터를 전달하면
 * 플랫폼 로그인 없이 해당 사용자로 자동 인증한다.
 * (적용 범위: /ps-insp-api/** 경로만)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    /** USERID 파라미터 자동 인증을 허용하는 경로 접두사 */
    private static final String PS_INSP_API_PREFIX = "/ps-insp-api/";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1) JWT 토큰 인증 (기존 로직)
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Security Context에 인증 정보 저장: {}", authentication.getName());
        }

        // 2) JWT 인증이 없는 경우 → USERID 파라미터로 자동 인증 (PS-INSP 경로만)
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            tryUserIdParamAuth(request);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * USERID 파라미터 기반 자동 인증 (PS-INSP 모듈 전용)
     *
     * <p>MES 등 외부 시스템에서 USERID 파라미터를 URL에 포함하여 호출하는 경우,
     * 플랫폼 JWT 토큰 없이도 해당 사용자로 인증을 처리한다.
     * 보안 범위를 /ps-insp-api/** 경로로 제한한다.
     */
    private void tryUserIdParamAuth(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith(PS_INSP_API_PREFIX)) {
            return; // PS-INSP 경로가 아니면 무시
        }

        String userId = request.getParameter("USERID");
        if (!StringUtils.hasText(userId)) {
            return;
        }

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userId.trim());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("[PS-INSP] USERID 파라미터 자동 인증 - user: {}, uri: {}", userId, requestUri);
        } catch (UsernameNotFoundException e) {
            log.warn("[PS-INSP] USERID 파라미터 인증 실패 - 존재하지 않는 사용자: {}", userId);
        }
    }

    /**
     * Request Header에서 Bearer 토큰 추출
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
