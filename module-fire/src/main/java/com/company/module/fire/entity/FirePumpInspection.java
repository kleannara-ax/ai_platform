package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_pump_inspection")
public class FirePumpInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pump_id", nullable = false)
    private FirePump pump;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "inspection_time")
    private LocalTime inspectionTime;

    @Column(name = "inspection_status", nullable = false, length = 30)
    private String inspectionStatus;

    @Lob
    @Column(name = "checklist_json", columnDefinition = "LONGTEXT")
    private String checklistJson;

    @Column(name = "image_path", length = 600)
    private String imagePath;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "pump_operation_status", length = 30)
    private String pumpOperationStatus;

    @Column(name = "panel_status", length = 30)
    private String panelStatus;

    @Column(name = "water_supply_status", length = 30)
    private String waterSupplyStatus;

    @Column(name = "fuel_status", length = 30)
    private String fuelStatus;

    @Column(name = "drain_pump_status", length = 30)
    private String drainPumpStatus;

    @Column(name = "piping_status", length = 30)
    private String pipingStatus;

    @Column(name = "inspected_by_user_id")
    private Long inspectedByUserId;

    @Column(name = "inspected_by_name", length = 200)
    private String inspectedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
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
