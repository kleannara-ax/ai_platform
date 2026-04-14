package com.company.core.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 사용자-역할 매핑 엔티티 (다중 역할 지원)
 */
@Entity
@Table(name = "core_user_role",
       uniqueConstraints = @UniqueConstraint(name = "UK_core_user_role", columnNames = {"USER_ID", "ROLE"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoreUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ROLE_ID")
    private Long userRoleId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "ROLE", nullable = false, length = 30)
    private String role;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
