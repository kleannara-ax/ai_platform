package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDailyLogBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDailyLogResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDailyLogSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedDailyLogService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 유동상 운전일지 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_daily_log / alias: fluidDailyLog
 */
@Component
public class SteamFluidizedDailyLogAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("workerAm", "workerPm", "workerNight", "apprLeader", "apprAssistant", "apprManager", "chemAmmoniumCarbonatePrev", "chemBoilerAgentPrev", "chemActivatedCarbonPrev", "chemCausticSodaPrev", "chemSlakedLimePrev", "chemSaltPrev", "chemDieselPrev", "chemSrfPrev", "chemSilicaSandPrev", "ashFlyOut", "ashBottomOut", "incombustibleDay", "incombustibleNight", "powerMainPrev", "powerMainToday", "powerAshSiloPrev", "powerAshSiloToday", "powerBagFilterPrev", "powerBagFilterToday", "powerScrubberPrev", "powerScrubberToday", "powerCarbonFeedPrev", "powerCarbonFeedToday", "powerLimeFeedPrev", "powerLimeFeedToday", "powerUreaFeedPrev", "powerUreaFeedToday", "powerFdPrev", "powerFdToday", "powerCarbonBagPrev", "powerCarbonBagToday", "powerLimeBagPrev", "powerLimeBagToday", "utilReuseWaterPrev", "utilRiverWaterPrev", "utilCondensatePrev", "utilBoilerFeedPrev", "utilWasteWaterPrev", "utilSoftWaterPrev", "moistAm1Time", "moistAm1Value", "moistAm2Time", "moistAm2Value", "moistAm3Time", "moistAm3Value", "moistAm4Time", "moistAm4Value", "moistAm5Time", "moistAm5Value", "moistPm1Time", "moistPm1Value", "moistPm2Time", "moistPm2Value", "moistPm3Time", "moistPm3Value", "moistPm4Time", "moistPm4Value", "moistPm5Time", "moistPm5Value", "moistNight1Time", "moistNight1Value", "moistNight2Time", "moistNight2Value", "moistNight3Time", "moistNight3Value", "moistNight4Time", "moistNight4Value", "moistNight5Time", "moistNight5Value");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("workerAm", String.class),
            Map.entry("workerPm", String.class),
            Map.entry("workerNight", String.class),
            Map.entry("apprLeader", String.class),
            Map.entry("apprAssistant", String.class),
            Map.entry("apprManager", String.class),
            Map.entry("chemAmmoniumCarbonatePrev", BigDecimal.class),
            Map.entry("chemBoilerAgentPrev", BigDecimal.class),
            Map.entry("chemActivatedCarbonPrev", BigDecimal.class),
            Map.entry("chemCausticSodaPrev", BigDecimal.class),
            Map.entry("chemSlakedLimePrev", BigDecimal.class),
            Map.entry("chemSaltPrev", BigDecimal.class),
            Map.entry("chemDieselPrev", BigDecimal.class),
            Map.entry("chemSrfPrev", BigDecimal.class),
            Map.entry("chemSilicaSandPrev", BigDecimal.class),
            Map.entry("ashFlyOut", BigDecimal.class),
            Map.entry("ashBottomOut", BigDecimal.class),
            Map.entry("incombustibleDay", BigDecimal.class),
            Map.entry("incombustibleNight", BigDecimal.class),
            Map.entry("powerMainPrev", BigDecimal.class),
            Map.entry("powerMainToday", BigDecimal.class),
            Map.entry("powerAshSiloPrev", BigDecimal.class),
            Map.entry("powerAshSiloToday", BigDecimal.class),
            Map.entry("powerBagFilterPrev", BigDecimal.class),
            Map.entry("powerBagFilterToday", BigDecimal.class),
            Map.entry("powerScrubberPrev", BigDecimal.class),
            Map.entry("powerScrubberToday", BigDecimal.class),
            Map.entry("powerCarbonFeedPrev", BigDecimal.class),
            Map.entry("powerCarbonFeedToday", BigDecimal.class),
            Map.entry("powerLimeFeedPrev", BigDecimal.class),
            Map.entry("powerLimeFeedToday", BigDecimal.class),
            Map.entry("powerUreaFeedPrev", BigDecimal.class),
            Map.entry("powerUreaFeedToday", BigDecimal.class),
            Map.entry("powerFdPrev", BigDecimal.class),
            Map.entry("powerFdToday", BigDecimal.class),
            Map.entry("powerCarbonBagPrev", BigDecimal.class),
            Map.entry("powerCarbonBagToday", BigDecimal.class),
            Map.entry("powerLimeBagPrev", BigDecimal.class),
            Map.entry("powerLimeBagToday", BigDecimal.class),
            Map.entry("utilReuseWaterPrev", BigDecimal.class),
            Map.entry("utilRiverWaterPrev", BigDecimal.class),
            Map.entry("utilCondensatePrev", BigDecimal.class),
            Map.entry("utilBoilerFeedPrev", BigDecimal.class),
            Map.entry("utilWasteWaterPrev", BigDecimal.class),
            Map.entry("utilSoftWaterPrev", BigDecimal.class),
            Map.entry("moistAm1Time", String.class),
            Map.entry("moistAm1Value", BigDecimal.class),
            Map.entry("moistAm2Time", String.class),
            Map.entry("moistAm2Value", BigDecimal.class),
            Map.entry("moistAm3Time", String.class),
            Map.entry("moistAm3Value", BigDecimal.class),
            Map.entry("moistAm4Time", String.class),
            Map.entry("moistAm4Value", BigDecimal.class),
            Map.entry("moistAm5Time", String.class),
            Map.entry("moistAm5Value", BigDecimal.class),
            Map.entry("moistPm1Time", String.class),
            Map.entry("moistPm1Value", BigDecimal.class),
            Map.entry("moistPm2Time", String.class),
            Map.entry("moistPm2Value", BigDecimal.class),
            Map.entry("moistPm3Time", String.class),
            Map.entry("moistPm3Value", BigDecimal.class),
            Map.entry("moistPm4Time", String.class),
            Map.entry("moistPm4Value", BigDecimal.class),
            Map.entry("moistPm5Time", String.class),
            Map.entry("moistPm5Value", BigDecimal.class),
            Map.entry("moistNight1Time", String.class),
            Map.entry("moistNight1Value", BigDecimal.class),
            Map.entry("moistNight2Time", String.class),
            Map.entry("moistNight2Value", BigDecimal.class),
            Map.entry("moistNight3Time", String.class),
            Map.entry("moistNight3Value", BigDecimal.class),
            Map.entry("moistNight4Time", String.class),
            Map.entry("moistNight4Value", BigDecimal.class),
            Map.entry("moistNight5Time", String.class),
            Map.entry("moistNight5Value", BigDecimal.class)
    );

    private static final Map<String, Function<SteamFluidizedDailyLogResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("workerAm", r -> r.getWorkerAm()),
            Map.entry("workerPm", r -> r.getWorkerPm()),
            Map.entry("workerNight", r -> r.getWorkerNight()),
            Map.entry("apprLeader", r -> r.getApprLeader()),
            Map.entry("apprAssistant", r -> r.getApprAssistant()),
            Map.entry("apprManager", r -> r.getApprManager()),
            Map.entry("chemAmmoniumCarbonatePrev", r -> r.getChemAmmoniumCarbonatePrev()),
            Map.entry("chemBoilerAgentPrev", r -> r.getChemBoilerAgentPrev()),
            Map.entry("chemActivatedCarbonPrev", r -> r.getChemActivatedCarbonPrev()),
            Map.entry("chemCausticSodaPrev", r -> r.getChemCausticSodaPrev()),
            Map.entry("chemSlakedLimePrev", r -> r.getChemSlakedLimePrev()),
            Map.entry("chemSaltPrev", r -> r.getChemSaltPrev()),
            Map.entry("chemDieselPrev", r -> r.getChemDieselPrev()),
            Map.entry("chemSrfPrev", r -> r.getChemSrfPrev()),
            Map.entry("chemSilicaSandPrev", r -> r.getChemSilicaSandPrev()),
            Map.entry("ashFlyOut", r -> r.getAshFlyOut()),
            Map.entry("ashBottomOut", r -> r.getAshBottomOut()),
            Map.entry("incombustibleDay", r -> r.getIncombustibleDay()),
            Map.entry("incombustibleNight", r -> r.getIncombustibleNight()),
            Map.entry("powerMainPrev", r -> r.getPowerMainPrev()),
            Map.entry("powerMainToday", r -> r.getPowerMainToday()),
            Map.entry("powerAshSiloPrev", r -> r.getPowerAshSiloPrev()),
            Map.entry("powerAshSiloToday", r -> r.getPowerAshSiloToday()),
            Map.entry("powerBagFilterPrev", r -> r.getPowerBagFilterPrev()),
            Map.entry("powerBagFilterToday", r -> r.getPowerBagFilterToday()),
            Map.entry("powerScrubberPrev", r -> r.getPowerScrubberPrev()),
            Map.entry("powerScrubberToday", r -> r.getPowerScrubberToday()),
            Map.entry("powerCarbonFeedPrev", r -> r.getPowerCarbonFeedPrev()),
            Map.entry("powerCarbonFeedToday", r -> r.getPowerCarbonFeedToday()),
            Map.entry("powerLimeFeedPrev", r -> r.getPowerLimeFeedPrev()),
            Map.entry("powerLimeFeedToday", r -> r.getPowerLimeFeedToday()),
            Map.entry("powerUreaFeedPrev", r -> r.getPowerUreaFeedPrev()),
            Map.entry("powerUreaFeedToday", r -> r.getPowerUreaFeedToday()),
            Map.entry("powerFdPrev", r -> r.getPowerFdPrev()),
            Map.entry("powerFdToday", r -> r.getPowerFdToday()),
            Map.entry("powerCarbonBagPrev", r -> r.getPowerCarbonBagPrev()),
            Map.entry("powerCarbonBagToday", r -> r.getPowerCarbonBagToday()),
            Map.entry("powerLimeBagPrev", r -> r.getPowerLimeBagPrev()),
            Map.entry("powerLimeBagToday", r -> r.getPowerLimeBagToday()),
            Map.entry("utilReuseWaterPrev", r -> r.getUtilReuseWaterPrev()),
            Map.entry("utilRiverWaterPrev", r -> r.getUtilRiverWaterPrev()),
            Map.entry("utilCondensatePrev", r -> r.getUtilCondensatePrev()),
            Map.entry("utilBoilerFeedPrev", r -> r.getUtilBoilerFeedPrev()),
            Map.entry("utilWasteWaterPrev", r -> r.getUtilWasteWaterPrev()),
            Map.entry("utilSoftWaterPrev", r -> r.getUtilSoftWaterPrev()),
            Map.entry("moistAm1Time", r -> r.getMoistAm1Time()),
            Map.entry("moistAm1Value", r -> r.getMoistAm1Value()),
            Map.entry("moistAm2Time", r -> r.getMoistAm2Time()),
            Map.entry("moistAm2Value", r -> r.getMoistAm2Value()),
            Map.entry("moistAm3Time", r -> r.getMoistAm3Time()),
            Map.entry("moistAm3Value", r -> r.getMoistAm3Value()),
            Map.entry("moistAm4Time", r -> r.getMoistAm4Time()),
            Map.entry("moistAm4Value", r -> r.getMoistAm4Value()),
            Map.entry("moistAm5Time", r -> r.getMoistAm5Time()),
            Map.entry("moistAm5Value", r -> r.getMoistAm5Value()),
            Map.entry("moistPm1Time", r -> r.getMoistPm1Time()),
            Map.entry("moistPm1Value", r -> r.getMoistPm1Value()),
            Map.entry("moistPm2Time", r -> r.getMoistPm2Time()),
            Map.entry("moistPm2Value", r -> r.getMoistPm2Value()),
            Map.entry("moistPm3Time", r -> r.getMoistPm3Time()),
            Map.entry("moistPm3Value", r -> r.getMoistPm3Value()),
            Map.entry("moistPm4Time", r -> r.getMoistPm4Time()),
            Map.entry("moistPm4Value", r -> r.getMoistPm4Value()),
            Map.entry("moistPm5Time", r -> r.getMoistPm5Time()),
            Map.entry("moistPm5Value", r -> r.getMoistPm5Value()),
            Map.entry("moistNight1Time", r -> r.getMoistNight1Time()),
            Map.entry("moistNight1Value", r -> r.getMoistNight1Value()),
            Map.entry("moistNight2Time", r -> r.getMoistNight2Time()),
            Map.entry("moistNight2Value", r -> r.getMoistNight2Value()),
            Map.entry("moistNight3Time", r -> r.getMoistNight3Time()),
            Map.entry("moistNight3Value", r -> r.getMoistNight3Value()),
            Map.entry("moistNight4Time", r -> r.getMoistNight4Time()),
            Map.entry("moistNight4Value", r -> r.getMoistNight4Value()),
            Map.entry("moistNight5Time", r -> r.getMoistNight5Time()),
            Map.entry("moistNight5Value", r -> r.getMoistNight5Value())
    );

    private static final Map<String, BiConsumer<SteamFluidizedDailyLogSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("workerAm", (r, v) -> r.setWorkerAm((String) v)),
            Map.entry("workerPm", (r, v) -> r.setWorkerPm((String) v)),
            Map.entry("workerNight", (r, v) -> r.setWorkerNight((String) v)),
            Map.entry("apprLeader", (r, v) -> r.setApprLeader((String) v)),
            Map.entry("apprAssistant", (r, v) -> r.setApprAssistant((String) v)),
            Map.entry("apprManager", (r, v) -> r.setApprManager((String) v)),
            Map.entry("chemAmmoniumCarbonatePrev", (r, v) -> r.setChemAmmoniumCarbonatePrev((BigDecimal) v)),
            Map.entry("chemBoilerAgentPrev", (r, v) -> r.setChemBoilerAgentPrev((BigDecimal) v)),
            Map.entry("chemActivatedCarbonPrev", (r, v) -> r.setChemActivatedCarbonPrev((BigDecimal) v)),
            Map.entry("chemCausticSodaPrev", (r, v) -> r.setChemCausticSodaPrev((BigDecimal) v)),
            Map.entry("chemSlakedLimePrev", (r, v) -> r.setChemSlakedLimePrev((BigDecimal) v)),
            Map.entry("chemSaltPrev", (r, v) -> r.setChemSaltPrev((BigDecimal) v)),
            Map.entry("chemDieselPrev", (r, v) -> r.setChemDieselPrev((BigDecimal) v)),
            Map.entry("chemSrfPrev", (r, v) -> r.setChemSrfPrev((BigDecimal) v)),
            Map.entry("chemSilicaSandPrev", (r, v) -> r.setChemSilicaSandPrev((BigDecimal) v)),
            Map.entry("ashFlyOut", (r, v) -> r.setAshFlyOut((BigDecimal) v)),
            Map.entry("ashBottomOut", (r, v) -> r.setAshBottomOut((BigDecimal) v)),
            Map.entry("incombustibleDay", (r, v) -> r.setIncombustibleDay((BigDecimal) v)),
            Map.entry("incombustibleNight", (r, v) -> r.setIncombustibleNight((BigDecimal) v)),
            Map.entry("powerMainPrev", (r, v) -> r.setPowerMainPrev((BigDecimal) v)),
            Map.entry("powerMainToday", (r, v) -> r.setPowerMainToday((BigDecimal) v)),
            Map.entry("powerAshSiloPrev", (r, v) -> r.setPowerAshSiloPrev((BigDecimal) v)),
            Map.entry("powerAshSiloToday", (r, v) -> r.setPowerAshSiloToday((BigDecimal) v)),
            Map.entry("powerBagFilterPrev", (r, v) -> r.setPowerBagFilterPrev((BigDecimal) v)),
            Map.entry("powerBagFilterToday", (r, v) -> r.setPowerBagFilterToday((BigDecimal) v)),
            Map.entry("powerScrubberPrev", (r, v) -> r.setPowerScrubberPrev((BigDecimal) v)),
            Map.entry("powerScrubberToday", (r, v) -> r.setPowerScrubberToday((BigDecimal) v)),
            Map.entry("powerCarbonFeedPrev", (r, v) -> r.setPowerCarbonFeedPrev((BigDecimal) v)),
            Map.entry("powerCarbonFeedToday", (r, v) -> r.setPowerCarbonFeedToday((BigDecimal) v)),
            Map.entry("powerLimeFeedPrev", (r, v) -> r.setPowerLimeFeedPrev((BigDecimal) v)),
            Map.entry("powerLimeFeedToday", (r, v) -> r.setPowerLimeFeedToday((BigDecimal) v)),
            Map.entry("powerUreaFeedPrev", (r, v) -> r.setPowerUreaFeedPrev((BigDecimal) v)),
            Map.entry("powerUreaFeedToday", (r, v) -> r.setPowerUreaFeedToday((BigDecimal) v)),
            Map.entry("powerFdPrev", (r, v) -> r.setPowerFdPrev((BigDecimal) v)),
            Map.entry("powerFdToday", (r, v) -> r.setPowerFdToday((BigDecimal) v)),
            Map.entry("powerCarbonBagPrev", (r, v) -> r.setPowerCarbonBagPrev((BigDecimal) v)),
            Map.entry("powerCarbonBagToday", (r, v) -> r.setPowerCarbonBagToday((BigDecimal) v)),
            Map.entry("powerLimeBagPrev", (r, v) -> r.setPowerLimeBagPrev((BigDecimal) v)),
            Map.entry("powerLimeBagToday", (r, v) -> r.setPowerLimeBagToday((BigDecimal) v)),
            Map.entry("utilReuseWaterPrev", (r, v) -> r.setUtilReuseWaterPrev((BigDecimal) v)),
            Map.entry("utilRiverWaterPrev", (r, v) -> r.setUtilRiverWaterPrev((BigDecimal) v)),
            Map.entry("utilCondensatePrev", (r, v) -> r.setUtilCondensatePrev((BigDecimal) v)),
            Map.entry("utilBoilerFeedPrev", (r, v) -> r.setUtilBoilerFeedPrev((BigDecimal) v)),
            Map.entry("utilWasteWaterPrev", (r, v) -> r.setUtilWasteWaterPrev((BigDecimal) v)),
            Map.entry("utilSoftWaterPrev", (r, v) -> r.setUtilSoftWaterPrev((BigDecimal) v)),
            Map.entry("moistAm1Time", (r, v) -> r.setMoistAm1Time((String) v)),
            Map.entry("moistAm1Value", (r, v) -> r.setMoistAm1Value((BigDecimal) v)),
            Map.entry("moistAm2Time", (r, v) -> r.setMoistAm2Time((String) v)),
            Map.entry("moistAm2Value", (r, v) -> r.setMoistAm2Value((BigDecimal) v)),
            Map.entry("moistAm3Time", (r, v) -> r.setMoistAm3Time((String) v)),
            Map.entry("moistAm3Value", (r, v) -> r.setMoistAm3Value((BigDecimal) v)),
            Map.entry("moistAm4Time", (r, v) -> r.setMoistAm4Time((String) v)),
            Map.entry("moistAm4Value", (r, v) -> r.setMoistAm4Value((BigDecimal) v)),
            Map.entry("moistAm5Time", (r, v) -> r.setMoistAm5Time((String) v)),
            Map.entry("moistAm5Value", (r, v) -> r.setMoistAm5Value((BigDecimal) v)),
            Map.entry("moistPm1Time", (r, v) -> r.setMoistPm1Time((String) v)),
            Map.entry("moistPm1Value", (r, v) -> r.setMoistPm1Value((BigDecimal) v)),
            Map.entry("moistPm2Time", (r, v) -> r.setMoistPm2Time((String) v)),
            Map.entry("moistPm2Value", (r, v) -> r.setMoistPm2Value((BigDecimal) v)),
            Map.entry("moistPm3Time", (r, v) -> r.setMoistPm3Time((String) v)),
            Map.entry("moistPm3Value", (r, v) -> r.setMoistPm3Value((BigDecimal) v)),
            Map.entry("moistPm4Time", (r, v) -> r.setMoistPm4Time((String) v)),
            Map.entry("moistPm4Value", (r, v) -> r.setMoistPm4Value((BigDecimal) v)),
            Map.entry("moistPm5Time", (r, v) -> r.setMoistPm5Time((String) v)),
            Map.entry("moistPm5Value", (r, v) -> r.setMoistPm5Value((BigDecimal) v)),
            Map.entry("moistNight1Time", (r, v) -> r.setMoistNight1Time((String) v)),
            Map.entry("moistNight1Value", (r, v) -> r.setMoistNight1Value((BigDecimal) v)),
            Map.entry("moistNight2Time", (r, v) -> r.setMoistNight2Time((String) v)),
            Map.entry("moistNight2Value", (r, v) -> r.setMoistNight2Value((BigDecimal) v)),
            Map.entry("moistNight3Time", (r, v) -> r.setMoistNight3Time((String) v)),
            Map.entry("moistNight3Value", (r, v) -> r.setMoistNight3Value((BigDecimal) v)),
            Map.entry("moistNight4Time", (r, v) -> r.setMoistNight4Time((String) v)),
            Map.entry("moistNight4Value", (r, v) -> r.setMoistNight4Value((BigDecimal) v)),
            Map.entry("moistNight5Time", (r, v) -> r.setMoistNight5Time((String) v)),
            Map.entry("moistNight5Value", (r, v) -> r.setMoistNight5Value((BigDecimal) v))
    );

    private final SteamFluidizedDailyLogService service;

    public SteamFluidizedDailyLogAdapter(SteamFluidizedDailyLogService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidDailyLog"; }

    @Override
    public String time() { return "day"; }

    @Override
    public List<String> keyFields() { return KEY_FIELDS; }

    @Override
    public List<String> valueFields() { return VALUE_FIELDS; }

    @Override
    public Class<?> typeOf(String field) { return TYPES.get(field); }

    @Override
    public List<Map<String, Object>> load(int year, Integer month) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SteamFluidizedDailyLogResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedDailyLogBulkRequest request = new SteamFluidizedDailyLogBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedDailyLogSaveRequest r = new SteamFluidizedDailyLogSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedDailyLogSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
