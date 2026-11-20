package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 복합보일러 운휴·디스퍼져 — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 복합보일러 › 운휴시간·디스퍼져 (combo-boiler-downtime.html)
 * <p>Table: steam_combo_daily_downtime
 * <p>PK: COMBO_DOWNTIME_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_combo_daily_downtime")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboDailyDowntime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMBO_DOWNTIME_ID")
    private Long comboDowntimeId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "CHANGE_COMBO", length = 500)
    private String changeCombo;

    @Column(name = "CHANGE_JESCO", length = 500)
    private String changeJesco;

    @Column(name = "DOWN1", precision = 18, scale = 6)
    private BigDecimal down1;

    @Column(name = "DOWN2", precision = 18, scale = 6)
    private BigDecimal down2;

    @Column(name = "DISPERSER_STEAM", precision = 18, scale = 6)
    private BigDecimal disperserSteam;

    @Column(name = "RUNTIME_MIN", precision = 18, scale = 6)
    private BigDecimal runtimeMin;

    @Column(name = "VENT_PRODUCTION", precision = 18, scale = 6)
    private BigDecimal ventProduction;

    @Column(name = "VENT_INTERNAL", precision = 18, scale = 6)
    private BigDecimal ventInternal;

    @Column(name = "VENT_USAGE", precision = 18, scale = 6)
    private BigDecimal ventUsage;

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
    public void update(String changeCombo, String changeJesco, BigDecimal down1, BigDecimal down2, BigDecimal disperserSteam, BigDecimal runtimeMin, BigDecimal ventProduction, BigDecimal ventInternal, BigDecimal ventUsage, Long updatedBy) {
        this.changeCombo = changeCombo;
        this.changeJesco = changeJesco;
        this.down1 = down1;
        this.down2 = down2;
        this.disperserSteam = disperserSteam;
        this.runtimeMin = runtimeMin;
        this.ventProduction = ventProduction;
        this.ventInternal = ventInternal;
        this.ventUsage = ventUsage;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, updatedBy);
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
