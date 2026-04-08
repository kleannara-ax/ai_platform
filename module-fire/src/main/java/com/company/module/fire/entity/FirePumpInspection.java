package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 소방펌프 점검 이력 엔티티
 * 테이블명: fire_pump_inspection
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_pump_inspection")
public class FirePumpInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INSPECTION_ID")
    private Long inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PUMP_ID", nullable = false)
    private FirePump pump;

    @Column(name = "INSPECTION_DATE", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "INSPECTION_TIME")
    private LocalTime inspectionTime;

    @Column(name = "INSPECTION_STATUS", nullable = false, length = 30)
    private String inspectionStatus;

    @Lob
    @Column(name = "CHECKLIST_JSON", columnDefinition = "LONGTEXT")
    private String checklistJson;

    @Column(name = "IMAGE_PATH", length = 600)
    private String imagePath;

    @Column(name = "NOTE", length = 1000)
    private String note;

    @Column(name = "PUMP_OPERATION_STATUS", length = 30)
    private String pumpOperationStatus;

    @Column(name = "PANEL_STATUS", length = 30)
    private String panelStatus;

    @Column(name = "WATER_SUPPLY_STATUS", length = 30)
    private String waterSupplyStatus;

    @Column(name = "FUEL_STATUS", length = 30)
    private String fuelStatus;

    @Column(name = "DRAIN_PUMP_STATUS", length = 30)
    private String drainPumpStatus;

    @Column(name = "PIPING_STATUS", length = 30)
    private String pipingStatus;

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
    public FirePumpInspection(FirePump pump, LocalDate inspectionDate, LocalTime inspectionTime, String inspectionStatus,
                              String checklistJson, String imagePath, String note,
                              String pumpOperationStatus, String panelStatus, String waterSupplyStatus,
                              String fuelStatus, String drainPumpStatus, String pipingStatus,
                              Long inspectedByUserId, String inspectedByName) {
        this.pump = pump;
        this.inspectionDate = inspectionDate;
        this.inspectionTime = inspectionTime;
        this.inspectionStatus = inspectionStatus;
        this.checklistJson = checklistJson;
        this.imagePath = imagePath;
        this.note = note;
        this.pumpOperationStatus = pumpOperationStatus;
        this.panelStatus = panelStatus;
        this.waterSupplyStatus = waterSupplyStatus;
        this.fuelStatus = fuelStatus;
        this.drainPumpStatus = drainPumpStatus;
        this.pipingStatus = pipingStatus;
        this.inspectedByUserId = inspectedByUserId;
        this.inspectedByName = inspectedByName;
    }

    public void updateImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void updateInspection(LocalDate inspectionDate, LocalTime inspectionTime, String inspectionStatus, String checklistJson,
                                 String note, String inspectedByName, String pumpOperationStatus, String panelStatus,
                                 String waterSupplyStatus, String fuelStatus, String drainPumpStatus, String pipingStatus) {
        this.inspectionDate = inspectionDate;
        this.inspectionTime = inspectionTime;
        this.inspectionStatus = inspectionStatus;
        this.checklistJson = checklistJson;
        this.note = note;
        this.pumpOperationStatus = pumpOperationStatus;
        this.panelStatus = panelStatus;
        this.waterSupplyStatus = waterSupplyStatus;
        this.fuelStatus = fuelStatus;
        this.drainPumpStatus = drainPumpStatus;
        this.pipingStatus = pipingStatus;
        if (inspectedByName != null && !inspectedByName.isBlank()) {
            this.inspectedByName = inspectedByName.trim();
        }
    }
}
