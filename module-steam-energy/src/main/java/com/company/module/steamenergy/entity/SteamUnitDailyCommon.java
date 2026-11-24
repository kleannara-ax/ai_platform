package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 원단위 일지 — 호기에 속하지 않는 일자별 공통 계측값
 *
 * <p>화면: 스팀사용 원단위, 스팀 사용량, 오늘 일지 등록
 * <p>디스퍼져 스팀량 총량 / TOC 스팀량(환경관리) / PICA-121 Vent
 *
 * <p>Table: steam_unit_daily_common
 * <p>PK: UNIT_COMMON_ID, UNIQUE: USAGE_DATE
 */
@Entity
@Table(name = "steam_unit_daily_common")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamUnitDailyCommon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UNIT_COMMON_ID")
    private Long unitCommonId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "DISPERSER_TOTAL", precision = 18, scale = 6)
    private BigDecimal disperserTotal;

    @Column(name = "TOC_STEAM", precision = 18, scale = 6)
    private BigDecimal tocSteam;

    @Column(name = "PICA121_VENT", precision = 18, scale = 6)
    private BigDecimal pica121Vent;

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
    public void update(BigDecimal disperserTotal, BigDecimal tocSteam, BigDecimal pica121Vent, Long updatedBy) {
        this.disperserTotal = disperserTotal;
        this.tocSteam = tocSteam;
        this.pica121Vent = pica121Vent;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 날. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, updatedBy);
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
