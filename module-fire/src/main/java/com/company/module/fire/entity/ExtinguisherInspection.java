package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 소화기 점검 이력 엔티티
 * <p>
 * 기존 ASP.NET: ExtinguisherInspection
 * - Extinguisher와 1:N 관계 (Cascade Delete)
 * - 최근 12건만 유지 (서비스에서 trim 처리)
 *
 * 테이블명: extinguisher_inspection
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "extinguisher_inspection",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_ext_inspection_date",
                            columnNames = {"extinguisher_id", "inspection_date"})
       })
public class ExtinguisherInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extinguisher_id", nullable = false)
    private Extinguisher extinguisher;

    /** 점검일 */
    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    /** 비정상 여부 */
    @Column(name = "is_faulty", nullable = false)
    private boolean isFaulty;

    /** 불량 사유 (isFaulty=true 일 때) */
    @Column(name = "fault_reason", length = 500)
    private String faultReason;

    /** 점검자 ID (WebUser FK) */
    @Column(name = "inspected_by_user_id")
    private Long inspectedByUserId;

    /** 점검자 표시 이름 스냅샷 */
    @Column(name = "inspected_by_name", length = 200)
    private String inspectedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public ExtinguisherInspection(Extinguisher extinguisher, LocalDate inspectionDate,
                                   boolean isFaulty, String faultReason,
                                   Long inspectedByUserId, String inspectedByName) {
        this.extinguisher = extinguisher;
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
