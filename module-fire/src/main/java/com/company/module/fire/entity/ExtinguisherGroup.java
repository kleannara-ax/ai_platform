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
 * 소화기 그룹 (도면 위치 단위 마커)
 * <p>
 * 기존 ASP.NET: ExtinguisherGroup (GroupId, BuildingId, FloorId, X, Y, Note, CreatedAt)
 * 테이블명: extinguisher_group
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "extinguisher_group")
public class ExtinguisherGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    /** 도면 X 좌표 (소수점 4자리) */
    @Column(name = "x", precision = 9, scale = 4)
    private BigDecimal x;

    /** 도면 Y 좌표 (소수점 4자리) */
    @Column(name = "y", precision = 9, scale = 4)
    private BigDecimal y;

    @Column(name = "note", length = 400)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private List<Extinguisher> extinguishers = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public ExtinguisherGroup(Building building, Floor floor,
                              BigDecimal x, BigDecimal y, String note) {
        this.building = building;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.note = note;
    }

    public void updateCoordinates(BigDecimal x, BigDecimal y) {
        this.x = x;
        this.y = y;
    }
}
