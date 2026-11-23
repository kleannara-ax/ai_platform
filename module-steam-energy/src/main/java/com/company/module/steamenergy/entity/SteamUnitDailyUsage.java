package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 원단위 일지 — 호기별 일일 스팀 사용·생산 실적
 *
 * <p>화면: 스팀사용 원단위(unit-steam-unit), 스팀 사용량(unit-steam-usage), 오늘 일지 등록(today-entry), 호기별 요약
 * <p>호기(MACHINE_CODE): PM-2, PM-3, TM-3, TM-4, TM-5
 * <p>스팀량 합·원단위(톤/hr) 등 계산값은 저장하지 않고 화면에서 계산한다.
 *
 * <p>Table: steam_unit_daily_usage
 * <p>PK: UNIT_USAGE_ID (BIGINT, AUTO_INCREMENT)
 * <p>UNIQUE: (USAGE_DATE, MACHINE_CODE)
 */
@Entity
@Table(name = "steam_unit_daily_usage")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamUnitDailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UNIT_USAGE_ID")
    private Long unitUsageId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "MACHINE_CODE", nullable = false, length = 20)
    private String machineCode;

    @Column(name = "MAIN_STEAM", precision = 18, scale = 6)
    private BigDecimal mainSteam;

    @Column(name = "COATER_STEAM", precision = 18, scale = 6)
    private BigDecimal coaterSteam;

    @Column(name = "VENTILATION_STEAM", precision = 18, scale = 6)
    private BigDecimal ventilationSteam;

    @Column(name = "STEAM", precision = 18, scale = 6)
    private BigDecimal steam;

    @Column(name = "PRODUCTION_KG", precision = 18, scale = 6)
    private BigDecimal productionKg;

    @Column(name = "PRODUCTION_TYPE", length = 50)
    private String productionType;

    @Column(name = "RUNTIME_HOURS", precision = 12, scale = 4)
    private BigDecimal runtimeHours;

    @Column(name = "DISPERSER_RUNTIME_HOURS", precision = 12, scale = 4)
    private BigDecimal disperserRuntimeHours;

    @Column(name = "VENT_RUNTIME_HOURS", precision = 12, scale = 4)
    private BigDecimal ventRuntimeHours;

    @Column(name = "NOTE", length = 500)
    private String note;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // ── 규격 공통 컬럼 (감사/소프트 삭제) ─────────────────────────
    // CREATED_BY/UPDATED_BY/DELETED_BY 는 core 의 CurrentUserProvider 가 제공되면 Service 에서 채운다.
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

    /** 행 전체 값 갱신 — 화면은 한 행(일자·호기)의 모든 항목을 함께 보낸다. null 은 빈 값으로 저장한다. */
    public void update(BigDecimal mainSteam, BigDecimal coaterSteam, BigDecimal ventilationSteam, BigDecimal steam,
                       BigDecimal productionKg, String productionType,
                       BigDecimal runtimeHours, BigDecimal disperserRuntimeHours, BigDecimal ventRuntimeHours,
                       String note, Long updatedBy) {
        this.mainSteam = mainSteam;
        this.coaterSteam = coaterSteam;
        this.ventilationSteam = ventilationSteam;
        this.steam = steam;
        this.productionKg = productionKg;
        this.productionType = productionType;
        this.runtimeHours = runtimeHours;
        this.disperserRuntimeHours = disperserRuntimeHours;
        this.ventRuntimeHours = ventRuntimeHours;
        this.note = note;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 날 (행은 남긴다). */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, updatedBy);
    }

    /** 소프트 삭제 — 물리 삭제하지 않고 DELETED_YN 을 'Y' 로 바꾼다. */
    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now(KST);
    }

    /** 삭제 취소 — 같은 일자·호기를 다시 저장할 때 되살린다. */
    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
