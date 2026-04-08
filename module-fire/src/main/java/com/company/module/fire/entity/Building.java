package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건물 마스터 엔티티
 * 테이블명: building
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "building")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BUILDING_ID")
    private Long buildingId;

    @Column(name = "BUILDING_NAME", nullable = false, length = 200)
    private String buildingName;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;

    @Builder
    public Building(String buildingName, boolean isActive) {
        this.buildingName = buildingName;
        this.active = isActive;
    }

    public boolean isActive() {
        return this.active;
    }
}
