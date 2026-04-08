package com.company.module.code.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "code_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CodeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GROUP_ID")
    private Long groupId;

    @Column(name = "GROUP_CODE", nullable = false, length = 50, unique = true)
    private String groupCode;

    @Column(name = "GROUP_NAME", nullable = false, length = 100)
    private String groupName;

    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    @Column(name = "IS_ACTIVE")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "SORT_ORDER")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<CodeDetail> details = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String groupName, String description, Boolean isActive, Integer sortOrder) {
        this.groupName = groupName;
        this.description = description;
        if (isActive != null) this.isActive = isActive;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }
}
