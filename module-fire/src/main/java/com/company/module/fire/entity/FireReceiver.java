package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 수신기 엔티티
 * 테이블명: fire_receiver
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_receiver")
public class FireReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RECEIVER_ID")
    private Long receiverId;

    @Column(name = "SERIAL_NUMBER", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @Column(name = "BUILDING_NAME", nullable = false, length = 200)
    private String buildingName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FLOOR_ID", nullable = false)
    private Floor floor;

    @Column(name = "X", precision = 5, scale = 2)
    private BigDecimal x;

    @Column(name = "Y", precision = 5, scale = 2)
    private BigDecimal y;

    @Column(name = "LOCATION_DESCRIPTION", length = 200)
    private String locationDescription;

    @Column(name = "NOTE", length = 500)
    private String note;

    @Column(name = "QR_KEY", nullable = false, unique = true, length = 100)
    private String qrKey;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.qrKey == null) {
            this.qrKey = java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    @Builder
    public FireReceiver(String serialNumber, String buildingName, Floor floor,
                        BigDecimal x, BigDecimal y, String locationDescription,
                        String note, boolean isActive) {
        this.serialNumber = serialNumber;
        this.buildingName = buildingName;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.locationDescription = locationDescription;
        this.note = note;
        this.active = isActive;
        this.qrKey = java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public boolean isActive() {
        return active;
    }

    public void assignQrKey(String qrKey) {
        this.qrKey = qrKey;
    }

    public void update(String buildingName, Floor floor, BigDecimal x, BigDecimal y,
                       String locationDescription, String note) {
        this.buildingName = buildingName;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.locationDescription = locationDescription;
        this.note = note;
    }
}
