/**
 * JPA Entity 계층.
 * <ul>
 *   <li>{@code @Table(name = "소문자_snake_case")} / {@code @Column(name = "UPPER_SNAKE_CASE")}</li>
 *   <li>Lombok: {@code @Getter}, {@code @NoArgsConstructor(access = PROTECTED)}, {@code @Builder}</li>
 *   <li>{@code @PrePersist} 로 createdAt 자동 설정, setter 대신 비즈니스 메서드 사용</li>
 * </ul>
 */
package com.company.module.kims.entity;
