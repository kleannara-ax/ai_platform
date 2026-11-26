package com.company.module.security_log.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안로그 관리(module-security_log) 화면 파일 전용 보안 체인.
 *
 * <p>플랫폼 메인 화면(index.html)은 /security_log/ 메뉴를 iframe 으로 연다.
 * iframe 이 화면 파일을 불러올 때는 Authorization 헤더가 붙지 않으므로,
 * module-kims(KimsSecurityConfig)·module-cckb(CckbSecurityConfig) 와 같은 방식으로
 * 화면 파일만 공개한다.
 *
 * <ul>
 *   <li>공개: /security_log/*.html, /security_log/css/**, /security_log/js/** (화면 모양만, 데이터 없음)</li>
 *   <li>공개 안 함: 데이터 API /security_log-api/** — 이 체인에 포함되지 않으므로
 *       core 메인 보안 체인(JWT 인증 + @PreAuthorize 역할 검사)을 그대로 탄다.</li>
 * </ul>
 *
 * <p>core SecurityConfig 는 수정하지 않는다.
 */
@Configuration
public class SecLogSecurityConfig {

    @Bean
    @Order(-2)
    public SecurityFilterChain seclogPageSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/security_log/*.html", "/security_log/css/**", "/security_log/js/**")
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
