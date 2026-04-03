package com.company.core.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 플랫폼 사용자 엔티티 (Core)
 * 테이블 Prefix: CORE_
 */
@Entity
@Table(name = "CORE_USER",
       uniqueConstraints = {
           @UniqueConstraint(name = "UK_CORE_USER_LOGIN_ID", columnNames = "LOGIN_ID")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoreUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    @Comment("사용자 ID (PK)")
    private Long userId;

    @Column(name = "LOGIN_ID", nullable = false, length = 50)
    @Comment("로그인 ID")
    private String loginId;

    @Column(name = "PASSWORD", nullable = false, length = 255)
    @Comment("비밀번호 (BCrypt)")
    private String password;

    @Column(name = "USER_NAME", nullable = false, length = 100)
    @Comment("사용자명")
    private String userName;

    @Column(name = "EMAIL", length = 200)
    @Comment("이메일")
    private String email;

    @Column(name = "PHONE", length = 20)
    @Comment("전화번호")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 30)
    @Comment("역할 (ROLE_ADMIN, ROLE_MANAGER, ROLE_USER)")
    private Role role;

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

    @Column(name = "CREATED_BY", length = 50)
    @Comment("생성자")
    private String createdBy;

    @Column(name = "UPDATED_BY", length = 50)
    @Comment("수정자")
    private String updatedBy;

    // ── 라이프사이클 콜백 ──

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

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateProfile(String userName, String email, String phone) {
        this.userName = userName;
        this.email = email;
        this.phone = phone;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}
