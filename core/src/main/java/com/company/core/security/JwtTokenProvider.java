package com.company.core.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 토큰 생성 및 검증
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final CustomUserDetailsService userDetailsService;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:3600000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration,
            CustomUserDetailsService userDetailsService) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Access Token 생성
     */
    public String createAccessToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return createToken(userDetails.getLoginId(), userDetails.getUserId(), accessTokenExpiration);
    }

    /**
     * Refresh Token 생성
     */
    public String createRefreshToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return createToken(userDetails.getLoginId(), userDetails.getUserId(), refreshTokenExpiration);
    }

    /**
     * 토큰에서 Authentication 객체 추출
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String loginId = claims.getSubject();

        UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT 토큰 만료: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("지원하지 않는 JWT 토큰: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("잘못된 JWT 토큰 형식: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT 서명 검증 실패: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어 있음: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 토큰에서 로그인 ID 추출
     */
    public String getLoginIdFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    // ── 내부 메서드 ──

    private String createToken(String loginId, Long userId, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(loginId)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}
