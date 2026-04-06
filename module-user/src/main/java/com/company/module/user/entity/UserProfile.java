package com.company.module.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 프로필 엔티티 (업무 모듈)
 * Core의 CoreUser와 userId로 연관
 * 테이블 Prefix: MOD_USER_
 */
@Entity
@Table(name = "MOD_USER_PROFILE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROFILE_ID")
    @Comment("프로필 ID (PK)")
    private Long profileId;

    @Column(name = "USER_ID", nullable = false, unique = true)
    @Comment("사용자 ID (CORE_USER 참조)")
    private Long userId;

    @Column(name = "DEPT_CODE", length = 50)
    @Comment("부서 코드 (공통코드 DEPT 참조)")
    private String deptCode;

    @Column(name = "POSITION", length = 50)
    @Comment("직위")
    private String position;

    @Column(name = "JOB_TITLE", length = 100)
    @Comment("직책")
    private String jobTitle;

    @Column(name = "EMPLOYEE_NO", length = 20)
    @Comment("사번")
    private String employeeNo;

    @Column(name = "JOIN_DATE")
    @Comment("입사일")
    private LocalDate joinDate;

    @Column(name = "OFFICE_PHONE", length = 20)
    @Comment("사무실 전화번호")
    private String officePhone;

    @Column(name = "INTERNAL_EXT", length = 10)
    @Comment("내선번호")
    private String internalExt;

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

    public void updateProfile(String deptCode, String position, String jobTitle,
                              String officePhone, String internalExt) {
        this.deptCode = deptCode;
        this.position = position;
        this.jobTitle = jobTitle;
        this.officePhone = officePhone;
        this.internalExt = internalExt;
    }

    public void updateExtended(String employeeNo, LocalDate joinDate) {
        this.employeeNo = employeeNo;
        this.joinDate = joinDate;
    }
}
