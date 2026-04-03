package com.company.core.config;

import org.springframework.context.annotation.Configuration;

/**
 * JPA 공통 설정
 *
 * <p>@EntityScan, @EnableJpaRepositories는
 * App 모듈의 PlatformApplication에서 통합 관리한다.
 * → 모듈 간 중복 스캔에 의한 Bean 충돌 방지
 */
@Configuration
public class JpaConfig {
    // JPA 기본 설정은 application.yml에서 처리
    // EntityManagerFactory, TransactionManager는 Spring Boot Auto-Configuration
}
