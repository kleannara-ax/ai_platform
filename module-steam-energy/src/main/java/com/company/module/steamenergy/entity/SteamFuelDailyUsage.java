package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 원단위 일지 — 유동상·폐합성소각로·복합보일러·외부보일러 일일 스팀 구매량과 단가
 *
 * <p>화면: 유동상/소각로/복합 일일 사용량(unit-flow-daily), 오늘 일지 등록, 호기별 요약.
 * 유동상 스팀량(FLUID_STEAM_TON)은 유동상 운전일지·세부 운영내역도 같이 쓴다.
 * <p>비용·스팀구매비·총 연료비는 저장하지 않고 화면에서 계산한다.
 *
 * <p>Table: steam_fuel_daily_usage
 * <p>PK: FUEL_USAGE_ID, UNIQUE: USAGE_DATE
 */
@Entity
@Table(name = "steam_fuel_daily_usage")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFuelDailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FUEL_USAGE_ID")
    private Long fuelUsageId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "FLUID_STEAM_TON", precision = 18, scale = 6)
    private BigDecimal fluidSteamTon;

    @Column(name = "FLUID_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal fluidUnitPrice;

    @Column(name = "INCINERATOR_STEAM_TON", precision = 18, scale = 6)
    private BigDecimal incineratorSteamTon;

    @Column(name = "INCINERATOR_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal incineratorUnitPrice;

    @Column(name = "COMBINED_STEAM_TON", precision = 18, scale = 6)
    private BigDecimal combinedSteamTon;

    @Column(name = "COMBINED_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal combinedUnitPrice;

    @Column(name = "COMBINED_DEDUCTION", precision = 18, scale = 6)
    private BigDecimal combinedDeduction;

    @Column(name = "EXT_BOILER_STEAM_TON", precision = 18, scale = 6)
    private BigDecimal extBoilerSteamTon;

    @Column(name = "EXT_BOILER_UNIT_PRICE", precision = 18, scale = 6)
    private BigDecimal extBoilerUnitPrice;

    @Column(name = "EXT_BOILER_DEDUCTION", precision = 18, scale = 6)
    private BigDecimal extBoilerDeduction;

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
    public void update(BigDecimal fluidSteamTon, BigDecimal fluidUnitPrice,
                       BigDecimal incineratorSteamTon, BigDecimal incineratorUnitPrice,
                       BigDecimal combinedSteamTon, BigDecimal combinedUnitPrice, BigDecimal combinedDeduction,
                       BigDecimal extBoilerSteamTon, BigDecimal extBoilerUnitPrice, BigDecimal extBoilerDeduction,
                       Long updatedBy) {
        this.fluidSteamTon = fluidSteamTon;
        this.fluidUnitPrice = fluidUnitPrice;
        this.incineratorSteamTon = incineratorSteamTon;
        this.incineratorUnitPrice = incineratorUnitPrice;
        this.combinedSteamTon = combinedSteamTon;
        this.combinedUnitPrice = combinedUnitPrice;
        this.combinedDeduction = combinedDeduction;
        this.extBoilerSteamTon = extBoilerSteamTon;
        this.extBoilerUnitPrice = extBoilerUnitPrice;
        this.extBoilerDeduction = extBoilerDeduction;
        this.updatedBy = updatedBy;
    }

    /** 유동상 스팀량만 갱신 — 유동상 운전일지·엑셀 가져오기가 송기량을 넣을 때. */
    public void updateFluidSteamTon(BigDecimal fluidSteamTon, Long updatedBy) {
        this.fluidSteamTon = fluidSteamTon;
        this.updatedBy = updatedBy;
    }

    /** 단가 입력에서 유동상 단가(fluid_steam)를 그 달 전체에 적용할 때. */
    public void updateFluidUnitPrice(BigDecimal fluidUnitPrice, Long updatedBy) {
        this.fluidUnitPrice = fluidUnitPrice;
        this.updatedBy = updatedBy;
    }

    /** 단가 입력에서 폐합성소각로 단가(waste2_steam)를 그 달 전체에 적용할 때. */
    public void updateIncineratorUnitPrice(BigDecimal incineratorUnitPrice, Long updatedBy) {
        this.incineratorUnitPrice = incineratorUnitPrice;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 날. */
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
