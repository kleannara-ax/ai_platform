package com.company.module.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 부서 엔티티 (업무 모듈)
 * 테이블 Prefix: MOD_USER_
 */
@Entity
@Table(name = "MOD_USER_DEPARTMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DEPT_ID")
    @Comment("부서 ID (PK)")
    private Long deptId;

    @Column(name = "DEPT_NAME", nullable = false, length = 100)
    @Comment("부서명")
    private String deptName;

    @Column(name = "DEPT_CODE", nullable = false, length = 20, unique = true)
    @Comment("부서 코드")
    private String deptCode;

    @Column(name = "PARENT_DEPT_ID")
    @Comment("상위 부서 ID")
    private Long parentDeptId;

    @Column(name = "SORT_ORDER")
    @Comment("정렬 순서")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "ENABLED", nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    @Comment("생성일시")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    @Comment("수정일시")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── 비즈니스 메서드 ──

    public void updateInfo(String deptName, Long parentDeptId, Integer sortOrder) {
        this.deptName = deptName;
        this.parentDeptId = parentDeptId;
        this.sortOrder = sortOrder;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}
