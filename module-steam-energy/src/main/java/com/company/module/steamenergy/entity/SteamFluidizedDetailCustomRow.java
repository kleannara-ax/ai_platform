package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 세부 운영내역 (추가 행) — 연/월별 · 구역 (kne · chemical · waste · power · water · etc)·행 식별자 (화면이 만든 값, 예: r1779432186923216)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 세부 운영내역 — 사용자가 추가한 행 (unit-fluidized-detail.html)
 * <p>Table: steam_fluidized_detail_custom_row
 * <p>PK: DETAIL_CUSTOM_ROW_ID, UNIQUE: (YEAR_NO, MONTH_NO, SECTION, ROW_KEY)
 */
@Entity
@Table(name = "steam_fluidized_detail_custom_row")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailCustomRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DETAIL_CUSTOM_ROW_ID")
    private Long detailCustomRowId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "SECTION", length = 30, nullable = false)
    private String section;

    @Column(name = "ROW_KEY", length = 40, nullable = false)
    private String rowKey;

    @Column(name = "LABEL", length = 200)
    private String label;

    @Column(name = "INSERT_AFTER")
    private Integer insertAfter;

    @Column(name = "DAY01", precision = 18, scale = 6)
    private BigDecimal day01;

    @Column(name = "DAY02", precision = 18, scale = 6)
    private BigDecimal day02;

    @Column(name = "DAY03", precision = 18, scale = 6)
    private BigDecimal day03;

    @Column(name = "DAY04", precision = 18, scale = 6)
    private BigDecimal day04;

    @Column(name = "DAY05", precision = 18, scale = 6)
    private BigDecimal day05;

    @Column(name = "DAY06", precision = 18, scale = 6)
    private BigDecimal day06;

    @Column(name = "DAY07", precision = 18, scale = 6)
    private BigDecimal day07;

    @Column(name = "DAY08", precision = 18, scale = 6)
    private BigDecimal day08;

    @Column(name = "DAY09", precision = 18, scale = 6)
    private BigDecimal day09;

    @Column(name = "DAY10", precision = 18, scale = 6)
    private BigDecimal day10;

    @Column(name = "DAY11", precision = 18, scale = 6)
    private BigDecimal day11;

    @Column(name = "DAY12", precision = 18, scale = 6)
    private BigDecimal day12;

    @Column(name = "DAY13", precision = 18, scale = 6)
    private BigDecimal day13;

    @Column(name = "DAY14", precision = 18, scale = 6)
    private BigDecimal day14;

    @Column(name = "DAY15", precision = 18, scale = 6)
    private BigDecimal day15;

    @Column(name = "DAY16", precision = 18, scale = 6)
    private BigDecimal day16;

    @Column(name = "DAY17", precision = 18, scale = 6)
    private BigDecimal day17;

    @Column(name = "DAY18", precision = 18, scale = 6)
    private BigDecimal day18;

    @Column(name = "DAY19", precision = 18, scale = 6)
    private BigDecimal day19;

    @Column(name = "DAY20", precision = 18, scale = 6)
    private BigDecimal day20;

    @Column(name = "DAY21", precision = 18, scale = 6)
    private BigDecimal day21;

    @Column(name = "DAY22", precision = 18, scale = 6)
    private BigDecimal day22;

    @Column(name = "DAY23", precision = 18, scale = 6)
    private BigDecimal day23;

    @Column(name = "DAY24", precision = 18, scale = 6)
    private BigDecimal day24;

    @Column(name = "DAY25", precision = 18, scale = 6)
    private BigDecimal day25;

    @Column(name = "DAY26", precision = 18, scale = 6)
    private BigDecimal day26;

    @Column(name = "DAY27", precision = 18, scale = 6)
    private BigDecimal day27;

    @Column(name = "DAY28", precision = 18, scale = 6)
    private BigDecimal day28;

    @Column(name = "DAY29", precision = 18, scale = 6)
    private BigDecimal day29;

    @Column(name = "DAY30", precision = 18, scale = 6)
    private BigDecimal day30;

    @Column(name = "DAY31", precision = 18, scale = 6)
    private BigDecimal day31;

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
    public void update(String label, Integer insertAfter, BigDecimal day01, BigDecimal day02, BigDecimal day03, BigDecimal day04, BigDecimal day05, BigDecimal day06, BigDecimal day07, BigDecimal day08, BigDecimal day09, BigDecimal day10, BigDecimal day11, BigDecimal day12, BigDecimal day13, BigDecimal day14, BigDecimal day15, BigDecimal day16, BigDecimal day17, BigDecimal day18, BigDecimal day19, BigDecimal day20, BigDecimal day21, BigDecimal day22, BigDecimal day23, BigDecimal day24, BigDecimal day25, BigDecimal day26, BigDecimal day27, BigDecimal day28, BigDecimal day29, BigDecimal day30, BigDecimal day31, Long updatedBy) {
        this.label = label;
        this.insertAfter = insertAfter;
        this.day01 = day01;
        this.day02 = day02;
        this.day03 = day03;
        this.day04 = day04;
        this.day05 = day05;
        this.day06 = day06;
        this.day07 = day07;
        this.day08 = day08;
        this.day09 = day09;
        this.day10 = day10;
        this.day11 = day11;
        this.day12 = day12;
        this.day13 = day13;
        this.day14 = day14;
        this.day15 = day15;
        this.day16 = day16;
        this.day17 = day17;
        this.day18 = day18;
        this.day19 = day19;
        this.day20 = day20;
        this.day21 = day21;
        this.day22 = day22;
        this.day23 = day23;
        this.day24 = day24;
        this.day25 = day25;
        this.day26 = day26;
        this.day27 = day27;
        this.day28 = day28;
        this.day29 = day29;
        this.day30 = day30;
        this.day31 = day31;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
