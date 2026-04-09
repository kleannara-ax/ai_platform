package com.company.core.config;

import com.company.core.security.JwtAccessDeniedHandler;
import com.company.core.security.JwtAuthenticationEntryPoint;
import com.company.core.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 비활성화 (Stateless REST API)
            .csrf(AbstractHttpConfigurer::disable)

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
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // 정적 리소스 (HTML, JS, CSS, 이미지 등)
                .requestMatchers("/", "/index.html", "/favicon.ico", "/static/**").permitAll()
                .requestMatchers("/images/**", "/js/**", "/css/**", "/uploads/**").permitAll()
                .requestMatchers("/account/**").permitAll()
                // 소방 모듈 정적 리소스
                .requestMatchers("/fire-map.html", "/dashboard.html").permitAll()
                .requestMatchers("/extinguishers.html", "/hydrants.html", "/receivers.html", "/pumps.html").permitAll()
                .requestMatchers("/maps/**", "/qr/**", "/minspection/**").permitAll()
                .requestMatchers("/login.html").permitAll()
                .requestMatchers("/fire-api/qr/image").permitAll()
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
