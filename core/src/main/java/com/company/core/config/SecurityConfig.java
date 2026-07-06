package com.company.core.config;

import com.company.core.security.JwtAccessDeniedHandler;
import com.company.core.security.JwtAuthenticationEntryPoint;
import com.company.core.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 공통 보안 필터 체인 설정
 *
 * <p>정책:
 * <ul>
 *   <li>JWT 기반 Stateless 인증</li>
 *   <li>CSRF 비활성화 (REST API + JWT이므로)</li>
 *   <li>세션 미사용 (STATELESS)</li>
 *   <li>인증 예외: /api/auth/** (로그인, 토큰 갱신)</li>
 *   <li>그 외 모든 API는 인증 필요</li>
 *   <li>@PreAuthorize 기반 메서드 레벨 권한 체크</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // @PreAuthorize, @Secured 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /**
     * 소방/기타설비 독립 대시보드 정적 파일 전용 체인.
     *
     * <p>메인 보안 체인의 authorizeHttpRequests().permitAll()은 JWT 필터 자체는 계속 실행한다.
     * 운영 환경에서 만료/잘못된 Authorization 헤더 또는 정적 리소스 매칭 차이로
     * 독립 대시보드 HTML이 401을 반환하는 경우가 있어, 해당 정적 파일은 JWT 필터를
     * 등록하지 않은 선순위 체인에서 처리한다. 실제 데이터 API는 아래 메인 체인을 탄다.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain staticDashboardSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(
                    "/fire-dashboard.html",
                    "/facility-dashboard.html",
                    "/css/equipment-dashboard.css",
                    "/js/equipment-dashboard.js"
            )
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 비활성화 (Stateless REST API)
            .csrf(AbstractHttpConfigurer::disable)

            // X-Frame-Options: 같은 도메인 iframe 허용 (소방 모듈 SPA 내 임베딩)
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )

            // 세션 미사용
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 예외 처리
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )

            // URL 기반 접근 제어
            .authorizeHttpRequests(auth -> auth
                // 인증 없이 접근 가능한 URL
                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/csrf").permitAll()
                .requestMatchers("/api/login/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // 정적 리소스 (HTML, JS, CSS, 이미지 등)
                .requestMatchers("/", "/index.html", "/sso-callback.html", "/favicon.ico", "/static/**").permitAll()
                .requestMatchers("/images/**", "/js/**", "/css/**", "/uploads/**").permitAll()
                .requestMatchers("/account/**").permitAll()
                // 소방 모듈 정적 리소스 및 메뉴 URL (/fire/**)
                .requestMatchers("/fire/**").permitAll()
                .requestMatchers("/fire-map.html", "/fire-dashboard.html", "/facility-dashboard.html").permitAll()
                .requestMatchers("/extinguishers.html", "/sprinklers.html", "/hydrants.html", "/receivers.html", "/pumps.html").permitAll()
                .requestMatchers("/maps/**", "/qr/**", "/minspection/**").permitAll()
                // 기타설비 페이지는 SPA iframe 및 직접 URL 접근이 가능해야 하므로 공개
                // API 본문(/facility-api/**)은 아래 파일 조회 예외를 제외하고 인증 유지
                .requestMatchers("/facility-map.html", "/facility/**").permitAll()
                .requestMatchers("/login.html").permitAll()
                .requestMatchers("/fire-api/qr/image").permitAll()
                // 소방 모듈 건물/층 목록 - 드롭다운에서 사용, 토큰 만료 시에도 전체 목록 표시
                .requestMatchers("/fire-api/qr/buildings", "/fire-api/qr/floors").permitAll()
                .requestMatchers("/fire-api/maps/building-floors").permitAll()
                .requestMatchers("/fire-api/maps/floor-data").permitAll()
                // 소방 모듈 파일(사진) 조회 - img 태그에서 JWT 헤더 전송 불가하므로 공개
                .requestMatchers("/fire-api/extinguishers/files/**").permitAll()
                .requestMatchers("/fire-api/hydrants/files/**").permitAll()
                .requestMatchers("/fire-api/pumps/files/**").permitAll()
                .requestMatchers("/fire-api/receivers/files/**").permitAll()
                .requestMatchers("/fire-api/sprinklers/files/**").permitAll()
                .requestMatchers("/fire-api/minspection/files/**").permitAll()
                .requestMatchers("/facility-api/air-conditioners/files/**", "/facility-api/water-purifiers/files/**").permitAll()
                // PS-INSP 모듈: 헬스체크·페이지(iframe)·정적리소스만 공개
                // API(/ps-insp-api/inspections/**, /ps-insp-api/mes/**)는 인증+메뉴접근권한 필요
                .requestMatchers("/ps-insp-api/health").permitAll()
                .requestMatchers("/ps-insp-api/page", "/ps-insp-api/page/**").permitAll()
                .requestMatchers("/ps-insp/**").permitAll()
                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )

            // JWT 인증 필터 등록
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
