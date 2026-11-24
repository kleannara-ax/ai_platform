package com.company.module.epr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * EPR 화면 경로 전용 보안 체인.
 *
 * <p>core 의 SecurityConfig 를 고치지 않고, 모듈이 자기 화면 경로({@code /epr/**})에 대한 체인만 기여한다.
 * (module-kims · module-safety · module-cckb 와 같은 방식, {@code @Order(-2)} 로 core 메인 체인보다 먼저 매칭)
 *
 * <ul>
 *   <li>{@code /epr/**} — 정적 HTML/JS/CSS. 플랫폼 SPA 가 iframe 으로 열 때 Authorization 헤더를 보낼 수 없어
 *       화면 파일 자체는 공개한다. 데이터는 아래 API 로만 오간다.</li>
 *   <li>{@code /epr-api/**} — 여기서 매칭하지 않는다. core 메인 체인의 {@code anyRequest().authenticated()} 가
 *       그대로 적용되어 플랫폼 JWT 로 보호된다.</li>
 * </ul>
 *
 * <p>원래 프로젝트의 자체 로그인(이메일·비밀번호, 세션, CSRF, 사용자 관리)은 제거했다. 로그인은 플랫폼 계정으로 통일한다.
 */
@Configuration
public class EprSecurityConfig {

    @Bean
    @Order(-2)
    public SecurityFilterChain eprPageSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/epr/**")
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
