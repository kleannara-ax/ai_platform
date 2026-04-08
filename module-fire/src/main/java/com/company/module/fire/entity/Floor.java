package com.company.module.fire.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 층 마스터 엔티티
 * 테이블명: floor
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "floor")
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FLOOR_ID")
    private Long floorId;

    @Column(name = "FLOOR_NAME", nullable = false, length = 100)
    private String floorName;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Builder
    public Floor(String floorName, int sortOrder) {
        this.floorName = floorName;
        this.sortOrder = sortOrder;
    }
}
