package com.company.module.code.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CodeDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CODE_ID")
    private Long codeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GROUP_ID", nullable = false)
    private CodeGroup group;

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @Column(name = "CODE_NAME", nullable = false, length = 100)
    private String codeName;

    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    @Column(name = "EXTRA_VALUE1", length = 200)
    private String extraValue1;

    @Column(name = "EXTRA_VALUE2", length = 200)
    private String extraValue2;

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

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String code, String codeName, String description,
                       String extraValue1, String extraValue2,
                       Boolean isActive, Integer sortOrder) {
        this.code = code;
        this.codeName = codeName;
        this.description = description;
        this.extraValue1 = extraValue1;
        this.extraValue2 = extraValue2;
        if (isActive != null) this.isActive = isActive;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }
}
