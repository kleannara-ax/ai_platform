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
@Table(name = "fire_receiver_inspection")
public class FireReceiverInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private FireReceiver receiver;

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

    @Column(name = "power_status", length = 30)
    private String powerStatus;

    @Column(name = "switch_status", length = 30)
    private String switchStatus;

    @Column(name = "transfer_device_status", length = 30)
    private String transferDeviceStatus;

    @Column(name = "zone_map_status", length = 30)
    private String zoneMapStatus;

    @Column(name = "continuity_test_status", length = 30)
    private String continuityTestStatus;

    @Column(name = "operation_test_status", length = 30)
    private String operationTestStatus;

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
    public FireReceiverInspection(FireReceiver receiver, LocalDate inspectionDate, LocalTime inspectionTime, String inspectionStatus,
                                  String checklistJson, String imagePath, String note,
                                  String powerStatus, String switchStatus, String transferDeviceStatus,
                                  String zoneMapStatus, String continuityTestStatus, String operationTestStatus,
                                  Long inspectedByUserId, String inspectedByName) {
        this.receiver = receiver;
        this.inspectionDate = inspectionDate;
        this.inspectionTime = inspectionTime;
        this.inspectionStatus = inspectionStatus;
        this.checklistJson = checklistJson;
        this.imagePath = imagePath;
        this.note = note;
        this.powerStatus = powerStatus;
        this.switchStatus = switchStatus;
        this.transferDeviceStatus = transferDeviceStatus;
        this.zoneMapStatus = zoneMapStatus;
        this.continuityTestStatus = continuityTestStatus;
        this.operationTestStatus = operationTestStatus;
        this.inspectedByUserId = inspectedByUserId;
        this.inspectedByName = inspectedByName;
    }

    public void updateImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void updateInspection(LocalDate inspectionDate, LocalTime inspectionTime, String inspectionStatus, String checklistJson,
                                 String note, String inspectedByName, String powerStatus, String switchStatus,
                                 String transferDeviceStatus, String zoneMapStatus, String continuityTestStatus,
                                 String operationTestStatus) {
        this.inspectionDate = inspectionDate;
        this.inspectionTime = inspectionTime;
        this.inspectionStatus = inspectionStatus;
        this.checklistJson = checklistJson;
        this.note = note;
        this.powerStatus = powerStatus;
        this.switchStatus = switchStatus;
        this.transferDeviceStatus = transferDeviceStatus;
        this.zoneMapStatus = zoneMapStatus;
        this.continuityTestStatus = continuityTestStatus;
        this.operationTestStatus = operationTestStatus;
        if (inspectedByName != null && !inspectedByName.isBlank()) {
            this.inspectedByName = inspectedByName.trim();
        }
    }
}
