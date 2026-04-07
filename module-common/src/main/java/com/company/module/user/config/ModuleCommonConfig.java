package com.company.module.user.config;

import org.springframework.context.annotation.Configuration;

/**
 * module-common 모듈 설정 (사용자 프로필 + 공통코드 관리)
 *
 * <p>Bean 충돌 방지 전략:
 * <ul>
 *   <li>ComponentScan: App의 @ComponentScan(com.company.module)에 의해 자동 포함</li>
 *   <li>EntityScan: App의 @EntityScan(com.company.module)에 의해 자동 포함</li>
 *   <li>Repository: App의 @EnableJpaRepositories(com.company.module)에 의해 자동 포함</li>
 *   <li>각 모듈은 독립된 패키지 네임스페이스를 사용하므로 충돌 없음</li>
 * </ul>
 *
 * <p>모듈별 추가 Bean 설정이 필요한 경우 이 클래스에 정의한다.
 */
@Configuration
public class ModuleCommonConfig {
    // 모듈 초기화 설정이 필요한 경우 여기에 추가
}
