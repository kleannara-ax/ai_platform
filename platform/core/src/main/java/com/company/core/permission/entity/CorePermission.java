package com.company.core.permission.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CORE_PERMISSION")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class CorePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PERM_ID") private Long permId;
    @Column(name = "PERM_CODE", nullable = false, length = 50) private String permCode;
    @Column(name = "PERM_NAME", nullable = false, length = 100) private String permName;
    @Column(name = "DESCRIPTION", length = 200) private String description;
    @Column(name = "IS_ACTIVE") @Builder.Default private Boolean isActive = true;
    @Column(name = "CREATED_AT", updatable = false) private LocalDateTime createdAt;
    @Column(name = "UPDATED_AT") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public void update(String permName, String description, Boolean isActive) {
        this.permName = permName; this.description = description; this.isActive = isActive;
    }
}
