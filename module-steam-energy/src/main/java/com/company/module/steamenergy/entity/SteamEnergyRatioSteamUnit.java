package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 스팀사용량 원단위(기타 포함) — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 연료비 배분비율 › 종합 원단위 17번째 표 (energy-fuel-ratio.html t17)
 * <p>Table: steam_energy_ratio_steam_unit
 * <p>PK: RATIO_STEAM_UNIT_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_ratio_steam_unit")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioSteamUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RATIO_STEAM_UNIT_ID")
    private Long ratioSteamUnitId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "UNIT_PM2", precision = 18, scale = 6)
    private BigDecimal unitPm2;

    @Column(name = "UNIT_PM3", precision = 18, scale = 6)
    private BigDecimal unitPm3;

    @Column(name = "UNIT_PAPER_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal unitPaperSubtotal;

    @Column(name = "UNIT_TM3", precision = 18, scale = 6)
    private BigDecimal unitTm3;

    @Column(name = "UNIT_TM4", precision = 18, scale = 6)
    private BigDecimal unitTm4;

    @Column(name = "UNIT_TM5", precision = 18, scale = 6)
    private BigDecimal unitTm5;

    @Column(name = "UNIT_TISSUE_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal unitTissueSubtotal;

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
    public void update(BigDecimal unitPm2, BigDecimal unitPm3, BigDecimal unitPaperSubtotal, BigDecimal unitTm3, BigDecimal unitTm4, BigDecimal unitTm5, BigDecimal unitTissueSubtotal, Long updatedBy) {
        this.unitPm2 = unitPm2;
        this.unitPm3 = unitPm3;
        this.unitPaperSubtotal = unitPaperSubtotal;
        this.unitTm3 = unitTm3;
        this.unitTm4 = unitTm4;
        this.unitTm5 = unitTm5;
        this.unitTissueSubtotal = unitTissueSubtotal;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, updatedBy);
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
