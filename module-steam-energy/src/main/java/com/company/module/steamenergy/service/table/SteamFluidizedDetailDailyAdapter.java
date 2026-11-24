package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailyResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailySaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedDetailDailyService;
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
 * 유동상 세부 운영내역 (일별) — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_detail_daily / alias: fluidDetailDaily
 */
@Component
public class SteamFluidizedDetailDailyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("runHours", "steamProducedTon", "sludgeBurnTon", "srfBurnTon", "steamUnitPrice", "stopHourUnitPrice", "ammoniumCarbonateInKg", "ammoniumCarbonateUseKg", "boilerChemInKg", "boilerChemUseKg", "activatedCarbonInKg", "activatedCarbonUseKg", "causticSodaInKg", "causticSodaUseKg", "slakedLimeInKg", "slakedLimeUseKg", "saltInKg", "saltUseKg", "dieselInL", "dieselUseL", "silicaSandInKg", "silicaSandUseKg", "activatedCarbonExtraInKg", "emergencyDieselInL", "srfInTon", "flyAshGeneratedTon", "flyAshOut01Ton", "flyAshOut02Ton", "flyAshOut03Ton", "flyAshOut04Ton", "flyAshOut05Ton", "flyAshOut06Ton", "flyAshOut07Ton", "flyAshOut08Ton", "bottomAshGeneratedTon", "bottomAshOut01Ton", "bottomAshOut02Ton", "bottomAshOut03Ton", "bottomAshOut04Ton", "powerMainKwh", "powerAshVentFilterKwh", "powerBagFilterKwh", "powerScrubberKwh", "powerCarbonFeedKwh", "powerLimeFeedKwh", "powerUreaKwh", "powerFdFanKwh", "reuseWaterTon", "riverbedWaterTon", "condensateTon", "boilerFeedWaterTon", "wasteWaterTon", "softWaterTon", "sludgeOutsource01Ton", "sludgeOutsource02Ton", "sludgeOutsource03Ton", "sludgeOutsource04Ton", "sludgeOutsource05Ton", "sludgeOutsource06Ton", "sludgeOutsource07Ton", "sludgeOutsource08Ton", "sludgeOutsource09Ton", "sludgeOutsource10Ton", "sludgeOutsource11Ton", "inspectionFeeQty");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("runHours", BigDecimal.class),
            Map.entry("steamProducedTon", BigDecimal.class),
            Map.entry("sludgeBurnTon", BigDecimal.class),
            Map.entry("srfBurnTon", BigDecimal.class),
            Map.entry("steamUnitPrice", BigDecimal.class),
            Map.entry("stopHourUnitPrice", BigDecimal.class),
            Map.entry("ammoniumCarbonateInKg", BigDecimal.class),
            Map.entry("ammoniumCarbonateUseKg", BigDecimal.class),
            Map.entry("boilerChemInKg", BigDecimal.class),
            Map.entry("boilerChemUseKg", BigDecimal.class),
            Map.entry("activatedCarbonInKg", BigDecimal.class),
            Map.entry("activatedCarbonUseKg", BigDecimal.class),
            Map.entry("causticSodaInKg", BigDecimal.class),
            Map.entry("causticSodaUseKg", BigDecimal.class),
            Map.entry("slakedLimeInKg", BigDecimal.class),
            Map.entry("slakedLimeUseKg", BigDecimal.class),
            Map.entry("saltInKg", BigDecimal.class),
            Map.entry("saltUseKg", BigDecimal.class),
            Map.entry("dieselInL", BigDecimal.class),
            Map.entry("dieselUseL", BigDecimal.class),
            Map.entry("silicaSandInKg", BigDecimal.class),
            Map.entry("silicaSandUseKg", BigDecimal.class),
            Map.entry("activatedCarbonExtraInKg", BigDecimal.class),
            Map.entry("emergencyDieselInL", BigDecimal.class),
            Map.entry("srfInTon", BigDecimal.class),
            Map.entry("flyAshGeneratedTon", BigDecimal.class),
            Map.entry("flyAshOut01Ton", BigDecimal.class),
            Map.entry("flyAshOut02Ton", BigDecimal.class),
            Map.entry("flyAshOut03Ton", BigDecimal.class),
            Map.entry("flyAshOut04Ton", BigDecimal.class),
            Map.entry("flyAshOut05Ton", BigDecimal.class),
            Map.entry("flyAshOut06Ton", BigDecimal.class),
            Map.entry("flyAshOut07Ton", BigDecimal.class),
            Map.entry("flyAshOut08Ton", BigDecimal.class),
            Map.entry("bottomAshGeneratedTon", BigDecimal.class),
            Map.entry("bottomAshOut01Ton", BigDecimal.class),
            Map.entry("bottomAshOut02Ton", BigDecimal.class),
            Map.entry("bottomAshOut03Ton", BigDecimal.class),
            Map.entry("bottomAshOut04Ton", BigDecimal.class),
            Map.entry("powerMainKwh", BigDecimal.class),
            Map.entry("powerAshVentFilterKwh", BigDecimal.class),
            Map.entry("powerBagFilterKwh", BigDecimal.class),
            Map.entry("powerScrubberKwh", BigDecimal.class),
            Map.entry("powerCarbonFeedKwh", BigDecimal.class),
            Map.entry("powerLimeFeedKwh", BigDecimal.class),
            Map.entry("powerUreaKwh", BigDecimal.class),
            Map.entry("powerFdFanKwh", BigDecimal.class),
            Map.entry("reuseWaterTon", BigDecimal.class),
            Map.entry("riverbedWaterTon", BigDecimal.class),
            Map.entry("condensateTon", BigDecimal.class),
            Map.entry("boilerFeedWaterTon", BigDecimal.class),
            Map.entry("wasteWaterTon", BigDecimal.class),
            Map.entry("softWaterTon", BigDecimal.class),
            Map.entry("sludgeOutsource01Ton", BigDecimal.class),
            Map.entry("sludgeOutsource02Ton", BigDecimal.class),
            Map.entry("sludgeOutsource03Ton", BigDecimal.class),
            Map.entry("sludgeOutsource04Ton", BigDecimal.class),
            Map.entry("sludgeOutsource05Ton", BigDecimal.class),
            Map.entry("sludgeOutsource06Ton", BigDecimal.class),
            Map.entry("sludgeOutsource07Ton", BigDecimal.class),
            Map.entry("sludgeOutsource08Ton", BigDecimal.class),
            Map.entry("sludgeOutsource09Ton", BigDecimal.class),
            Map.entry("sludgeOutsource10Ton", BigDecimal.class),
            Map.entry("sludgeOutsource11Ton", BigDecimal.class),
            Map.entry("inspectionFeeQty", BigDecimal.class)
    );

    private static final Map<String, Function<SteamFluidizedDetailDailyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("runHours", r -> r.getRunHours()),
            Map.entry("steamProducedTon", r -> r.getSteamProducedTon()),
            Map.entry("sludgeBurnTon", r -> r.getSludgeBurnTon()),
            Map.entry("srfBurnTon", r -> r.getSrfBurnTon()),
            Map.entry("steamUnitPrice", r -> r.getSteamUnitPrice()),
            Map.entry("stopHourUnitPrice", r -> r.getStopHourUnitPrice()),
            Map.entry("ammoniumCarbonateInKg", r -> r.getAmmoniumCarbonateInKg()),
            Map.entry("ammoniumCarbonateUseKg", r -> r.getAmmoniumCarbonateUseKg()),
            Map.entry("boilerChemInKg", r -> r.getBoilerChemInKg()),
            Map.entry("boilerChemUseKg", r -> r.getBoilerChemUseKg()),
            Map.entry("activatedCarbonInKg", r -> r.getActivatedCarbonInKg()),
            Map.entry("activatedCarbonUseKg", r -> r.getActivatedCarbonUseKg()),
            Map.entry("causticSodaInKg", r -> r.getCausticSodaInKg()),
            Map.entry("causticSodaUseKg", r -> r.getCausticSodaUseKg()),
            Map.entry("slakedLimeInKg", r -> r.getSlakedLimeInKg()),
            Map.entry("slakedLimeUseKg", r -> r.getSlakedLimeUseKg()),
            Map.entry("saltInKg", r -> r.getSaltInKg()),
            Map.entry("saltUseKg", r -> r.getSaltUseKg()),
            Map.entry("dieselInL", r -> r.getDieselInL()),
            Map.entry("dieselUseL", r -> r.getDieselUseL()),
            Map.entry("silicaSandInKg", r -> r.getSilicaSandInKg()),
            Map.entry("silicaSandUseKg", r -> r.getSilicaSandUseKg()),
            Map.entry("activatedCarbonExtraInKg", r -> r.getActivatedCarbonExtraInKg()),
            Map.entry("emergencyDieselInL", r -> r.getEmergencyDieselInL()),
            Map.entry("srfInTon", r -> r.getSrfInTon()),
            Map.entry("flyAshGeneratedTon", r -> r.getFlyAshGeneratedTon()),
            Map.entry("flyAshOut01Ton", r -> r.getFlyAshOut01Ton()),
            Map.entry("flyAshOut02Ton", r -> r.getFlyAshOut02Ton()),
            Map.entry("flyAshOut03Ton", r -> r.getFlyAshOut03Ton()),
            Map.entry("flyAshOut04Ton", r -> r.getFlyAshOut04Ton()),
            Map.entry("flyAshOut05Ton", r -> r.getFlyAshOut05Ton()),
            Map.entry("flyAshOut06Ton", r -> r.getFlyAshOut06Ton()),
            Map.entry("flyAshOut07Ton", r -> r.getFlyAshOut07Ton()),
            Map.entry("flyAshOut08Ton", r -> r.getFlyAshOut08Ton()),
            Map.entry("bottomAshGeneratedTon", r -> r.getBottomAshGeneratedTon()),
            Map.entry("bottomAshOut01Ton", r -> r.getBottomAshOut01Ton()),
            Map.entry("bottomAshOut02Ton", r -> r.getBottomAshOut02Ton()),
            Map.entry("bottomAshOut03Ton", r -> r.getBottomAshOut03Ton()),
            Map.entry("bottomAshOut04Ton", r -> r.getBottomAshOut04Ton()),
            Map.entry("powerMainKwh", r -> r.getPowerMainKwh()),
            Map.entry("powerAshVentFilterKwh", r -> r.getPowerAshVentFilterKwh()),
            Map.entry("powerBagFilterKwh", r -> r.getPowerBagFilterKwh()),
            Map.entry("powerScrubberKwh", r -> r.getPowerScrubberKwh()),
            Map.entry("powerCarbonFeedKwh", r -> r.getPowerCarbonFeedKwh()),
            Map.entry("powerLimeFeedKwh", r -> r.getPowerLimeFeedKwh()),
            Map.entry("powerUreaKwh", r -> r.getPowerUreaKwh()),
            Map.entry("powerFdFanKwh", r -> r.getPowerFdFanKwh()),
            Map.entry("reuseWaterTon", r -> r.getReuseWaterTon()),
            Map.entry("riverbedWaterTon", r -> r.getRiverbedWaterTon()),
            Map.entry("condensateTon", r -> r.getCondensateTon()),
            Map.entry("boilerFeedWaterTon", r -> r.getBoilerFeedWaterTon()),
            Map.entry("wasteWaterTon", r -> r.getWasteWaterTon()),
            Map.entry("softWaterTon", r -> r.getSoftWaterTon()),
            Map.entry("sludgeOutsource01Ton", r -> r.getSludgeOutsource01Ton()),
            Map.entry("sludgeOutsource02Ton", r -> r.getSludgeOutsource02Ton()),
            Map.entry("sludgeOutsource03Ton", r -> r.getSludgeOutsource03Ton()),
            Map.entry("sludgeOutsource04Ton", r -> r.getSludgeOutsource04Ton()),
            Map.entry("sludgeOutsource05Ton", r -> r.getSludgeOutsource05Ton()),
            Map.entry("sludgeOutsource06Ton", r -> r.getSludgeOutsource06Ton()),
            Map.entry("sludgeOutsource07Ton", r -> r.getSludgeOutsource07Ton()),
            Map.entry("sludgeOutsource08Ton", r -> r.getSludgeOutsource08Ton()),
            Map.entry("sludgeOutsource09Ton", r -> r.getSludgeOutsource09Ton()),
            Map.entry("sludgeOutsource10Ton", r -> r.getSludgeOutsource10Ton()),
            Map.entry("sludgeOutsource11Ton", r -> r.getSludgeOutsource11Ton()),
            Map.entry("inspectionFeeQty", r -> r.getInspectionFeeQty())
    );

    private static final Map<String, BiConsumer<SteamFluidizedDetailDailySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("runHours", (r, v) -> r.setRunHours((BigDecimal) v)),
            Map.entry("steamProducedTon", (r, v) -> r.setSteamProducedTon((BigDecimal) v)),
            Map.entry("sludgeBurnTon", (r, v) -> r.setSludgeBurnTon((BigDecimal) v)),
            Map.entry("srfBurnTon", (r, v) -> r.setSrfBurnTon((BigDecimal) v)),
            Map.entry("steamUnitPrice", (r, v) -> r.setSteamUnitPrice((BigDecimal) v)),
            Map.entry("stopHourUnitPrice", (r, v) -> r.setStopHourUnitPrice((BigDecimal) v)),
            Map.entry("ammoniumCarbonateInKg", (r, v) -> r.setAmmoniumCarbonateInKg((BigDecimal) v)),
            Map.entry("ammoniumCarbonateUseKg", (r, v) -> r.setAmmoniumCarbonateUseKg((BigDecimal) v)),
            Map.entry("boilerChemInKg", (r, v) -> r.setBoilerChemInKg((BigDecimal) v)),
            Map.entry("boilerChemUseKg", (r, v) -> r.setBoilerChemUseKg((BigDecimal) v)),
            Map.entry("activatedCarbonInKg", (r, v) -> r.setActivatedCarbonInKg((BigDecimal) v)),
            Map.entry("activatedCarbonUseKg", (r, v) -> r.setActivatedCarbonUseKg((BigDecimal) v)),
            Map.entry("causticSodaInKg", (r, v) -> r.setCausticSodaInKg((BigDecimal) v)),
            Map.entry("causticSodaUseKg", (r, v) -> r.setCausticSodaUseKg((BigDecimal) v)),
            Map.entry("slakedLimeInKg", (r, v) -> r.setSlakedLimeInKg((BigDecimal) v)),
            Map.entry("slakedLimeUseKg", (r, v) -> r.setSlakedLimeUseKg((BigDecimal) v)),
            Map.entry("saltInKg", (r, v) -> r.setSaltInKg((BigDecimal) v)),
            Map.entry("saltUseKg", (r, v) -> r.setSaltUseKg((BigDecimal) v)),
            Map.entry("dieselInL", (r, v) -> r.setDieselInL((BigDecimal) v)),
            Map.entry("dieselUseL", (r, v) -> r.setDieselUseL((BigDecimal) v)),
            Map.entry("silicaSandInKg", (r, v) -> r.setSilicaSandInKg((BigDecimal) v)),
            Map.entry("silicaSandUseKg", (r, v) -> r.setSilicaSandUseKg((BigDecimal) v)),
            Map.entry("activatedCarbonExtraInKg", (r, v) -> r.setActivatedCarbonExtraInKg((BigDecimal) v)),
            Map.entry("emergencyDieselInL", (r, v) -> r.setEmergencyDieselInL((BigDecimal) v)),
            Map.entry("srfInTon", (r, v) -> r.setSrfInTon((BigDecimal) v)),
            Map.entry("flyAshGeneratedTon", (r, v) -> r.setFlyAshGeneratedTon((BigDecimal) v)),
            Map.entry("flyAshOut01Ton", (r, v) -> r.setFlyAshOut01Ton((BigDecimal) v)),
            Map.entry("flyAshOut02Ton", (r, v) -> r.setFlyAshOut02Ton((BigDecimal) v)),
            Map.entry("flyAshOut03Ton", (r, v) -> r.setFlyAshOut03Ton((BigDecimal) v)),
            Map.entry("flyAshOut04Ton", (r, v) -> r.setFlyAshOut04Ton((BigDecimal) v)),
            Map.entry("flyAshOut05Ton", (r, v) -> r.setFlyAshOut05Ton((BigDecimal) v)),
            Map.entry("flyAshOut06Ton", (r, v) -> r.setFlyAshOut06Ton((BigDecimal) v)),
            Map.entry("flyAshOut07Ton", (r, v) -> r.setFlyAshOut07Ton((BigDecimal) v)),
            Map.entry("flyAshOut08Ton", (r, v) -> r.setFlyAshOut08Ton((BigDecimal) v)),
            Map.entry("bottomAshGeneratedTon", (r, v) -> r.setBottomAshGeneratedTon((BigDecimal) v)),
            Map.entry("bottomAshOut01Ton", (r, v) -> r.setBottomAshOut01Ton((BigDecimal) v)),
            Map.entry("bottomAshOut02Ton", (r, v) -> r.setBottomAshOut02Ton((BigDecimal) v)),
            Map.entry("bottomAshOut03Ton", (r, v) -> r.setBottomAshOut03Ton((BigDecimal) v)),
            Map.entry("bottomAshOut04Ton", (r, v) -> r.setBottomAshOut04Ton((BigDecimal) v)),
            Map.entry("powerMainKwh", (r, v) -> r.setPowerMainKwh((BigDecimal) v)),
            Map.entry("powerAshVentFilterKwh", (r, v) -> r.setPowerAshVentFilterKwh((BigDecimal) v)),
            Map.entry("powerBagFilterKwh", (r, v) -> r.setPowerBagFilterKwh((BigDecimal) v)),
            Map.entry("powerScrubberKwh", (r, v) -> r.setPowerScrubberKwh((BigDecimal) v)),
            Map.entry("powerCarbonFeedKwh", (r, v) -> r.setPowerCarbonFeedKwh((BigDecimal) v)),
            Map.entry("powerLimeFeedKwh", (r, v) -> r.setPowerLimeFeedKwh((BigDecimal) v)),
            Map.entry("powerUreaKwh", (r, v) -> r.setPowerUreaKwh((BigDecimal) v)),
            Map.entry("powerFdFanKwh", (r, v) -> r.setPowerFdFanKwh((BigDecimal) v)),
            Map.entry("reuseWaterTon", (r, v) -> r.setReuseWaterTon((BigDecimal) v)),
            Map.entry("riverbedWaterTon", (r, v) -> r.setRiverbedWaterTon((BigDecimal) v)),
            Map.entry("condensateTon", (r, v) -> r.setCondensateTon((BigDecimal) v)),
            Map.entry("boilerFeedWaterTon", (r, v) -> r.setBoilerFeedWaterTon((BigDecimal) v)),
            Map.entry("wasteWaterTon", (r, v) -> r.setWasteWaterTon((BigDecimal) v)),
            Map.entry("softWaterTon", (r, v) -> r.setSoftWaterTon((BigDecimal) v)),
            Map.entry("sludgeOutsource01Ton", (r, v) -> r.setSludgeOutsource01Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource02Ton", (r, v) -> r.setSludgeOutsource02Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource03Ton", (r, v) -> r.setSludgeOutsource03Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource04Ton", (r, v) -> r.setSludgeOutsource04Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource05Ton", (r, v) -> r.setSludgeOutsource05Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource06Ton", (r, v) -> r.setSludgeOutsource06Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource07Ton", (r, v) -> r.setSludgeOutsource07Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource08Ton", (r, v) -> r.setSludgeOutsource08Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource09Ton", (r, v) -> r.setSludgeOutsource09Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource10Ton", (r, v) -> r.setSludgeOutsource10Ton((BigDecimal) v)),
            Map.entry("sludgeOutsource11Ton", (r, v) -> r.setSludgeOutsource11Ton((BigDecimal) v)),
            Map.entry("inspectionFeeQty", (r, v) -> r.setInspectionFeeQty((BigDecimal) v))
    );

    private final SteamFluidizedDetailDailyService service;

    public SteamFluidizedDetailDailyAdapter(SteamFluidizedDetailDailyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidDetailDaily"; }

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
        for (SteamFluidizedDetailDailyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedDetailDailyBulkRequest request = new SteamFluidizedDetailDailyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedDetailDailySaveRequest r = new SteamFluidizedDetailDailySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedDetailDailySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
