package com.company.module.fire.facility;

import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.Floor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "facility_equipment",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_FACILITY_EQUIPMENT_SERIAL", columnNames = "SERIAL_NUMBER"),
                @UniqueConstraint(name = "UK_FACILITY_EQUIPMENT_QR_KEY", columnNames = "QR_KEY")
        })
public class FacilityEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EQUIPMENT_ID")
    private Long equipmentId;

    @Column(name = "CATEGORY", nullable = false, length = 30)
    private String category;

    @Column(name = "SERIAL_NUMBER", nullable = false, length = 50)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BUILDING_ID", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FLOOR_ID", nullable = false)
    private Floor floor;

    @Column(name = "EQUIPMENT_TYPE", nullable = false, length = 100)
    private String equipmentType;

    @Column(name = "MANUFACTURER", length = 100)
    private String manufacturer;

    @Column(name = "INSTALLATION_YEAR")
    private Integer installationYear;

    @Column(name = "LOCATION_DESCRIPTION", length = 200)
    private String locationDescription;

    @Column(name = "OUTDOOR_UNIT_COUNT", nullable = false)
    private int outdoorUnitCount = 1;

    @Column(name = "OUTDOOR_X", precision = 9, scale = 4)
    private BigDecimal outdoorX;

    @Column(name = "OUTDOOR_Y", precision = 9, scale = 4)
    private BigDecimal outdoorY;

    @Column(name = "MANUFACTURE_DATE", nullable = false)
    private LocalDate manufactureDate;

    @Column(name = "REPLACEMENT_CYCLE_YEARS", nullable = false)
    private int replacementCycleYears = 10;

    @Column(name = "REPLACEMENT_DUE_DATE")
    private LocalDate replacementDueDate;

    @Column(name = "QUANTITY", nullable = false)
    private int quantity = 1;

    @Column(name = "X", precision = 9, scale = 4)
    private BigDecimal x;

    @Column(name = "Y", precision = 9, scale = 4)
    private BigDecimal y;

    @Column(name = "IMAGE_PATH", length = 600)
    private String imagePath;

    @Column(name = "NOTE", length = 500)
    private String note;

    @Column(name = "QR_KEY", nullable = false, length = 100)
    private String qrKey;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (qrKey == null || qrKey.isBlank()) {
            qrKey = UUID.randomUUID().toString();
        }
        if (quantity < 1) {
            quantity = 1;
        }
        outdoorUnitCount = normalizeOutdoorUnitCount(outdoorUnitCount);
        calculateReplacementDueDate();
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        calculateReplacementDueDate();
    }

    @Builder
    public FacilityEquipment(String category, String serialNumber, Building building, Floor floor,
                             String equipmentType, String manufacturer, Integer installationYear,
                             String locationDescription, int outdoorUnitCount, BigDecimal outdoorX, BigDecimal outdoorY,
                             LocalDate manufactureDate, int replacementCycleYears,
                             int quantity, BigDecimal x, BigDecimal y, String imagePath, String note) {
        this.category = category;
        this.serialNumber = serialNumber;
        this.building = building;
        this.floor = floor;
        this.equipmentType = equipmentType;
        this.manufacturer = manufacturer;
        this.installationYear = installationYear;
        this.locationDescription = locationDescription;
        this.outdoorUnitCount = normalizeOutdoorUnitCount(outdoorUnitCount);
        this.outdoorX = outdoorX;
        this.outdoorY = outdoorY;
        this.manufactureDate = manufactureDate;
        this.replacementCycleYears = replacementCycleYears <= 0 ? 10 : replacementCycleYears;
        this.quantity = quantity <= 0 ? 1 : quantity;
        this.x = x;
        this.y = y;
        this.imagePath = imagePath;
        this.note = note;
        this.qrKey = UUID.randomUUID().toString();
        calculateReplacementDueDate();
    }

    public void update(String serialNumber, Building building, Floor floor, String equipmentType,
                       String manufacturer, Integer installationYear, String locationDescription,
                       int outdoorUnitCount, BigDecimal outdoorX, BigDecimal outdoorY,
                       LocalDate manufactureDate, int replacementCycleYears, int quantity,
                       BigDecimal x, BigDecimal y, String note) {
        this.serialNumber = serialNumber;
        this.building = building;
        this.floor = floor;
        this.equipmentType = equipmentType;
        this.manufacturer = manufacturer;
        this.installationYear = installationYear;
        this.locationDescription = locationDescription;
        this.outdoorUnitCount = normalizeOutdoorUnitCount(outdoorUnitCount);
        this.outdoorX = outdoorX;
        this.outdoorY = outdoorY;
        this.manufactureDate = manufactureDate;
        this.replacementCycleYears = replacementCycleYears <= 0 ? 10 : replacementCycleYears;
        this.quantity = quantity <= 0 ? 1 : quantity;
        this.x = x;
        this.y = y;
        this.note = note;
        calculateReplacementDueDate();
    }

    public void updateImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    private int normalizeOutdoorUnitCount(int value) {
        if (value < 1) return 1;
        return Math.min(value, 2);
    }

    private void calculateReplacementDueDate() {
        if (manufactureDate != null && replacementCycleYears > 0) {
            replacementDueDate = manufactureDate.plusYears(replacementCycleYears);
        }
    }
}
