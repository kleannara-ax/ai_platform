package com.company.core.permission.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CORE_ROLE_PERMISSION")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class CoreRolePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROLE_PERM_ID") private Long rolePermId;
    @Column(name = "ROLE", nullable = false, length = 30) private String role;
    @Column(name = "PERM_ID", nullable = false) private Long permId;
    @Column(name = "CREATED_AT", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
