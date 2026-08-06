package com.company.core.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 사용자-역할 매핑 엔티티 (다중 역할 지원)
 * 한 사용자(userId)가 여러 역할(role)을 동시에 가질 수 있도록
 * core_user와 별도의 N:M 매핑 테이블로 관리한다.
 */
@Entity
@Table(name = "core_user_role",
       uniqueConstraints = {
           @UniqueConstraint(name = "UK_core_user_role", columnNames = {"USER_ID", "ROLE"})
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoreUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ROLE_ID")
    @Comment("PK")
    private Long userRoleId;

    @Column(name = "USER_ID", nullable = false)
    @Comment("사용자 ID (core_user 참조)")
    private Long userId;

    @Column(name = "ROLE", nullable = false, length = 30)
    @Comment("역할 코드 (ROLE_ADMIN 등)")
    private String role;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    @Comment("생성일시")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
