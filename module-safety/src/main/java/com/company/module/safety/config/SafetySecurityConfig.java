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
 *
 * <h2>업무 모듈 표준과의 차이 — core 정리 대기</h2>
 * 업무 모듈 개발 표준은 SecurityConfig 생성을 금지하고 보안 설정을 core 가 관리하도록 한다.
 * 실제로 {@code module-fire} 와 {@code module-ps-insp} 는 자기 설정 없이 core 의
 * {@code SecurityConfig} 공개 경로 목록({@code /fire/**}, {@code /fire-api/.../files/**},
 * {@code /ps-insp-api/health} 등)에 등록해 쓴다.
 *
 * <p>이 클래스는 그 방식으로 옮겨야 하지만, 옮기려면 core 를 고쳐야 해서 남겨 둔다.
 * 지금 지우면 {@code /safety/**} 가 core 의 {@code anyRequest().authenticated()} 에 걸려
 * 화면이 401 로 뜨지 않는다(iframe 진입은 Authorization 헤더를 보낼 수 없다).
 *
 * <p>core 담당자가 아래 두 줄을 core {@code SecurityConfig} 의 기존 공개 경로 목록에 추가하면
 * 이 클래스를 통째로 지울 수 있다. core 도 이미 {@code frameOptions(sameOrigin)} 을 쓴다.
 * <pre>
 * .requestMatchers("/safety/**").permitAll()                 // 화면(iframe 로드)
 * .requestMatchers("/safety-api/photos/*&#47;view").permitAll() // &lt;img&gt; 는 토큰 헤더를 못 보냄
 * </pre>
 * 자세한 내용은 {@code sql/module-safety/README.md} 의 "core 에 추가로 필요한 기능" 참고.
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
