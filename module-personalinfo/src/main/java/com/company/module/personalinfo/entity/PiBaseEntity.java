package com.company.module.personalinfo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 개인정보 위수탁 모듈 업무 테이블 공통 컬럼.
 *
 * <p>모든 업무 테이블은 생성/수정/삭제 이력 컬럼과 소프트 삭제(DELETED_YN) 컬럼을 가진다.
 * 필드는 외부에서 직접 변경할 수 없고, 하위 Entity 의 비즈니스 메서드가
 * {@link #markCreatedBy}, {@link #markUpdatedBy}, {@link #softDelete}, {@link #restoreDeleted}
 * 를 호출해서만 바뀐다. (Setter 미사용)
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PiBaseEntity {

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
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

    protected void softDelete(Long userId) {
        this.deletedYn = "Y";
        this.deletedBy = userId;
        this.deletedAt = LocalDateTime.now();
    }

    protected void restoreDeleted(Long userId) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = userId;
    }

    public boolean isDeleted() {
        return "Y".equals(this.deletedYn);
    }
}
