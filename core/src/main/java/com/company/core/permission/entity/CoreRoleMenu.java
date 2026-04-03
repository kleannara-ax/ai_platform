package com.company.core.permission.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CORE_ROLE_MENU")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class CoreRoleMenu {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROLE_MENU_ID") private Long roleMenuId;
    @Column(name = "ROLE", nullable = false, length = 30) private String role;
    @Column(name = "MENU_ID", nullable = false) private Long menuId;
    @Column(name = "CREATED_AT", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
