package com.company.module.epr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * EPR 업무 테이블 공통 감사 컬럼 (규격 10장).
 *
 * <p>{@code @MappedSuperclass} 이므로 테이블이 되지 않고, 상속한 엔티티의 테이블에 아래 컬럼으로 들어간다.
 * CREATED_BY · UPDATED_BY · DELETED_BY 는 플랫폼 사용자 ID 값만 저장한다 (core_user FK 아님).
 * 값은 하위 엔티티의 비즈니스 메서드(생성 · update · delete · restore) 안에서 protected 메서드로만 채운다.
 */
@Getter
@MappedSuperclass
public abstract class EprBaseEntity {

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn = "N";

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    protected void markCreatedBy(Long userId) {
        this.createdBy = userId;
    }

    protected void markUpdatedBy(Long userId) {
        this.updatedBy = userId;
    }

    protected void markDeleted(Long userId) {
        this.deletedYn = "Y";
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = userId;
    }

    protected void markRestored(Long userId) {
        this.deletedYn = "N";
        this.deletedAt = null;
        this.deletedBy = null;
        this.updatedBy = userId;
    }

    public boolean isDeleted() {
        return "Y".equals(this.deletedYn);
    }
}
