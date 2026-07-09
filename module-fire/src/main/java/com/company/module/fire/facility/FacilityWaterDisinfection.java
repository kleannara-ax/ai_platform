package com.company.module.fire.facility;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "facility_water_disinfection")
public class FacilityWaterDisinfection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DISINFECTION_ID")
    private Long disinfectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EQUIPMENT_ID", nullable = false)
    private FacilityEquipment equipment;

    @Column(name = "DISINFECTION_DATE", nullable = false)
    private LocalDate disinfectionDate;

    @Column(name = "WORKER_NAME", nullable = false, length = 100)
    private String workerName;

    @Column(name = "NOTE", length = 500)
    private String note;

    @Column(name = "PHOTO_PATH", nullable = false, length = 600)
    private String photoPath;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Builder
    public FacilityWaterDisinfection(FacilityEquipment equipment, LocalDate disinfectionDate, String workerName,
                                     String note, String photoPath) {
        this.equipment = equipment;
        this.disinfectionDate = disinfectionDate;
        this.workerName = workerName;
        this.note = note;
        this.photoPath = photoPath;
    }
}
