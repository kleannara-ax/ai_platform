package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 이동계획 호기별 생산계획 — 연·월·호기별 한 행. 생산계획 엑셀(생산계획 시트)을 올리거나 화면에서 직접 넣는다.
 *
 * <p>화면: 스팀에너지관리 › 이동계획 수립 (energy-move-plan.html) — 생산계획 영역
 * <p>Table: steam_move_plan_machine
 * <p>PK: MOVE_PLAN_MACHINE_ID, UNIQUE: (YEAR_NO, MONTH_NO, MACHINE_CODE)
 */
@Entity
@Table(name = "steam_move_plan_machine")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanMachine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MOVE_PLAN_MACHINE_ID")
    private Long movePlanMachineId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "MACHINE_CODE", length = 20, nullable = false)
    private String machineCode;

    @Column(name = "PRODUCTION_TON", precision = 18, scale = 6)
    private BigDecimal productionTon;

    @Column(name = "RUNTIME_HOURS", precision = 12, scale = 4)
    private BigDecimal runtimeHours;

    @Column(name = "PLAN_KIND", length = 10)
    private String planKind;

    @Column(name = "UNIT_OVERRIDE", precision = 18, scale = 6)
    private BigDecimal unitOverride;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(KST);
        if (this.deletedYn == null) this.deletedYn = "N";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(KST);
    }

    /** 행 전체 값 갱신. null 은 빈 값으로 저장한다. */
    public void update(BigDecimal productionTon, BigDecimal runtimeHours, String planKind, BigDecimal unitOverride, Long updatedBy) {
        this.productionTon = productionTon;
        this.runtimeHours = runtimeHours;
        this.planKind = planKind;
        this.unitOverride = unitOverride;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 일괄 저장에서 목록에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, updatedBy);
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now(KST);
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
