package com.company.module.cckb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * CCKB 전용 보안 체인.
 *
 * <p>core 를 수정하지 않고, 모듈이 자기 경로에 대한 독립 SecurityFilterChain 을 기여한다.
 * (module-safety / module-kims 의 {@code /safety/**}, {@code /kims/**} 체인과 같은 방식,
 * {@code @Order(-2)} 로 core 의 메인 체인보다 먼저 매칭)
 *
 * <ul>
 *   <li>{@code /cckb/**} — 화면(정적 HTML/JS). 플랫폼 SPA 가 iframe 으로 로드하므로
 *       X-Frame-Options 는 sameOrigin. iframe 최초 진입은 Authorization 헤더를 보낼 수 없어
 *       정적 페이지 자체는 공개해야 한다. 실제 데이터는 아래 API 로만 조회된다.</li>
 * </ul>
 *
 * <p>{@code /cckb-api/**} 는 여기서 매칭하지 않는다 — core 메인 체인의
 * {@code anyRequest().authenticated()} 가 그대로 적용되어 플랫폼 JWT 로 보호된다.
 * ★ 이미지 다운로드(/cckb-api/images/files/**) 도 예외 없이 인증을 요구한다
 *   (dailyreport ImageController 와 동일한 방침 — 사용자 명시 지시).
 *   프론트엔드는 &lt;img src="..."&gt; 가 아니라 인증된 fetch()+blob 을 사용해야 한다.
 *
 * <p>이 파일은 module-cckb 내부 파일이므로 "모듈 격리 원칙 보류" 질문과 무관하게
 * 즉시 적용 가능하다 (settings.gradle/app/build.gradle/app index.html 과 달리
 * 플랫폼 공용 파일을 건드리지 않음).
 */
@Configuration
public class CckbSecurityConfig {

    @Bean
    @Order(-2)
    public SecurityFilterChain cckbPageSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/cckb/**")
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
