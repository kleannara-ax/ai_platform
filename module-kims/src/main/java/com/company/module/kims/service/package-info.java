/**
 * 비즈니스 로직 계층.
 * <p>{@code @Service}, {@code @RequiredArgsConstructor} 사용.
 * <p>{@code @Transactional(readOnly = true)} 를 기본으로 하고, 쓰기 메서드에만
 * {@code @Transactional} 을 별도 지정한다.
 */
package com.company.module.kims.service;
