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
 * <p>
 * 기존 ASP.NET: FireHydrant
 * - HydrantType: Indoor(옥내) / Outdoor(옥외)
 * - OperationType: Auto(자동) / Manual(수동)
 * - 옥내: BuildingId/FloorId/X/Y 사용
 * - 옥외: LocationDescription 사용 (BuildingId=99)
 *
 * 테이블명: fire_hydrant
 *
 * NOTE: boolean 필드명을 'active'로 선언하여 Lombok @Getter 충돌(isIsActive 이중생성)을 방지한다.
 *       DB 컬럼은 @Column(name="is_active")으로 명시 매핑.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "fire_hydrant")
public class FireHydrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hydrant_id")
    private Long hydrantId;

    /** QR용 일련번호 (HYD-000001 형식) */
    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    private String serialNumber;

    /** 소화전 타입: Indoor / Outdoor */
    @Column(name = "hydrant_type", nullable = false, length = 20)
    private String hydrantType;

    /** 작동 방식: Auto / Manual */
    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    /** 건물 (옥외인 경우 id=99 사용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    /** 층 (옥외인 경우 floor_id=1 사용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    /** 도면 X 좌표 (소수점 2자리) */
    @Column(name = "x", precision = 5, scale = 2)
    private BigDecimal x;

    /** 도면 Y 좌표 (소수점 2자리) */
    @Column(name = "y", precision = 5, scale = 2)
    private BigDecimal y;

    /** 위치 설명 (옥외 소화전) */
    @Column(name = "location_description", length = 200)
    private String locationDescription;

    /** 소화전 이미지 경로 */
    @Column(name = "image_path", length = 600)
    private String imagePath;

    @Column(name = "qr_key", nullable = false, unique = true, length = 100)
    private String qrKey;

    /**
     * 활성 여부
     * NOTE: 필드명 'active' → DB 컬럼 'is_active' 명시 매핑 (Lombok 이중 getter 방지)
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 점검 이력 (1:N, Cascade Delete) */
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

    // ===== 편의 메서드 (기존 코드 호환) =====

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
