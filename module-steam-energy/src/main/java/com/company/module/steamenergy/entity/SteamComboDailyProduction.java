package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 복합보일러 스팀생산실적 — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 복합보일러 › 스팀생산실적 (combo-boiler-steam-production.html)
 * <p>Table: steam_combo_daily_production
 * <p>PK: COMBO_PRODUCTION_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_combo_daily_production")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboDailyProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMBO_PRODUCTION_ID")
    private Long comboProductionId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "COMBO_STEAM", precision = 18, scale = 6)
    private BigDecimal comboSteam;

    @Column(name = "COMBO_CONDENSATE", precision = 18, scale = 6)
    private BigDecimal comboCondensate;

    @Column(name = "COMBO_MAKEUP", precision = 18, scale = 6)
    private BigDecimal comboMakeup;

    @Column(name = "COMBO_DEAERATOR", precision = 18, scale = 6)
    private BigDecimal comboDeaerator;

    @Column(name = "COMBO_PROCESS", precision = 18, scale = 6)
    private BigDecimal comboProcess;

    @Column(name = "COMBO_DILUTION", precision = 18, scale = 6)
    private BigDecimal comboDilution;

    @Column(name = "EXTERNAL_STEAM", precision = 18, scale = 6)
    private BigDecimal externalSteam;

    @Column(name = "EXTERNAL_CONDENSATE", precision = 18, scale = 6)
    private BigDecimal externalCondensate;

    @Column(name = "EXTERNAL_MAKEUP", precision = 18, scale = 6)
    private BigDecimal externalMakeup;

    @Column(name = "WASTE_STEAM1", precision = 18, scale = 6)
    private BigDecimal wasteSteam1;

    @Column(name = "WASTE_STEAM2", precision = 18, scale = 6)
    private BigDecimal wasteSteam2;

    @Column(name = "KN20", precision = 18, scale = 6)
    private BigDecimal kn20;

    @Column(name = "KN50", precision = 18, scale = 6)
    private BigDecimal kn50;

    @Column(name = "FLUIDIZED_STEAM", precision = 18, scale = 6)
    private BigDecimal fluidizedSteam;

    @Column(name = "FLUIDIZED_CONDENSATE", precision = 18, scale = 6)
    private BigDecimal fluidizedCondensate;

    @Column(name = "RUNTIME20", precision = 18, scale = 6)
    private BigDecimal runtime20;

    @Column(name = "RUNTIME50", precision = 18, scale = 6)
    private BigDecimal runtime50;

    @Column(name = "RUNTIME_FLUIDIZED", precision = 18, scale = 6)
    private BigDecimal runtimeFluidized;

    /** 폐합성소각로 기관실유량계 2호 스팀량(T/D) — 참고자료, 합계 제외 */
    @Column(name = "WASTE_ROOM_STEAM2", precision = 18, scale = 6)
    private BigDecimal wasteRoomSteam2;

    /** 유동상 보일러 총스팀량 FI-301 (T/D) — 참고자료, 합계 제외 */
    @Column(name = "FLUIDIZED_TOTAL_STEAM", precision = 18, scale = 6)
    private BigDecimal fluidizedTotalSteam;

    /** 유동상 보일러 응축수 FI-303 — 참고자료, 합계 제외 */
    @Column(name = "FLUIDIZED_TOTAL_CONDENSATE", precision = 18, scale = 6)
    private BigDecimal fluidizedTotalCondensate;

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
    public void update(BigDecimal comboSteam, BigDecimal comboCondensate, BigDecimal comboMakeup, BigDecimal comboDeaerator, BigDecimal comboProcess, BigDecimal comboDilution, BigDecimal externalSteam, BigDecimal externalCondensate, BigDecimal externalMakeup, BigDecimal wasteSteam1, BigDecimal wasteSteam2, BigDecimal kn20, BigDecimal kn50, BigDecimal fluidizedSteam, BigDecimal fluidizedCondensate, BigDecimal runtime20, BigDecimal runtime50, BigDecimal runtimeFluidized, BigDecimal wasteRoomSteam2, BigDecimal fluidizedTotalSteam, BigDecimal fluidizedTotalCondensate, Long updatedBy) {
        this.comboSteam = comboSteam;
        this.comboCondensate = comboCondensate;
        this.comboMakeup = comboMakeup;
        this.comboDeaerator = comboDeaerator;
        this.comboProcess = comboProcess;
        this.comboDilution = comboDilution;
        this.externalSteam = externalSteam;
        this.externalCondensate = externalCondensate;
        this.externalMakeup = externalMakeup;
        this.wasteSteam1 = wasteSteam1;
        this.wasteSteam2 = wasteSteam2;
        this.kn20 = kn20;
        this.kn50 = kn50;
        this.fluidizedSteam = fluidizedSteam;
        this.fluidizedCondensate = fluidizedCondensate;
        this.runtime20 = runtime20;
        this.runtime50 = runtime50;
        this.runtimeFluidized = runtimeFluidized;
        this.wasteRoomSteam2 = wasteRoomSteam2;
        this.fluidizedTotalSteam = fluidizedTotalSteam;
        this.fluidizedTotalCondensate = fluidizedTotalCondensate;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
