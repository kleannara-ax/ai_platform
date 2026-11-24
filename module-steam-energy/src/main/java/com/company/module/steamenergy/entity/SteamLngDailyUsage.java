package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 원단위 일지 — 일별 LNG 연료 사용량
 *
 * <p>화면: LNG 연료 사용 비용(unit-lng-cost), 오늘 일지 등록, 호기별 요약
 * <p>보일러(20톤-1·2, 50톤, 60톤, 복합보일러, 신설소각로)와 버너(PM-3, TM-3~5) 사용량, 각 단가.
 * <p>복합+외부(MIX_*) 호기별 LNG 는 엑셀 왕복용으로만 보관하고 화면에는 없다.
 * <p>LNG 사용합계·비용·총비용·화장지계는 저장하지 않고 화면에서 계산한다.
 *
 * <p>Table: steam_lng_daily_usage
 * <p>PK: LNG_USAGE_ID, UNIQUE: USAGE_DATE
 */
@Entity
@Table(name = "steam_lng_daily_usage")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamLngDailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LNG_USAGE_ID")
    private Long lngUsageId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "BOILER_20T_1", precision = 18, scale = 6)
    private BigDecimal boiler20t1;

    @Column(name = "BOILER_20T_2", precision = 18, scale = 6)
    private BigDecimal boiler20t2;

    @Column(name = "BOILER_50T", precision = 18, scale = 6)
    private BigDecimal boiler50t;

    @Column(name = "BOILER_60T", precision = 18, scale = 6)
    private BigDecimal boiler60t;

    @Column(name = "BOILER_COMBINED", precision = 18, scale = 6)
    private BigDecimal boilerCombined;

    @Column(name = "BOILER_NEW_INCINERATOR", precision = 18, scale = 6)
    private BigDecimal boilerNewIncinerator;

    @Column(name = "BOILER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal boilerUnitPrice;

    @Column(name = "BURNER_PM3", precision = 18, scale = 6)
    private BigDecimal burnerPm3;

    @Column(name = "BURNER_TM3", precision = 18, scale = 6)
    private BigDecimal burnerTm3;

    @Column(name = "BURNER_TM4", precision = 18, scale = 6)
    private BigDecimal burnerTm4;

    @Column(name = "BURNER_TM5", precision = 18, scale = 6)
    private BigDecimal burnerTm5;

    @Column(name = "BURNER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal burnerUnitPrice;

    @Column(name = "MIX_PM2", precision = 18, scale = 6)
    private BigDecimal mixPm2;

    @Column(name = "MIX_PM3", precision = 18, scale = 6)
    private BigDecimal mixPm3;

    @Column(name = "MIX_TM3", precision = 18, scale = 6)
    private BigDecimal mixTm3;

    @Column(name = "MIX_TM4", precision = 18, scale = 6)
    private BigDecimal mixTm4;

    @Column(name = "MIX_TM5", precision = 18, scale = 6)
    private BigDecimal mixTm5;

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
    public void update(BigDecimal boiler20t1, BigDecimal boiler20t2, BigDecimal boiler50t, BigDecimal boiler60t,
                       BigDecimal boilerCombined, BigDecimal boilerNewIncinerator, BigDecimal boilerUnitPrice,
                       BigDecimal burnerPm3, BigDecimal burnerTm3, BigDecimal burnerTm4, BigDecimal burnerTm5,
                       BigDecimal burnerUnitPrice,
                       BigDecimal mixPm2, BigDecimal mixPm3, BigDecimal mixTm3, BigDecimal mixTm4, BigDecimal mixTm5,
                       Long updatedBy) {
        this.boiler20t1 = boiler20t1;
        this.boiler20t2 = boiler20t2;
        this.boiler50t = boiler50t;
        this.boiler60t = boiler60t;
        this.boilerCombined = boilerCombined;
        this.boilerNewIncinerator = boilerNewIncinerator;
        this.boilerUnitPrice = boilerUnitPrice;
        this.burnerPm3 = burnerPm3;
        this.burnerTm3 = burnerTm3;
        this.burnerTm4 = burnerTm4;
        this.burnerTm5 = burnerTm5;
        this.burnerUnitPrice = burnerUnitPrice;
        this.mixPm2 = mixPm2;
        this.mixPm3 = mixPm3;
        this.mixTm3 = mixTm3;
        this.mixTm4 = mixTm4;
        this.mixTm5 = mixTm5;
        this.updatedBy = updatedBy;
    }

    /** 단가 입력에서 LNG 단가(lng_unit)를 그 달 전체에 적용할 때 — 보일러·버너 단가만 바꾼다. */
    public void updateUnitPrices(BigDecimal boilerUnitPrice, BigDecimal burnerUnitPrice, Long updatedBy) {
        this.boilerUnitPrice = boilerUnitPrice;
        this.burnerUnitPrice = burnerUnitPrice;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 날. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, updatedBy);
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
