package com.company.module.autodrawing.config;

import org.springframework.context.annotation.Configuration;

/**
 * module-autodrawing 모듈 설정
 *
 * - ComponentScan: App의 @ComponentScan(com.company.module)에 의해 자동 포함
 * - EntityScan: App의 @EntityScan(com.company.module)에 의해 자동 포함
 * - Repository: App의 @EnableJpaRepositories(com.company.module)에 의해 자동 포함
 */
@Configuration
public class ModuleAutodrawingConfig {
    // 모듈 초기화 설정이 필요한 경우 여기에 추가
}
