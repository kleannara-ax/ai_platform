package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 회계비용 마감비교 비고 — 연도별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 월별 회계비용 마감 vs 실마감 차이 — 비고 (energy-month-compare.html)
 * <p>Table: steam_energy_month_compare_note
 * <p>PK: MONTH_COMPARE_NOTE_ID, UNIQUE: YEAR_NO
 */
@Entity
@Table(name = "steam_energy_month_compare_note")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyMonthCompareNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MONTH_COMPARE_NOTE_ID")
    private Long monthCompareNoteId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "NOTE_FUEL", length = 500)
    private String noteFuel;

    @Column(name = "NOTE_DEDUCT", length = 500)
    private String noteDeduct;

    @Column(name = "NOTE_ACTUAL", length = 500)
    private String noteActual;

    @Column(name = "NOTE_STEAM_RATIO", length = 500)
    private String noteSteamRatio;

    @Column(name = "NOTE_PLAN", length = 500)
    private String notePlan;

    @Column(name = "NOTE_PROD", length = 500)
    private String noteProd;

    @Column(name = "NOTE_PRODUCT", length = 500)
    private String noteProduct;

    @Column(name = "NOTE_UNIT", length = 500)
    private String noteUnit;

    @Column(name = "NOTE_PROFIT", length = 500)
    private String noteProfit;

    @Column(name = "NOTE_AUX", length = 500)
    private String noteAux;

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
    public void update(String noteFuel, String noteDeduct, String noteActual, String noteSteamRatio, String notePlan, String noteProd, String noteProduct, String noteUnit, String noteProfit, String noteAux, Long updatedBy) {
        this.noteFuel = noteFuel;
        this.noteDeduct = noteDeduct;
        this.noteActual = noteActual;
        this.noteSteamRatio = noteSteamRatio;
        this.notePlan = notePlan;
        this.noteProd = noteProd;
        this.noteProduct = noteProduct;
        this.noteUnit = noteUnit;
        this.noteProfit = noteProfit;
        this.noteAux = noteAux;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, updatedBy);
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
