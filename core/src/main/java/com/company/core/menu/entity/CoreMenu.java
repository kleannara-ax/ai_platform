package com.company.core.menu.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CORE_MENU")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class CoreMenu {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MENU_ID") private Long menuId;
    @Column(name = "MENU_NAME", nullable = false, length = 100) private String menuName;
    @Column(name = "MENU_CODE", nullable = false, length = 50) private String menuCode;
    @Column(name = "PARENT_ID") private Long parentId;
    @Column(name = "MENU_URL", length = 255) private String menuUrl;
    @Column(name = "ICON", length = 50) private String icon;
    @Column(name = "SORT_ORDER") @Builder.Default private Integer sortOrder = 0;
    @Column(name = "MENU_TYPE", length = 20) @Builder.Default private String menuType = "MENU";
    @Column(name = "IS_VISIBLE") @Builder.Default private Boolean isVisible = true;
    @Column(name = "IS_ACTIVE") @Builder.Default private Boolean isActive = true;
    @Column(name = "DESCRIPTION", length = 200) private String description;
    @Column(name = "ALLOWED_IPS", length = 1000) private String allowedIps;
    @Column(name = "CREATED_AT", updatable = false) private LocalDateTime createdAt;
    @Column(name = "UPDATED_AT") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public void update(String menuName, String menuUrl, String icon, Integer sortOrder,
                       String menuType, Boolean isVisible, Boolean isActive, String description,
                       Long parentId, String allowedIps) {
        this.menuName = menuName; this.menuUrl = menuUrl; this.icon = icon;
        this.sortOrder = sortOrder; this.menuType = menuType; this.isVisible = isVisible;
        this.isActive = isActive; this.description = description; this.parentId = parentId;
        this.allowedIps = allowedIps;
    }
}
