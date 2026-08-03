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
@Table(name = "facility_water_consumption")
public class FacilityWaterConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONSUMPTION_ID")
    private Long consumptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EQUIPMENT_ID", nullable = false)
    private FacilityEquipment equipment;

    @Column(name = "CONSUMPTION_DATE", nullable = false)
    private LocalDate consumptionDate;

    @Column(name = "BOTTLE_COUNT", nullable = false)
    private int bottleCount;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Builder
    public FacilityWaterConsumption(FacilityEquipment equipment, LocalDate consumptionDate, int bottleCount) {
        this.equipment = equipment;
        this.consumptionDate = consumptionDate;
        this.bottleCount = bottleCount;
    }

    public void update(LocalDate consumptionDate, int bottleCount) {
        this.consumptionDate = consumptionDate;
        this.bottleCount = bottleCount;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
