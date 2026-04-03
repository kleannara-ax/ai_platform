package com.company.core.common.transaction;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 공통 트랜잭션 정책 설정
 *
 * <p>트랜잭션 정책:
 * <ul>
 *   <li>Service 레이어에서 @Transactional 사용</li>
 *   <li>읽기 전용 메서드: @Transactional(readOnly = true)</li>
 *   <li>CUD 메서드: @Transactional (기본 REQUIRED)</li>
 *   <li>RuntimeException 발생 시 자동 롤백 (Spring 기본)</li>
 *   <li>Checked Exception은 롤백하지 않음 (필요 시 rollbackFor 지정)</li>
 * </ul>
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * @Service
 * @Transactional(readOnly = true)  // 클래스 레벨: 기본 읽기 전용
 * public class SomeService {
 *
 *     public SomeEntity findById(Long id) {
 *         // readOnly = true 적용
 *     }
 *
 *     @Transactional  // 메서드 레벨: 쓰기 트랜잭션으로 오버라이드
 *     public SomeEntity create(CreateRequest request) {
 *         // readOnly = false, REQUIRED 전파
 *     }
 * }
 * }
 * </pre>
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    // 트랜잭션 매니저는 Spring Boot Auto-Configuration에 의해 자동 등록
    // JpaTransactionManager가 기본 등록됨
}
