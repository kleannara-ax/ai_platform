package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 소화전 점검 이력 엔티티
 * 테이블명: fire_hydrant_inspection
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_hydrant_inspection")
public class FireHydrantInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INSPECTION_ID")
    private Long inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HYDRANT_ID", nullable = false)
    private FireHydrant hydrant;

    @Column(name = "INSPECTION_DATE", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "IS_FAULTY", nullable = false)
    private boolean isFaulty;

    @Column(name = "FAULT_REASON", length = 500)
    private String faultReason;

    @Column(name = "INSPECTED_BY_USER_ID")
    private Long inspectedByUserId;

    @Column(name = "INSPECTED_BY_NAME", length = 200)
    private String inspectedByName;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public FireHydrantInspection(FireHydrant hydrant, LocalDate inspectionDate,
                                  boolean isFaulty, String faultReason,
                                  Long inspectedByUserId, String inspectedByName) {
        this.hydrant = hydrant;
        this.inspectionDate = inspectionDate;
        this.isFaulty = isFaulty;
        this.faultReason = isFaulty ? faultReason : null;
        this.inspectedByUserId = inspectedByUserId;
        this.inspectedByName = inspectedByName;
    }

    public void updateInspection(LocalDate inspectionDate, boolean isFaulty, String faultReason, String inspectorName) {
        this.inspectionDate = inspectionDate;
        this.isFaulty = isFaulty;
        this.faultReason = isFaulty ? faultReason : null;
        if (inspectorName != null && !inspectorName.isBlank()) {
            this.inspectedByName = inspectorName.trim();
        }
    }
}
