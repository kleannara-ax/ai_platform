package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedDailyLog;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 유동상 운전일지 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDailyLogResponse {

    private Long fluidizedLogId;
    private LocalDate usageDate;
    private String workerAm;
    private String workerPm;
    private String workerNight;
    private String apprLeader;
    private String apprAssistant;
    private String apprManager;
    private BigDecimal chemAmmoniumCarbonatePrev;
    private BigDecimal chemBoilerAgentPrev;
    private BigDecimal chemActivatedCarbonPrev;
    private BigDecimal chemCausticSodaPrev;
    private BigDecimal chemSlakedLimePrev;
    private BigDecimal chemSaltPrev;
    private BigDecimal chemDieselPrev;
    private BigDecimal chemSrfPrev;
    private BigDecimal chemSilicaSandPrev;
    private BigDecimal ashFlyOut;
    private BigDecimal ashBottomOut;
    private BigDecimal incombustibleDay;
    private BigDecimal incombustibleNight;
    private BigDecimal powerMainPrev;
    private BigDecimal powerMainToday;
    private BigDecimal powerAshSiloPrev;
    private BigDecimal powerAshSiloToday;
    private BigDecimal powerBagFilterPrev;
    private BigDecimal powerBagFilterToday;
    private BigDecimal powerScrubberPrev;
    private BigDecimal powerScrubberToday;
    private BigDecimal powerCarbonFeedPrev;
    private BigDecimal powerCarbonFeedToday;
    private BigDecimal powerLimeFeedPrev;
    private BigDecimal powerLimeFeedToday;
    private BigDecimal powerUreaFeedPrev;
    private BigDecimal powerUreaFeedToday;
    private BigDecimal powerFdPrev;
    private BigDecimal powerFdToday;
    private BigDecimal powerCarbonBagPrev;
    private BigDecimal powerCarbonBagToday;
    private BigDecimal powerLimeBagPrev;
    private BigDecimal powerLimeBagToday;
    private BigDecimal utilReuseWaterPrev;
    private BigDecimal utilRiverWaterPrev;
    private BigDecimal utilCondensatePrev;
    private BigDecimal utilBoilerFeedPrev;
    private BigDecimal utilWasteWaterPrev;
    private BigDecimal utilSoftWaterPrev;
    private String moistAm1Time;
    private BigDecimal moistAm1Value;
    private String moistAm2Time;
    private BigDecimal moistAm2Value;
    private String moistAm3Time;
    private BigDecimal moistAm3Value;
    private String moistAm4Time;
    private BigDecimal moistAm4Value;
    private String moistAm5Time;
    private BigDecimal moistAm5Value;
    private String moistPm1Time;
    private BigDecimal moistPm1Value;
    private String moistPm2Time;
    private BigDecimal moistPm2Value;
    private String moistPm3Time;
    private BigDecimal moistPm3Value;
    private String moistPm4Time;
    private BigDecimal moistPm4Value;
    private String moistPm5Time;
    private BigDecimal moistPm5Value;
    private String moistNight1Time;
    private BigDecimal moistNight1Value;
    private String moistNight2Time;
    private BigDecimal moistNight2Value;
    private String moistNight3Time;
    private BigDecimal moistNight3Value;
    private String moistNight4Time;
    private BigDecimal moistNight4Value;
    private String moistNight5Time;
    private BigDecimal moistNight5Value;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedDailyLogResponse from(SteamFluidizedDailyLog e) {
        return from(e, null);
    }

    public static SteamFluidizedDailyLogResponse from(SteamFluidizedDailyLog e, Boolean isUpdate) {
        return SteamFluidizedDailyLogResponse.builder()
                .fluidizedLogId(e.getFluidizedLogId())
                .usageDate(e.getUsageDate())
                .workerAm(e.getWorkerAm())
                .workerPm(e.getWorkerPm())
                .workerNight(e.getWorkerNight())
                .apprLeader(e.getApprLeader())
                .apprAssistant(e.getApprAssistant())
                .apprManager(e.getApprManager())
                .chemAmmoniumCarbonatePrev(e.getChemAmmoniumCarbonatePrev())
                .chemBoilerAgentPrev(e.getChemBoilerAgentPrev())
                .chemActivatedCarbonPrev(e.getChemActivatedCarbonPrev())
                .chemCausticSodaPrev(e.getChemCausticSodaPrev())
                .chemSlakedLimePrev(e.getChemSlakedLimePrev())
                .chemSaltPrev(e.getChemSaltPrev())
                .chemDieselPrev(e.getChemDieselPrev())
                .chemSrfPrev(e.getChemSrfPrev())
                .chemSilicaSandPrev(e.getChemSilicaSandPrev())
                .ashFlyOut(e.getAshFlyOut())
                .ashBottomOut(e.getAshBottomOut())
                .incombustibleDay(e.getIncombustibleDay())
                .incombustibleNight(e.getIncombustibleNight())
                .powerMainPrev(e.getPowerMainPrev())
                .powerMainToday(e.getPowerMainToday())
                .powerAshSiloPrev(e.getPowerAshSiloPrev())
                .powerAshSiloToday(e.getPowerAshSiloToday())
                .powerBagFilterPrev(e.getPowerBagFilterPrev())
                .powerBagFilterToday(e.getPowerBagFilterToday())
                .powerScrubberPrev(e.getPowerScrubberPrev())
                .powerScrubberToday(e.getPowerScrubberToday())
                .powerCarbonFeedPrev(e.getPowerCarbonFeedPrev())
                .powerCarbonFeedToday(e.getPowerCarbonFeedToday())
                .powerLimeFeedPrev(e.getPowerLimeFeedPrev())
                .powerLimeFeedToday(e.getPowerLimeFeedToday())
                .powerUreaFeedPrev(e.getPowerUreaFeedPrev())
                .powerUreaFeedToday(e.getPowerUreaFeedToday())
                .powerFdPrev(e.getPowerFdPrev())
                .powerFdToday(e.getPowerFdToday())
                .powerCarbonBagPrev(e.getPowerCarbonBagPrev())
                .powerCarbonBagToday(e.getPowerCarbonBagToday())
                .powerLimeBagPrev(e.getPowerLimeBagPrev())
                .powerLimeBagToday(e.getPowerLimeBagToday())
                .utilReuseWaterPrev(e.getUtilReuseWaterPrev())
                .utilRiverWaterPrev(e.getUtilRiverWaterPrev())
                .utilCondensatePrev(e.getUtilCondensatePrev())
                .utilBoilerFeedPrev(e.getUtilBoilerFeedPrev())
                .utilWasteWaterPrev(e.getUtilWasteWaterPrev())
                .utilSoftWaterPrev(e.getUtilSoftWaterPrev())
                .moistAm1Time(e.getMoistAm1Time())
                .moistAm1Value(e.getMoistAm1Value())
                .moistAm2Time(e.getMoistAm2Time())
                .moistAm2Value(e.getMoistAm2Value())
                .moistAm3Time(e.getMoistAm3Time())
                .moistAm3Value(e.getMoistAm3Value())
                .moistAm4Time(e.getMoistAm4Time())
                .moistAm4Value(e.getMoistAm4Value())
                .moistAm5Time(e.getMoistAm5Time())
                .moistAm5Value(e.getMoistAm5Value())
                .moistPm1Time(e.getMoistPm1Time())
                .moistPm1Value(e.getMoistPm1Value())
                .moistPm2Time(e.getMoistPm2Time())
                .moistPm2Value(e.getMoistPm2Value())
                .moistPm3Time(e.getMoistPm3Time())
                .moistPm3Value(e.getMoistPm3Value())
                .moistPm4Time(e.getMoistPm4Time())
                .moistPm4Value(e.getMoistPm4Value())
                .moistPm5Time(e.getMoistPm5Time())
                .moistPm5Value(e.getMoistPm5Value())
                .moistNight1Time(e.getMoistNight1Time())
                .moistNight1Value(e.getMoistNight1Value())
                .moistNight2Time(e.getMoistNight2Time())
                .moistNight2Value(e.getMoistNight2Value())
                .moistNight3Time(e.getMoistNight3Time())
                .moistNight3Value(e.getMoistNight3Value())
                .moistNight4Time(e.getMoistNight4Time())
                .moistNight4Value(e.getMoistNight4Value())
                .moistNight5Time(e.getMoistNight5Time())
                .moistNight5Value(e.getMoistNight5Value())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
