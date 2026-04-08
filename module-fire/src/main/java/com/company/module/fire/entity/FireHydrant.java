package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 소화전 엔티티
 * 테이블명: fire_hydrant
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_hydrant")
public class FireHydrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HYDRANT_ID")
    private Long hydrantId;

    @Column(name = "SERIAL_NUMBER", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @Column(name = "HYDRANT_TYPE", nullable = false, length = 20)
    private String hydrantType;

    @Column(name = "OPERATION_TYPE", nullable = false, length = 20)
    private String operationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BUILDING_ID")
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FLOOR_ID")
    private Floor floor;

    @Column(name = "X", precision = 5, scale = 2)
    private BigDecimal x;

    @Column(name = "Y", precision = 5, scale = 2)
    private BigDecimal y;

    @Column(name = "LOCATION_DESCRIPTION", length = 200)
    private String locationDescription;

    @Column(name = "IMAGE_PATH", length = 600)
    private String imagePath;

    @Column(name = "QR_KEY", nullable = false, unique = true, length = 100)
    private String qrKey;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "hydrant", fetch = FetchType.LAZY,
               cascade = CascadeType.REMOVE, orphanRemoval = true)
    @OrderBy("inspectionDate DESC, inspectionId DESC")
    private List<FireHydrantInspection> inspections = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.qrKey == null) {
            this.qrKey = java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    @Builder
    public FireHydrant(String serialNumber, String hydrantType, String operationType,
                       Building building, Floor floor, BigDecimal x, BigDecimal y,
                       String locationDescription, String imagePath, boolean isActive) {
        this.serialNumber = serialNumber;
        this.hydrantType = hydrantType;
        this.operationType = operationType;
        this.building = building;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.locationDescription = locationDescription;
        this.imagePath = imagePath;
        this.active = isActive;
        this.qrKey = java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public boolean isActive() {
        return this.active;
    }

    // ===== 비즈니스 메서드 =====

    public void update(String operationType, Building building, Floor floor,
                       BigDecimal x, BigDecimal y, String locationDescription) {
        this.operationType = operationType;
        this.building = building;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.locationDescription = locationDescription;
    }

    public void updateImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void assignQrKey(String qrKey) {
        if (qrKey != null && !qrKey.isBlank()) {
            this.qrKey = qrKey;
        }
    }

    public boolean isOutdoor() {
        return "Outdoor".equalsIgnoreCase(this.hydrantType);
    }
}
