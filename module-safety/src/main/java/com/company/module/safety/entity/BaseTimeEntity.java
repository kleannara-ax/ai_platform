package com.company.module.safety.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 모든 업무 엔티티가 공통으로 가지는 감사(Audit) 컬럼.
 * <p>{@code @MappedSuperclass} 이므로 이 클래스는 테이블이 되지 않고,
 * 이를 상속한 엔티티의 테이블에 아래 컬럼으로 포함된다.
 *
 * <ul>
 *   <li>CREATED_AT / CREATED_BY - 최초 생성 시각/생성자</li>
 *   <li>UPDATED_AT / UPDATED_BY - 마지막 수정 시각/수정자</li>
 *   <li>DELETED_YN / DELETED_AT / DELETED_BY - 소프트 삭제 여부/시각/삭제자
 *       (물리 삭제(DELETE) 금지, 항상 DELETED_YN='Y' 처리로만 삭제한다)</li>
 * </ul>
 *
 * <p>createdBy/updatedBy/deletedBy 값은 {@code @Setter} 없이,
 * 하위 엔티티가 자신의 비즈니스 메서드(update/delete/restore) 안에서
 * 아래 protected 메서드를 호출해 채운다.
 *
 * <p>module-kims 의 BaseTimeEntity 와 동일한 패턴이다.
 * {@code @MappedSuperclass} 라 모듈 간 공유가 되지 않으므로, 신규 업무모듈 표준에 따라
 * 이 모듈만의 사본을 둔다 (core 에는 두지 않는다 — core 는 User/Auth 전용).
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 생성자 (플랫폼 로그인 ID). core 사용자 테이블 FK 가 아닌 문자열로 보관한다. */
    @Column(name = "CREATED_BY", length = 50, updatable = false)
    private String createdBy;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    /** 수정자 (플랫폼 로그인 ID) */
    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn = "N";

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    /** 삭제자 (플랫폼 로그인 ID) */
    @Column(name = "DELETED_BY", length = 50)
    private String deletedBy;

    /** 저장(INSERT) 직전 자동 호출 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    /** 수정(UPDATE) 직전 자동 호출 */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 생성자를 기록한다. 정적 팩토리 메서드 안에서 호출한다. */
    protected void markCreatedBy(String loginId) {
        this.createdBy = loginId;
        this.updatedBy = loginId;
    }

    /** 수정자를 기록한다. update() 계열 비즈니스 메서드 안에서 호출한다. */
    protected void markUpdatedBy(String loginId) {
        this.updatedBy = loginId;
    }

    /** 소프트 삭제 처리. delete() 비즈니스 메서드 안에서 호출한다. */
    protected void markDeleted(String loginId) {
        this.deletedYn = "Y";
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = loginId;
    }

    /** 소프트 삭제 복구. restore() 비즈니스 메서드 안에서 호출한다. */
    protected void markRestored() {
        this.deletedYn = "N";
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isDeleted() {
        return "Y".equals(this.deletedYn);
    }
}
