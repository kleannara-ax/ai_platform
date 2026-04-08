package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건물 마스터 엔티티
 * <p>
 * 기존 ASP.NET: Building (BuildingId, BuildingName, IsActive)
 * 테이블명: building
 *
 * NOTE: boolean 필드명을 'active'로 선언 → Lombok @Getter 충돌 방지.
 *       DB 컬럼은 @Column(name="is_active") 명시 매핑.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "building")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "building_name", nullable = false, length = 200)
    private String buildingName;

    /**
     * 활성 여부
     * NOTE: 필드명 'active' → DB 컬럼 'is_active' 명시 매핑
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder
    public Building(String buildingName, boolean isActive) {
        this.buildingName = buildingName;
        this.active = isActive;
    }

    /** 기존 코드 호환 편의 메서드 */
    public boolean isActive() {
        return this.active;
    }
}
