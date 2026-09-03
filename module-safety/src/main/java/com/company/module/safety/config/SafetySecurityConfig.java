package com.company.module.safety.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SAFETY 전용 보안 체인.
 *
 * <p>core 를 수정하지 않고, 모듈이 자기 경로에 대한 독립 SecurityFilterChain 을 기여한다.
 * (module-kims 의 {@code /kims/**} 체인과 같은 방식, @Order(-2) 로 먼저 매칭)
 *
 * <ul>
 *   <li>{@code /safety/**}  — 화면(정적 HTML/JS). 플랫폼 SPA iframe 으로 로드되므로
 *       X-Frame-Options 는 sameOrigin. 실제 데이터는 아래 API 로만 조회된다.</li>
 * </ul>
 *
 * <p>{@code /safety-api/**} 는 여기서 매칭하지 않는다. core 메인 체인의
 * {@code anyRequest().authenticated()} 가 적용되어 플랫폼 JWT 로 보호되고,
 * 각 컨트롤러의 {@code @PreAuthorize} 로 역할까지 확인된다.
 * (다만 사진 조회(view)는 &lt;img&gt; 태그에서 Authorization 헤더를 보낼 수 없으므로
 * 컨트롤러에서 공개 처리한다 — module-fire 의 files/** 공개 패턴과 동일한 이유)
 */
@Configuration
public class SafetySecurityConfig {

    @Bean
    @Order(-2)
    public SecurityFilterChain safetyPageSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/safety/**", "/safety-api/photos/*/view")
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
