package com.company.module.fire.facility;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "facility_equipment_inspection",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_FACILITY_INSPECTION_DATE", columnNames = {"EQUIPMENT_ID", "INSPECTION_DATE"})
        })
public class FacilityEquipmentInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INSPECTION_ID")
    private Long inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EQUIPMENT_ID", nullable = false)
    private FacilityEquipment equipment;

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
        createdAt = LocalDateTime.now();
    }

    @Builder
    public FacilityEquipmentInspection(FacilityEquipment equipment, LocalDate inspectionDate,
                                       boolean isFaulty, String faultReason,
                                       Long inspectedByUserId, String inspectedByName) {
        this.equipment = equipment;
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
