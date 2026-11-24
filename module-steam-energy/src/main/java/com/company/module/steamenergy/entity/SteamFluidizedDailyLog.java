package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 운전일지 — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 운전일지 (fluidized-daily-log.html)
 * <p>Table: steam_fluidized_daily_log
 * <p>PK: FLUIDIZED_LOG_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_fluidized_daily_log")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDailyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FLUIDIZED_LOG_ID")
    private Long fluidizedLogId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "WORKER_AM", length = 100)
    private String workerAm;

    @Column(name = "WORKER_PM", length = 100)
    private String workerPm;

    @Column(name = "WORKER_NIGHT", length = 100)
    private String workerNight;

    @Column(name = "APPR_LEADER", length = 50)
    private String apprLeader;

    @Column(name = "APPR_ASSISTANT", length = 50)
    private String apprAssistant;

    @Column(name = "APPR_MANAGER", length = 50)
    private String apprManager;

    @Column(name = "CHEM_AMMONIUM_CARBONATE_PREV", precision = 18, scale = 6)
    private BigDecimal chemAmmoniumCarbonatePrev;

    @Column(name = "CHEM_BOILER_AGENT_PREV", precision = 18, scale = 6)
    private BigDecimal chemBoilerAgentPrev;

    @Column(name = "CHEM_ACTIVATED_CARBON_PREV", precision = 18, scale = 6)
    private BigDecimal chemActivatedCarbonPrev;

    @Column(name = "CHEM_CAUSTIC_SODA_PREV", precision = 18, scale = 6)
    private BigDecimal chemCausticSodaPrev;

    @Column(name = "CHEM_SLAKED_LIME_PREV", precision = 18, scale = 6)
    private BigDecimal chemSlakedLimePrev;

    @Column(name = "CHEM_SALT_PREV", precision = 18, scale = 6)
    private BigDecimal chemSaltPrev;

    @Column(name = "CHEM_DIESEL_PREV", precision = 18, scale = 6)
    private BigDecimal chemDieselPrev;

    @Column(name = "CHEM_SRF_PREV", precision = 18, scale = 6)
    private BigDecimal chemSrfPrev;

    @Column(name = "CHEM_SILICA_SAND_PREV", precision = 18, scale = 6)
    private BigDecimal chemSilicaSandPrev;

    @Column(name = "ASH_FLY_OUT", precision = 18, scale = 6)
    private BigDecimal ashFlyOut;

    @Column(name = "ASH_BOTTOM_OUT", precision = 18, scale = 6)
    private BigDecimal ashBottomOut;

    @Column(name = "INCOMBUSTIBLE_DAY", precision = 18, scale = 6)
    private BigDecimal incombustibleDay;

    @Column(name = "INCOMBUSTIBLE_NIGHT", precision = 18, scale = 6)
    private BigDecimal incombustibleNight;

    @Column(name = "POWER_MAIN_PREV", precision = 18, scale = 6)
    private BigDecimal powerMainPrev;

    @Column(name = "POWER_MAIN_TODAY", precision = 18, scale = 6)
    private BigDecimal powerMainToday;

    @Column(name = "POWER_ASH_SILO_PREV", precision = 18, scale = 6)
    private BigDecimal powerAshSiloPrev;

    @Column(name = "POWER_ASH_SILO_TODAY", precision = 18, scale = 6)
    private BigDecimal powerAshSiloToday;

    @Column(name = "POWER_BAG_FILTER_PREV", precision = 18, scale = 6)
    private BigDecimal powerBagFilterPrev;

    @Column(name = "POWER_BAG_FILTER_TODAY", precision = 18, scale = 6)
    private BigDecimal powerBagFilterToday;

    @Column(name = "POWER_SCRUBBER_PREV", precision = 18, scale = 6)
    private BigDecimal powerScrubberPrev;

    @Column(name = "POWER_SCRUBBER_TODAY", precision = 18, scale = 6)
    private BigDecimal powerScrubberToday;

    @Column(name = "POWER_CARBON_FEED_PREV", precision = 18, scale = 6)
    private BigDecimal powerCarbonFeedPrev;

    @Column(name = "POWER_CARBON_FEED_TODAY", precision = 18, scale = 6)
    private BigDecimal powerCarbonFeedToday;

    @Column(name = "POWER_LIME_FEED_PREV", precision = 18, scale = 6)
    private BigDecimal powerLimeFeedPrev;

    @Column(name = "POWER_LIME_FEED_TODAY", precision = 18, scale = 6)
    private BigDecimal powerLimeFeedToday;

    @Column(name = "POWER_UREA_FEED_PREV", precision = 18, scale = 6)
    private BigDecimal powerUreaFeedPrev;

    @Column(name = "POWER_UREA_FEED_TODAY", precision = 18, scale = 6)
    private BigDecimal powerUreaFeedToday;

    @Column(name = "POWER_FD_PREV", precision = 18, scale = 6)
    private BigDecimal powerFdPrev;

    @Column(name = "POWER_FD_TODAY", precision = 18, scale = 6)
    private BigDecimal powerFdToday;

    @Column(name = "POWER_CARBON_BAG_PREV", precision = 18, scale = 6)
    private BigDecimal powerCarbonBagPrev;

    @Column(name = "POWER_CARBON_BAG_TODAY", precision = 18, scale = 6)
    private BigDecimal powerCarbonBagToday;

    @Column(name = "POWER_LIME_BAG_PREV", precision = 18, scale = 6)
    private BigDecimal powerLimeBagPrev;

    @Column(name = "POWER_LIME_BAG_TODAY", precision = 18, scale = 6)
    private BigDecimal powerLimeBagToday;

    @Column(name = "UTIL_REUSE_WATER_PREV", precision = 18, scale = 6)
    private BigDecimal utilReuseWaterPrev;

    @Column(name = "UTIL_RIVER_WATER_PREV", precision = 18, scale = 6)
    private BigDecimal utilRiverWaterPrev;

    @Column(name = "UTIL_CONDENSATE_PREV", precision = 18, scale = 6)
    private BigDecimal utilCondensatePrev;

    @Column(name = "UTIL_BOILER_FEED_PREV", precision = 18, scale = 6)
    private BigDecimal utilBoilerFeedPrev;

    @Column(name = "UTIL_WASTE_WATER_PREV", precision = 18, scale = 6)
    private BigDecimal utilWasteWaterPrev;

    @Column(name = "UTIL_SOFT_WATER_PREV", precision = 18, scale = 6)
    private BigDecimal utilSoftWaterPrev;

    @Column(name = "MOIST_AM_1_TIME", length = 8)
    private String moistAm1Time;

    @Column(name = "MOIST_AM_1_VALUE", precision = 18, scale = 6)
    private BigDecimal moistAm1Value;

    @Column(name = "MOIST_AM_2_TIME", length = 8)
    private String moistAm2Time;

    @Column(name = "MOIST_AM_2_VALUE", precision = 18, scale = 6)
    private BigDecimal moistAm2Value;

    @Column(name = "MOIST_AM_3_TIME", length = 8)
    private String moistAm3Time;

    @Column(name = "MOIST_AM_3_VALUE", precision = 18, scale = 6)
    private BigDecimal moistAm3Value;

    @Column(name = "MOIST_AM_4_TIME", length = 8)
    private String moistAm4Time;

    @Column(name = "MOIST_AM_4_VALUE", precision = 18, scale = 6)
    private BigDecimal moistAm4Value;

    @Column(name = "MOIST_AM_5_TIME", length = 8)
    private String moistAm5Time;

    @Column(name = "MOIST_AM_5_VALUE", precision = 18, scale = 6)
    private BigDecimal moistAm5Value;

    @Column(name = "MOIST_PM_1_TIME", length = 8)
    private String moistPm1Time;

    @Column(name = "MOIST_PM_1_VALUE", precision = 18, scale = 6)
    private BigDecimal moistPm1Value;

    @Column(name = "MOIST_PM_2_TIME", length = 8)
    private String moistPm2Time;

    @Column(name = "MOIST_PM_2_VALUE", precision = 18, scale = 6)
    private BigDecimal moistPm2Value;

    @Column(name = "MOIST_PM_3_TIME", length = 8)
    private String moistPm3Time;

    @Column(name = "MOIST_PM_3_VALUE", precision = 18, scale = 6)
    private BigDecimal moistPm3Value;

    @Column(name = "MOIST_PM_4_TIME", length = 8)
    private String moistPm4Time;

    @Column(name = "MOIST_PM_4_VALUE", precision = 18, scale = 6)
    private BigDecimal moistPm4Value;

    @Column(name = "MOIST_PM_5_TIME", length = 8)
    private String moistPm5Time;

    @Column(name = "MOIST_PM_5_VALUE", precision = 18, scale = 6)
    private BigDecimal moistPm5Value;

    @Column(name = "MOIST_NIGHT_1_TIME", length = 8)
    private String moistNight1Time;

    @Column(name = "MOIST_NIGHT_1_VALUE", precision = 18, scale = 6)
    private BigDecimal moistNight1Value;

    @Column(name = "MOIST_NIGHT_2_TIME", length = 8)
    private String moistNight2Time;

    @Column(name = "MOIST_NIGHT_2_VALUE", precision = 18, scale = 6)
    private BigDecimal moistNight2Value;

    @Column(name = "MOIST_NIGHT_3_TIME", length = 8)
    private String moistNight3Time;

    @Column(name = "MOIST_NIGHT_3_VALUE", precision = 18, scale = 6)
    private BigDecimal moistNight3Value;

    @Column(name = "MOIST_NIGHT_4_TIME", length = 8)
    private String moistNight4Time;

    @Column(name = "MOIST_NIGHT_4_VALUE", precision = 18, scale = 6)
    private BigDecimal moistNight4Value;

    @Column(name = "MOIST_NIGHT_5_TIME", length = 8)
    private String moistNight5Time;

    @Column(name = "MOIST_NIGHT_5_VALUE", precision = 18, scale = 6)
    private BigDecimal moistNight5Value;

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
    public void update(String workerAm, String workerPm, String workerNight, String apprLeader, String apprAssistant, String apprManager, BigDecimal chemAmmoniumCarbonatePrev, BigDecimal chemBoilerAgentPrev, BigDecimal chemActivatedCarbonPrev, BigDecimal chemCausticSodaPrev, BigDecimal chemSlakedLimePrev, BigDecimal chemSaltPrev, BigDecimal chemDieselPrev, BigDecimal chemSrfPrev, BigDecimal chemSilicaSandPrev, BigDecimal ashFlyOut, BigDecimal ashBottomOut, BigDecimal incombustibleDay, BigDecimal incombustibleNight, BigDecimal powerMainPrev, BigDecimal powerMainToday, BigDecimal powerAshSiloPrev, BigDecimal powerAshSiloToday, BigDecimal powerBagFilterPrev, BigDecimal powerBagFilterToday, BigDecimal powerScrubberPrev, BigDecimal powerScrubberToday, BigDecimal powerCarbonFeedPrev, BigDecimal powerCarbonFeedToday, BigDecimal powerLimeFeedPrev, BigDecimal powerLimeFeedToday, BigDecimal powerUreaFeedPrev, BigDecimal powerUreaFeedToday, BigDecimal powerFdPrev, BigDecimal powerFdToday, BigDecimal powerCarbonBagPrev, BigDecimal powerCarbonBagToday, BigDecimal powerLimeBagPrev, BigDecimal powerLimeBagToday, BigDecimal utilReuseWaterPrev, BigDecimal utilRiverWaterPrev, BigDecimal utilCondensatePrev, BigDecimal utilBoilerFeedPrev, BigDecimal utilWasteWaterPrev, BigDecimal utilSoftWaterPrev, String moistAm1Time, BigDecimal moistAm1Value, String moistAm2Time, BigDecimal moistAm2Value, String moistAm3Time, BigDecimal moistAm3Value, String moistAm4Time, BigDecimal moistAm4Value, String moistAm5Time, BigDecimal moistAm5Value, String moistPm1Time, BigDecimal moistPm1Value, String moistPm2Time, BigDecimal moistPm2Value, String moistPm3Time, BigDecimal moistPm3Value, String moistPm4Time, BigDecimal moistPm4Value, String moistPm5Time, BigDecimal moistPm5Value, String moistNight1Time, BigDecimal moistNight1Value, String moistNight2Time, BigDecimal moistNight2Value, String moistNight3Time, BigDecimal moistNight3Value, String moistNight4Time, BigDecimal moistNight4Value, String moistNight5Time, BigDecimal moistNight5Value, Long updatedBy) {
        this.workerAm = workerAm;
        this.workerPm = workerPm;
        this.workerNight = workerNight;
        this.apprLeader = apprLeader;
        this.apprAssistant = apprAssistant;
        this.apprManager = apprManager;
        this.chemAmmoniumCarbonatePrev = chemAmmoniumCarbonatePrev;
        this.chemBoilerAgentPrev = chemBoilerAgentPrev;
        this.chemActivatedCarbonPrev = chemActivatedCarbonPrev;
        this.chemCausticSodaPrev = chemCausticSodaPrev;
        this.chemSlakedLimePrev = chemSlakedLimePrev;
        this.chemSaltPrev = chemSaltPrev;
        this.chemDieselPrev = chemDieselPrev;
        this.chemSrfPrev = chemSrfPrev;
        this.chemSilicaSandPrev = chemSilicaSandPrev;
        this.ashFlyOut = ashFlyOut;
        this.ashBottomOut = ashBottomOut;
        this.incombustibleDay = incombustibleDay;
        this.incombustibleNight = incombustibleNight;
        this.powerMainPrev = powerMainPrev;
        this.powerMainToday = powerMainToday;
        this.powerAshSiloPrev = powerAshSiloPrev;
        this.powerAshSiloToday = powerAshSiloToday;
        this.powerBagFilterPrev = powerBagFilterPrev;
        this.powerBagFilterToday = powerBagFilterToday;
        this.powerScrubberPrev = powerScrubberPrev;
        this.powerScrubberToday = powerScrubberToday;
        this.powerCarbonFeedPrev = powerCarbonFeedPrev;
        this.powerCarbonFeedToday = powerCarbonFeedToday;
        this.powerLimeFeedPrev = powerLimeFeedPrev;
        this.powerLimeFeedToday = powerLimeFeedToday;
        this.powerUreaFeedPrev = powerUreaFeedPrev;
        this.powerUreaFeedToday = powerUreaFeedToday;
        this.powerFdPrev = powerFdPrev;
        this.powerFdToday = powerFdToday;
        this.powerCarbonBagPrev = powerCarbonBagPrev;
        this.powerCarbonBagToday = powerCarbonBagToday;
        this.powerLimeBagPrev = powerLimeBagPrev;
        this.powerLimeBagToday = powerLimeBagToday;
        this.utilReuseWaterPrev = utilReuseWaterPrev;
        this.utilRiverWaterPrev = utilRiverWaterPrev;
        this.utilCondensatePrev = utilCondensatePrev;
        this.utilBoilerFeedPrev = utilBoilerFeedPrev;
        this.utilWasteWaterPrev = utilWasteWaterPrev;
        this.utilSoftWaterPrev = utilSoftWaterPrev;
        this.moistAm1Time = moistAm1Time;
        this.moistAm1Value = moistAm1Value;
        this.moistAm2Time = moistAm2Time;
        this.moistAm2Value = moistAm2Value;
        this.moistAm3Time = moistAm3Time;
        this.moistAm3Value = moistAm3Value;
        this.moistAm4Time = moistAm4Time;
        this.moistAm4Value = moistAm4Value;
        this.moistAm5Time = moistAm5Time;
        this.moistAm5Value = moistAm5Value;
        this.moistPm1Time = moistPm1Time;
        this.moistPm1Value = moistPm1Value;
        this.moistPm2Time = moistPm2Time;
        this.moistPm2Value = moistPm2Value;
        this.moistPm3Time = moistPm3Time;
        this.moistPm3Value = moistPm3Value;
        this.moistPm4Time = moistPm4Time;
        this.moistPm4Value = moistPm4Value;
        this.moistPm5Time = moistPm5Time;
        this.moistPm5Value = moistPm5Value;
        this.moistNight1Time = moistNight1Time;
        this.moistNight1Value = moistNight1Value;
        this.moistNight2Time = moistNight2Time;
        this.moistNight2Value = moistNight2Value;
        this.moistNight3Time = moistNight3Time;
        this.moistNight3Value = moistNight3Value;
        this.moistNight4Time = moistNight4Time;
        this.moistNight4Value = moistNight4Value;
        this.moistNight5Time = moistNight5Time;
        this.moistNight5Value = moistNight5Value;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
