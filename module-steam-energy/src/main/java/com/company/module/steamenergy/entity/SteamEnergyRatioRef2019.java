package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 연료비 배분비율(2019 참고) — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 연료비 배분비율 › 2019 참고 표 (energy-fuel-ratio.html t18~t20)
 * <p>Table: steam_energy_ratio_ref2019
 * <p>PK: RATIO_REF2019_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_ratio_ref2019")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioRef2019 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RATIO_REF2019_ID")
    private Long ratioRef2019Id;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "T18_PM1", precision = 18, scale = 6)
    private BigDecimal t18Pm1;

    @Column(name = "T18_PM2", precision = 18, scale = 6)
    private BigDecimal t18Pm2;

    @Column(name = "T18_PM3", precision = 18, scale = 6)
    private BigDecimal t18Pm3;

    @Column(name = "T18_PAPER_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal t18PaperSubtotal;

    @Column(name = "T18_TM1", precision = 18, scale = 6)
    private BigDecimal t18Tm1;

    @Column(name = "T18_TM3", precision = 18, scale = 6)
    private BigDecimal t18Tm3;

    @Column(name = "T18_TM4", precision = 18, scale = 6)
    private BigDecimal t18Tm4;

    @Column(name = "T18_TM5", precision = 18, scale = 6)
    private BigDecimal t18Tm5;

    @Column(name = "T18_TISSUE_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal t18TissueSubtotal;

    @Column(name = "T18_TOTAL", precision = 18, scale = 6)
    private BigDecimal t18Total;

    @Column(name = "T19_PM1", precision = 18, scale = 6)
    private BigDecimal t19Pm1;

    @Column(name = "T19_PM2", precision = 18, scale = 6)
    private BigDecimal t19Pm2;

    @Column(name = "T19_PM3", precision = 18, scale = 6)
    private BigDecimal t19Pm3;

    @Column(name = "T19_PAPER_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal t19PaperSubtotal;

    @Column(name = "T19_TM1", precision = 18, scale = 6)
    private BigDecimal t19Tm1;

    @Column(name = "T19_TM3", precision = 18, scale = 6)
    private BigDecimal t19Tm3;

    @Column(name = "T19_TM4", precision = 18, scale = 6)
    private BigDecimal t19Tm4;

    @Column(name = "T19_TM5", precision = 18, scale = 6)
    private BigDecimal t19Tm5;

    @Column(name = "T19_TISSUE_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal t19TissueSubtotal;

    @Column(name = "T19_TOTAL", precision = 18, scale = 6)
    private BigDecimal t19Total;

    @Column(name = "T20_PM1", precision = 18, scale = 6)
    private BigDecimal t20Pm1;

    @Column(name = "T20_PM2", precision = 18, scale = 6)
    private BigDecimal t20Pm2;

    @Column(name = "T20_PM3", precision = 18, scale = 6)
    private BigDecimal t20Pm3;

    @Column(name = "T20_PAPER_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal t20PaperSubtotal;

    @Column(name = "T20_TM1", precision = 18, scale = 6)
    private BigDecimal t20Tm1;

    @Column(name = "T20_TM3", precision = 18, scale = 6)
    private BigDecimal t20Tm3;

    @Column(name = "T20_TM4", precision = 18, scale = 6)
    private BigDecimal t20Tm4;

    @Column(name = "T20_TM5", precision = 18, scale = 6)
    private BigDecimal t20Tm5;

    @Column(name = "T20_TISSUE_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal t20TissueSubtotal;

    @Column(name = "T20_TOTAL", precision = 18, scale = 6)
    private BigDecimal t20Total;

    @Column(name = "T20_TOTAL_ENERGY_COST", precision = 18, scale = 6)
    private BigDecimal t20TotalEnergyCost;

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
    public void update(BigDecimal t18Pm1, BigDecimal t18Pm2, BigDecimal t18Pm3, BigDecimal t18PaperSubtotal, BigDecimal t18Tm1, BigDecimal t18Tm3, BigDecimal t18Tm4, BigDecimal t18Tm5, BigDecimal t18TissueSubtotal, BigDecimal t18Total, BigDecimal t19Pm1, BigDecimal t19Pm2, BigDecimal t19Pm3, BigDecimal t19PaperSubtotal, BigDecimal t19Tm1, BigDecimal t19Tm3, BigDecimal t19Tm4, BigDecimal t19Tm5, BigDecimal t19TissueSubtotal, BigDecimal t19Total, BigDecimal t20Pm1, BigDecimal t20Pm2, BigDecimal t20Pm3, BigDecimal t20PaperSubtotal, BigDecimal t20Tm1, BigDecimal t20Tm3, BigDecimal t20Tm4, BigDecimal t20Tm5, BigDecimal t20TissueSubtotal, BigDecimal t20Total, BigDecimal t20TotalEnergyCost, Long updatedBy) {
        this.t18Pm1 = t18Pm1;
        this.t18Pm2 = t18Pm2;
        this.t18Pm3 = t18Pm3;
        this.t18PaperSubtotal = t18PaperSubtotal;
        this.t18Tm1 = t18Tm1;
        this.t18Tm3 = t18Tm3;
        this.t18Tm4 = t18Tm4;
        this.t18Tm5 = t18Tm5;
        this.t18TissueSubtotal = t18TissueSubtotal;
        this.t18Total = t18Total;
        this.t19Pm1 = t19Pm1;
        this.t19Pm2 = t19Pm2;
        this.t19Pm3 = t19Pm3;
        this.t19PaperSubtotal = t19PaperSubtotal;
        this.t19Tm1 = t19Tm1;
        this.t19Tm3 = t19Tm3;
        this.t19Tm4 = t19Tm4;
        this.t19Tm5 = t19Tm5;
        this.t19TissueSubtotal = t19TissueSubtotal;
        this.t19Total = t19Total;
        this.t20Pm1 = t20Pm1;
        this.t20Pm2 = t20Pm2;
        this.t20Pm3 = t20Pm3;
        this.t20PaperSubtotal = t20PaperSubtotal;
        this.t20Tm1 = t20Tm1;
        this.t20Tm3 = t20Tm3;
        this.t20Tm4 = t20Tm4;
        this.t20Tm5 = t20Tm5;
        this.t20TissueSubtotal = t20TissueSubtotal;
        this.t20Total = t20Total;
        this.t20TotalEnergyCost = t20TotalEnergyCost;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
