package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 이동계획 일자별 운휴 — 일자별 한 행. 생산설비 운휴시간과 보일러 정비 운휴시간을 날짜별로 고른다 (하루 종일 = 24).
 *
 * <p>화면: 에너지 회계비용 › 이동계획 수립 (energy-move-plan.html) — 일별 계획 표
 * <p>Table: steam_move_plan_day
 * <p>PK: MOVE_PLAN_DAY_ID, UNIQUE: (PLAN_DATE)
 */
@Entity
@Table(name = "steam_move_plan_day")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MOVE_PLAN_DAY_ID")
    private Long movePlanDayId;

    @Column(name = "PLAN_DATE", nullable = false)
    private LocalDate planDate;

    @Column(name = "DOWN_PM2", precision = 6, scale = 2)
    private BigDecimal downPm2;

    @Column(name = "DOWN_PM3", precision = 6, scale = 2)
    private BigDecimal downPm3;

    @Column(name = "DOWN_TM3", precision = 6, scale = 2)
    private BigDecimal downTm3;

    @Column(name = "DOWN_TM4", precision = 6, scale = 2)
    private BigDecimal downTm4;

    @Column(name = "DOWN_TM5", precision = 6, scale = 2)
    private BigDecimal downTm5;

    @Column(name = "STOP_WASTE1", precision = 6, scale = 2)
    private BigDecimal stopWaste1;

    @Column(name = "STOP_WASTE2", precision = 6, scale = 2)
    private BigDecimal stopWaste2;

    @Column(name = "STOP_COMBO", precision = 6, scale = 2)
    private BigDecimal stopCombo;

    @Column(name = "STOP_FLUID", precision = 6, scale = 2)
    private BigDecimal stopFluid;

    @Column(name = "STOP_LNG20", precision = 6, scale = 2)
    private BigDecimal stopLng20;

    @Column(name = "STOP_LNG50", precision = 6, scale = 2)
    private BigDecimal stopLng50;

    @Column(name = "NOTE", length = 500)
    private String note;

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
    public void update(BigDecimal downPm2, BigDecimal downPm3, BigDecimal downTm3, BigDecimal downTm4, BigDecimal downTm5, BigDecimal stopWaste1, BigDecimal stopWaste2, BigDecimal stopCombo, BigDecimal stopFluid, BigDecimal stopLng20, BigDecimal stopLng50, String note, Long updatedBy) {
        this.downPm2 = downPm2;
        this.downPm3 = downPm3;
        this.downTm3 = downTm3;
        this.downTm4 = downTm4;
        this.downTm5 = downTm5;
        this.stopWaste1 = stopWaste1;
        this.stopWaste2 = stopWaste2;
        this.stopCombo = stopCombo;
        this.stopFluid = stopFluid;
        this.stopLng20 = stopLng20;
        this.stopLng50 = stopLng50;
        this.note = note;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 일괄 저장에서 목록에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
