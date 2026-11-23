package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 유동상 운전일지 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedDailyLogSaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
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
}
