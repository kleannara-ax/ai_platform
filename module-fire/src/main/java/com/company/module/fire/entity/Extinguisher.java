package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 소화기 엔티티
 * 테이블명: extinguisher
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "extinguisher")
public class Extinguisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EXTINGUISHER_ID")
    private Long extinguisherId;

    @Column(name = "SERIAL_NUMBER", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BUILDING_ID", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FLOOR_ID", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GROUP_ID")
    private ExtinguisherGroup group;

    @Column(name = "EXTINGUISHER_TYPE", nullable = false, length = 100)
    private String extinguisherType;

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

    @Column(name = "NOTE_KEY", nullable = false, unique = true, length = 100)
    private String noteKey;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "extinguisher", fetch = FetchType.LAZY,
               cascade = CascadeType.REMOVE, orphanRemoval = true)
    @OrderBy("inspectionDate DESC, inspectionId DESC")
    private List<ExtinguisherInspection> inspections = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.noteKey == null) {
            this.noteKey = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        calculateReplacementDueDate();
    }

    @PreUpdate
    protected void onUpdate() {
        calculateReplacementDueDate();
    }

    private void calculateReplacementDueDate() {
        if (this.manufactureDate != null && this.replacementCycleYears > 0) {
            this.replacementDueDate = this.manufactureDate.plusYears(this.replacementCycleYears);
        }
    }

    @Builder
    public Extinguisher(String serialNumber, Building building, Floor floor,
                        ExtinguisherGroup group, String extinguisherType,
                        LocalDate manufactureDate, int replacementCycleYears,
                        int quantity, BigDecimal x, BigDecimal y,
                        String imagePath, String note) {
        this.serialNumber = serialNumber;
        this.building = building;
        this.floor = floor;
        this.group = group;
        this.extinguisherType = extinguisherType;
        this.manufactureDate = manufactureDate;
        this.replacementCycleYears = replacementCycleYears > 0 ? replacementCycleYears : 10;
        this.quantity = quantity > 0 ? quantity : 1;
        this.x = x;
        this.y = y;
        this.imagePath = imagePath;
        this.note = note;
        this.noteKey = java.util.UUID.randomUUID().toString().replace("-", "");
        calculateReplacementDueDate();
    }

    // ===== 비즈니스 메서드 =====

    public void update(Building building, Floor floor, ExtinguisherGroup group,
                       String extinguisherType, LocalDate manufactureDate,
                       int replacementCycleYears, int quantity,
                       BigDecimal x, BigDecimal y, String note) {
        this.building = building;
        this.floor = floor;
        this.group = group;
        this.extinguisherType = extinguisherType;
        this.manufactureDate = manufactureDate;
        this.replacementCycleYears = replacementCycleYears > 0 ? replacementCycleYears : 10;
        this.quantity = quantity > 0 ? quantity : 1;
        this.x = x;
        this.y = y;
        this.note = note;
        calculateReplacementDueDate();
    }

    public void updateImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void assignNoteKey(String noteKey) {
        if (noteKey != null && !noteKey.isBlank()) {
            this.noteKey = noteKey;
        }
    }
}
