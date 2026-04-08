package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_pump")
public class FirePump {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pump_id")
    private Long pumpId;

    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @Column(name = "building_name", nullable = false, length = 200)
    private String buildingName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(name = "x", precision = 5, scale = 2)
    private BigDecimal x;

    @Column(name = "y", precision = 5, scale = 2)
    private BigDecimal y;

    @Column(name = "location_description", length = 200)
    private String locationDescription;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "qr_key", nullable = false, unique = true, length = 100)
    private String qrKey;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.qrKey == null) {
            this.qrKey = java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    @Builder
    public FirePump(String serialNumber, String buildingName, Floor floor,
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
