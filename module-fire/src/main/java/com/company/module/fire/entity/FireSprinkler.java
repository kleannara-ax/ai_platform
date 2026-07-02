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
 * 스프링클러 엔티티
 * 테이블명: fire_sprinkler
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_sprinkler")
public class FireSprinkler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SPRINKLER_ID")
    private Long sprinklerId;

    @Column(name = "SERIAL_NUMBER", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BUILDING_ID", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FLOOR_ID", nullable = false)
    private Floor floor;

    @Column(name = "X", precision = 9, scale = 4)
    private BigDecimal x;

    @Column(name = "Y", precision = 9, scale = 4)
    private BigDecimal y;

    @Column(name = "NOTE", length = 500)
    private String note;

    @Column(name = "QR_KEY", unique = true, length = 64)
    private String qrKey;

    @Column(name = "IMAGE_PATH", length = 500)
    private String imagePath;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "sprinkler", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @OrderBy("inspectionDate DESC, inspectionId DESC")
    private List<FireSprinklerInspection> inspections = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public FireSprinkler(String serialNumber, Building building, Floor floor,
                         BigDecimal x, BigDecimal y, String note, String qrKey, String imagePath, boolean isActive) {
        this.serialNumber = serialNumber;
        this.building = building;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.note = note;
        this.qrKey = qrKey;
        this.imagePath = imagePath;
        this.active = isActive;
    }

    public boolean isActive() {
        return active;
    }

    public void update(Building building, Floor floor, BigDecimal x, BigDecimal y, String note) {
        this.building = building;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.note = note;
    }

    public void assignQrKey(String qrKey) {
        this.qrKey = qrKey;
    }

    public void updateImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
