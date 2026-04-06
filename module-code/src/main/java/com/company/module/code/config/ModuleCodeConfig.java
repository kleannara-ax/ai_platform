package com.company.module.code.config;

import org.springframework.context.annotation.Configuration;

/**
 * module-code 모듈 설정 (공통코드 관리)
 *
 * - ComponentScan: App의 @ComponentScan(com.company.module)에 의해 자동 포함
 * - EntityScan: App의 @EntityScan(com.company.module)에 의해 자동 포함
 * - Repository: App의 @EnableJpaRepositories(com.company.module)에 의해 자동 포함
 */
@Configuration
public class ModuleCodeConfig {
}
