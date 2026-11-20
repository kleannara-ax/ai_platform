package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 사고·정비 이력 — 연도별 · 기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 사고이력 · SRF Boiler invoice › 사고이력 · 폐합성수지 소각로 › 사고이력 (unit-fluidized-accident.html, srf-boiler-invoice-accident.html, incinerator-accident.html)
 * <p>Table: steam_incident
 * <p>PK: INCIDENT_ID, UNIQUE: (YEAR_NO, RECORD_KEY)
 */
@Entity
@Table(name = "steam_incident")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INCIDENT_ID")
    private Long incidentId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "RECORD_KEY", length = 80, nullable = false)
    private String recordKey;

    @Column(name = "FACILITY", length = 20)
    private String facility;

    @Column(name = "KIND", length = 20)
    private String kind;

    @Column(name = "OCCUR_DATE", length = 100)
    private String occurDate;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "ACTION_TAKEN", length = 1000)
    private String actionTaken;

    @Column(name = "DOWNTIME_HOURS", length = 100)
    private String downtimeHours;

    @Column(name = "LOSS_AMOUNT", length = 100)
    private String lossAmount;

    @Column(name = "NOTE", length = 500)
    private String note;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

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
    public void update(String facility, String kind, String occurDate, String reason, String actionTaken, String downtimeHours, String lossAmount, String note, Integer sortOrder, Long updatedBy) {
        this.facility = facility;
        this.kind = kind;
        this.occurDate = occurDate;
        this.reason = reason;
        this.actionTaken = actionTaken;
        this.downtimeHours = downtimeHours;
        this.lossAmount = lossAmount;
        this.note = note;
        this.sortOrder = sortOrder;
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
