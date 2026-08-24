package com.company.module.kims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 모든 엔티티가 공통으로 가지는 생성/수정 시각 필드.
 * <p>{@code @MappedSuperclass} 이므로 이 클래스는 테이블이 되지 않고,
 * 이를 상속한 엔티티의 테이블에 CREATED_AT / UPDATED_AT 컬럼으로 포함된다.
 *
 * <ul>
 *   <li>{@link #onCreate()} - INSERT 직전 호출되어 생성/수정 시각을 현재 시각으로 설정</li>
 *   <li>{@link #onUpdate()} - UPDATE 직전 호출되어 수정 시각만 갱신</li>
 * </ul>
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    /** 저장(INSERT) 직전 자동 호출 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** 수정(UPDATE) 직전 자동 호출 */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
