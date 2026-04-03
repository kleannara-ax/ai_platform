package com.company.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 플랫폼 메인 애플리케이션
 *
 * <p>Component Scan 전략:
 * <ul>
 *   <li>com.company.core   → Core 모듈 (보안, 인증, 공통 기능)</li>
 *   <li>com.company.module  → 모든 업무 모듈 (module-user, module-xxx...)</li>
 *   <li>com.company.app     → 애플리케이션 자체 설정</li>
 * </ul>
 *
 * <p>새 업무 모듈 추가 시:
 * <ol>
 *   <li>com.company.module.{모듈명} 패키지로 구성</li>
 *   <li>app/build.gradle에 implementation project(':module-xxx') 추가</li>
 *   <li>settings.gradle에 include 'module-xxx' 추가</li>
 *   <li>Core 소스 수정 없이 자동 스캔됨</li>
 * </ol>
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.company.core",     // Core 모듈
        "com.company.module",   // 모든 업무 모듈 (하위 패키지 자동 포함)
        "com.company.app"       // App 모듈
})
@EntityScan(basePackages = {
        "com.company.core",     // Core 엔티티 (CoreUser 등)
        "com.company.module"    // 모든 업무 모듈 엔티티
})
@EnableJpaRepositories(basePackages = {
        "com.company.core",     // Core Repository
        "com.company.module"    // 모든 업무 모듈 Repository
})
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
