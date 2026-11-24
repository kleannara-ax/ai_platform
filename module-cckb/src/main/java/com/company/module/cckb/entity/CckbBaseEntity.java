package com.company.module.cckb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * CCKB 후면 색상검사 모듈의 모든 테이블이 공통으로 가지는 감사(Audit) 컬럼.
 * <p>{@code @MappedSuperclass} 이므로 이 클래스는 테이블이 되지 않고,
 * 이를 상속한 엔티티의 테이블에 아래 컬럼으로 포함된다.
 *
 * <p>※ module-safety 의 BaseTimeEntity 와 달리 CREATED_BY/UPDATED_BY/DELETED_BY 는
 * {@code Long} 타입이다. CCKB 엑셀 데이터테이블 설계(스프링부트 표준 반영본)에서
 * 해당 컬럼들이 "core 사용자 ID 값 (core 사용자 ID, FK 아님)" 즉 BIGINT 로 정의되어 있어
 * 이를 그대로 따른다 (safety 모듈은 로그인 ID 문자열을 쓰지만, CCKB 스키마는 숫자 ID 기준).
 *
 * <ul>
 *   <li>CREATED_AT / CREATED_BY - 최초 생성 시각/생성자(사용자ID)</li>
 *   <li>UPDATED_AT / UPDATED_BY - 마지막 수정 시각/수정자(사용자ID)</li>
 *   <li>DELETED_YN / DELETED_AT / DELETED_BY - 소프트 삭제 여부/시각/삭제자(사용자ID)
 *       (물리 삭제(DELETE) 금지, 항상 DELETED_YN='Y' 처리로만 삭제한다)</li>
 * </ul>
 *
 * <p>createdBy/updatedBy/deletedBy 값은 {@code @Setter} 없이,
 * 하위 엔티티가 자신의 비즈니스 메서드(update/delete/restore) 안에서
 * 아래 protected 메서드를 호출해 채운다.
 */
@Getter
@MappedSuperclass
public abstract class CckbBaseEntity {

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 생성자 (core 사용자 ID, FK 아님) */
    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** 수정자 (core 사용자 ID, FK 아님) */
    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn = "N";

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    /** 삭제자 (core 사용자 ID, FK 아님) */
    @Column(name = "DELETED_BY")
    private Long deletedBy;

    /** 저장(INSERT) 직전 자동 호출 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
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
    protected void markCreatedBy(Long userId) {
        this.createdBy = userId;
        this.updatedBy = userId;
    }

    /** 수정자를 기록한다. update() 계열 비즈니스 메서드 안에서 호출한다. */
    protected void markUpdatedBy(Long userId) {
        this.updatedBy = userId;
    }

    /** 소프트 삭제 처리. delete() 비즈니스 메서드 안에서 호출한다. */
    protected void markDeleted(Long userId) {
        this.deletedYn = "Y";
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = userId;
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
