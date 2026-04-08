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
 * <p>
 * 기존 ASP.NET: Extinguisher
 * - SerialNumber: DB 시퀀스 자동 생성 (EXT-000001 형식)
 * - ManufactureDate: 제조일
 * - ReplacementCycleYears: 교체 주기 (년)
 * - ReplacementDueDate: 교체 예정일 (DB Computed Column 대신 서비스에서 계산)
 * - NoteKey: QR 조회용 고정 키
 *
 * 테이블명: extinguisher
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "extinguisher")
public class Extinguisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "extinguisher_id")
    private Long extinguisherId;

    /** QR용 일련번호 (EXT-000001 형식) */
    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    /** 도면 위치 그룹 (NULL 허용: 좌표 미지정 대응) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ExtinguisherGroup group;

    /** 소화기 종류 (분말, CO2 등) */
    @Column(name = "extinguisher_type", nullable = false, length = 100)
    private String extinguisherType;

    /** 제조일 */
    @Column(name = "manufacture_date", nullable = false)
    private LocalDate manufactureDate;

    /** 교체 주기 (년, 기본 5년) */
    @Column(name = "replacement_cycle_years", nullable = false)
    private int replacementCycleYears = 10;

    /** 교체 예정일 (제조일 + 교체주기 / 서비스 레이어에서 계산) */
    @Column(name = "replacement_due_date")
    private LocalDate replacementDueDate;

    /** 수량 (기본 1) */
    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    /** 도면 X 좌표 */
    @Column(name = "x", precision = 9, scale = 4)
    private BigDecimal x;

    /** 도면 Y 좌표 */
    @Column(name = "y", precision = 9, scale = 4)
    private BigDecimal y;

    /** 이미지 경로 */
    @Column(name = "image_path", length = 600)
    private String imagePath;

    /** 비고 */
    @Column(name = "note", length = 500)
    private String note;

    /** QR 조회용 고정 키 (UUID) */
    @Column(name = "note_key", nullable = false, unique = true, length = 100)
    private String noteKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 점검 이력 (1:N, Cascade Delete) */
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
