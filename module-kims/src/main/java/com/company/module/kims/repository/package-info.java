/**
 * Spring Data JPA Repository 계층.
 * <p>{@code JpaRepository<Entity, Long>} 를 상속하며, 필요 시
 * {@code @Query(nativeQuery = true)} 를 사용한다.
 * <p>core_user 등 타 모듈 테이블 참조가 필요하면 {@code EntityManager} 네이티브 쿼리를 사용한다.
 */
package com.company.module.kims.repository;
