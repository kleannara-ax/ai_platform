package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 연료비 배분비율(버너 LNG) — 연/월별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 연료비 배분비율 › 버너 LNG (energy-fuel-ratio.html t10~t12)
 * <p>Table: steam_energy_ratio_burner
 * <p>PK: RATIO_BURNER_ID, UNIQUE: (YEAR_NO, MONTH_NO)
 */
@Entity
@Table(name = "steam_energy_ratio_burner")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioBurner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RATIO_BURNER_ID")
    private Long ratioBurnerId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "LNG_PM3", precision = 18, scale = 6)
    private BigDecimal lngPm3;

    @Column(name = "LNG_TM3", precision = 18, scale = 6)
    private BigDecimal lngTm3;

    @Column(name = "LNG_TM4", precision = 18, scale = 6)
    private BigDecimal lngTm4;

    @Column(name = "LNG_TM5", precision = 18, scale = 6)
    private BigDecimal lngTm5;

    @Column(name = "LNG_PRICE_PREV_YEAR", precision = 18, scale = 6)
    private BigDecimal lngPricePrevYear;

    @Column(name = "LNG_PRICE", precision = 18, scale = 6)
    private BigDecimal lngPrice;

    @Column(name = "LNG_PRICE_CHANGE_RATE", precision = 18, scale = 6)
    private BigDecimal lngPriceChangeRate;

    @Column(name = "LNG_COST_TOTAL", precision = 18, scale = 6)
    private BigDecimal lngCostTotal;

    @Column(name = "LNG_UNIT_PM3", precision = 18, scale = 6)
    private BigDecimal lngUnitPm3;

    @Column(name = "LNG_UNIT_TM3", precision = 18, scale = 6)
    private BigDecimal lngUnitTm3;

    @Column(name = "LNG_UNIT_TM4", precision = 18, scale = 6)
    private BigDecimal lngUnitTm4;

    @Column(name = "LNG_UNIT_TM5", precision = 18, scale = 6)
    private BigDecimal lngUnitTm5;

    @Column(name = "LNG_UNIT_TISSUE_SUBTOTAL", precision = 18, scale = 6)
    private BigDecimal lngUnitTissueSubtotal;

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
    public void update(BigDecimal lngPm3, BigDecimal lngTm3, BigDecimal lngTm4, BigDecimal lngTm5, BigDecimal lngPricePrevYear, BigDecimal lngPrice, BigDecimal lngPriceChangeRate, BigDecimal lngCostTotal, BigDecimal lngUnitPm3, BigDecimal lngUnitTm3, BigDecimal lngUnitTm4, BigDecimal lngUnitTm5, BigDecimal lngUnitTissueSubtotal, Long updatedBy) {
        this.lngPm3 = lngPm3;
        this.lngTm3 = lngTm3;
        this.lngTm4 = lngTm4;
        this.lngTm5 = lngTm5;
        this.lngPricePrevYear = lngPricePrevYear;
        this.lngPrice = lngPrice;
        this.lngPriceChangeRate = lngPriceChangeRate;
        this.lngCostTotal = lngCostTotal;
        this.lngUnitPm3 = lngUnitPm3;
        this.lngUnitTm3 = lngUnitTm3;
        this.lngUnitTm4 = lngUnitTm4;
        this.lngUnitTm5 = lngUnitTm5;
        this.lngUnitTissueSubtotal = lngUnitTissueSubtotal;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 달. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
