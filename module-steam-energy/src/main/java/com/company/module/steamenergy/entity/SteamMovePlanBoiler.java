package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 이동계획 보일러별 기준값 — 연·월·보일러별 한 행. 비운 칸은 화면이 기본값(최근 실적·단가 입력)을 쓴다.
 *
 * <p>화면: 스팀에너지관리 › 이동계획 수립 (energy-move-plan.html) — 보일러 기준값 영역
 * <p>Table: steam_move_plan_boiler
 * <p>PK: MOVE_PLAN_BOILER_ID, UNIQUE: (YEAR_NO, MONTH_NO, BOILER_CODE)
 */
@Entity
@Table(name = "steam_move_plan_boiler")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanBoiler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MOVE_PLAN_BOILER_ID")
    private Long movePlanBoilerId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "BOILER_CODE", length = 20, nullable = false)
    private String boilerCode;

    @Column(name = "USE_YN", length = 1)
    private String useYn;

    @Column(name = "MAX_TON_DAY", precision = 18, scale = 6)
    private BigDecimal maxTonDay;

    @Column(name = "MIN_TON_DAY", precision = 18, scale = 6)
    private BigDecimal minTonDay;

    @Column(name = "STEAM_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal steamUnitPrice;

    @Column(name = "IDLE_HOUR_PRICE", precision = 18, scale = 6)
    private BigDecimal idleHourPrice;

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
    public void update(String useYn, BigDecimal maxTonDay, BigDecimal minTonDay, BigDecimal steamUnitPrice, BigDecimal idleHourPrice, Long updatedBy) {
        this.useYn = useYn;
        this.maxTonDay = maxTonDay;
        this.minTonDay = minTonDay;
        this.steamUnitPrice = steamUnitPrice;
        this.idleHourPrice = idleHourPrice;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 일괄 저장에서 목록에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, updatedBy);
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
