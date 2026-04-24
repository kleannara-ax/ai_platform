package com.company.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 인증 필터
 * 모든 HTTP 요청에서 Authorization 헤더의 Bearer 토큰을 검증한다.
 *
 * <p>PS-INSP 모듈 전용 USERID 파라미터 자동 인증:
 * MES 등 외부 시스템에서 USERID 파라미터를 전달하면
 * DB 조회 없이 해당 값을 사용자 ID로 간주하여 인증을 통과시킨다.
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
     * DB 조회 없이 해당 USERID 값을 그대로 인증 주체로 사용하여 무조건 인증을 통과시킨다.
     * USERID에 어떤 값이든 입력되면 로그인 없이 접근 가능하다.
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

        // DB 조회 없이 USERID 값으로 가상 인증 객체 생성 (ROLE_USER 부여)
        String trimmedUserId = userId.trim();
        User principal = new User(trimmedUserId, "", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.info("[PS-INSP] USERID 파라미터 자동 인증 (DB 미조회) - user: {}, uri: {}", trimmedUserId, requestUri);
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
