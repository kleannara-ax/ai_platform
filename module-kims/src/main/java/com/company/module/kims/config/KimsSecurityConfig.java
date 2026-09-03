package com.company.module.kims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * KIMS 전용 보안 체인.
 *
 * <p>core 를 수정하지 않고, 모듈이 자기 경로에 대한 독립 SecurityFilterChain 을 기여한다.
 * (module-steam-energy 의 {@code /steam/**} 체인과 같은 방식, @Order(-2) 로 먼저 매칭)
 *
 * <ul>
 *   <li>{@code /kims/**}  — 화면(정적 HTML/JS). 플랫폼 SPA iframe 으로 로드되므로
 *       X-Frame-Options 는 sameOrigin. 실제 데이터는 아래 API 로만 조회된다.</li>
 *   <li>{@code /qr-api/**} — QR 스캔 공개 조회(휴대폰 비로그인 접근).</li>
 * </ul>
 *
 * <p>{@code /kims-api/**} 는 여기서 매칭하지 않는다. core 메인 체인의
 * {@code anyRequest().authenticated()} 가 적용되어 플랫폼 JWT 로 보호되고,
 * 각 컨트롤러의 {@code @PreAuthorize} 로 역할까지 확인된다.
 */
@Configuration
public class KimsSecurityConfig {

    @Bean
    @Order(-2)
    public SecurityFilterChain kimsPageSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/kims/**", "/qr-api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
