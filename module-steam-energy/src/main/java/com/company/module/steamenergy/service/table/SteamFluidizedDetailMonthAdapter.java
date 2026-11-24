package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedDetailMonthService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 유동상 세부 운영내역 (월별 단가·비용) — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_detail_month / alias: fluidDetailMonth
 */
@Component
public class SteamFluidizedDetailMonthAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("improvementCost", "extraCost", "steamProducedUnitPrice", "ammoniumCarbonateUnitPrice", "boilerChemInUnitPrice", "boilerChemUseUnitPrice", "activatedCarbonInUnitPrice", "activatedCarbonUseUnitPrice", "causticSodaInUnitPrice", "causticSodaUseUnitPrice", "slakedLimeUnitPrice", "saltUnitPrice", "dieselUnitPrice", "silicaSandUseUnitPrice", "srfUnitPrice", "flyAshGeneratedUnitPrice", "flyAshOut01UnitPrice", "flyAshOut02UnitPrice", "flyAshOut03UnitPrice", "flyAshOut04UnitPrice", "flyAshOut05UnitPrice", "flyAshOut06UnitPrice", "flyAshOut07UnitPrice", "flyAshOut08UnitPrice", "bottomAshGeneratedUnitPrice", "bottomAshOut01UnitPrice", "bottomAshOut02UnitPrice", "bottomAshOut03UnitPrice", "bottomAshOut04UnitPrice", "powerUnitPrice", "powerUreaUnitPrice", "powerFdFanUnitPrice", "reuseWaterUnitPrice", "condensateUnitPrice", "boilerFeedWaterUnitPrice", "wasteWaterUnitPrice", "softWaterUnitPrice", "sludgeOutsource01UnitPrice", "sludgeOutsource02UnitPrice", "sludgeOutsource03UnitPrice", "sludgeOutsource04UnitPrice", "sludgeOutsource05UnitPrice", "sludgeOutsource06UnitPrice", "sludgeOutsource07UnitPrice", "sludgeOutsource08UnitPrice", "sludgeOutsource09UnitPrice", "sludgeOutsource10UnitPrice", "sludgeOutsource11UnitPrice", "inspectionFeeUnitPrice", "silicaSandUseNote");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("improvementCost", BigDecimal.class),
            Map.entry("extraCost", BigDecimal.class),
            Map.entry("steamProducedUnitPrice", BigDecimal.class),
            Map.entry("ammoniumCarbonateUnitPrice", BigDecimal.class),
            Map.entry("boilerChemInUnitPrice", BigDecimal.class),
            Map.entry("boilerChemUseUnitPrice", BigDecimal.class),
            Map.entry("activatedCarbonInUnitPrice", BigDecimal.class),
            Map.entry("activatedCarbonUseUnitPrice", BigDecimal.class),
            Map.entry("causticSodaInUnitPrice", BigDecimal.class),
            Map.entry("causticSodaUseUnitPrice", BigDecimal.class),
            Map.entry("slakedLimeUnitPrice", BigDecimal.class),
            Map.entry("saltUnitPrice", BigDecimal.class),
            Map.entry("dieselUnitPrice", BigDecimal.class),
            Map.entry("silicaSandUseUnitPrice", BigDecimal.class),
            Map.entry("srfUnitPrice", BigDecimal.class),
            Map.entry("flyAshGeneratedUnitPrice", BigDecimal.class),
            Map.entry("flyAshOut01UnitPrice", BigDecimal.class),
            Map.entry("flyAshOut02UnitPrice", BigDecimal.class),
            Map.entry("flyAshOut03UnitPrice", BigDecimal.class),
            Map.entry("flyAshOut04UnitPrice", BigDecimal.class),
            Map.entry("flyAshOut05UnitPrice", BigDecimal.class),
            Map.entry("flyAshOut06UnitPrice", BigDecimal.class),
            Map.entry("flyAshOut07UnitPrice", BigDecimal.class),
            Map.entry("flyAshOut08UnitPrice", BigDecimal.class),
            Map.entry("bottomAshGeneratedUnitPrice", BigDecimal.class),
            Map.entry("bottomAshOut01UnitPrice", BigDecimal.class),
            Map.entry("bottomAshOut02UnitPrice", BigDecimal.class),
            Map.entry("bottomAshOut03UnitPrice", BigDecimal.class),
            Map.entry("bottomAshOut04UnitPrice", BigDecimal.class),
            Map.entry("powerUnitPrice", BigDecimal.class),
            Map.entry("powerUreaUnitPrice", BigDecimal.class),
            Map.entry("powerFdFanUnitPrice", BigDecimal.class),
            Map.entry("reuseWaterUnitPrice", BigDecimal.class),
            Map.entry("condensateUnitPrice", BigDecimal.class),
            Map.entry("boilerFeedWaterUnitPrice", BigDecimal.class),
            Map.entry("wasteWaterUnitPrice", BigDecimal.class),
            Map.entry("softWaterUnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource01UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource02UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource03UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource04UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource05UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource06UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource07UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource08UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource09UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource10UnitPrice", BigDecimal.class),
            Map.entry("sludgeOutsource11UnitPrice", BigDecimal.class),
            Map.entry("inspectionFeeUnitPrice", BigDecimal.class),
            Map.entry("silicaSandUseNote", String.class)
    );

    private static final Map<String, Function<SteamFluidizedDetailMonthResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("improvementCost", r -> r.getImprovementCost()),
            Map.entry("extraCost", r -> r.getExtraCost()),
            Map.entry("steamProducedUnitPrice", r -> r.getSteamProducedUnitPrice()),
            Map.entry("ammoniumCarbonateUnitPrice", r -> r.getAmmoniumCarbonateUnitPrice()),
            Map.entry("boilerChemInUnitPrice", r -> r.getBoilerChemInUnitPrice()),
            Map.entry("boilerChemUseUnitPrice", r -> r.getBoilerChemUseUnitPrice()),
            Map.entry("activatedCarbonInUnitPrice", r -> r.getActivatedCarbonInUnitPrice()),
            Map.entry("activatedCarbonUseUnitPrice", r -> r.getActivatedCarbonUseUnitPrice()),
            Map.entry("causticSodaInUnitPrice", r -> r.getCausticSodaInUnitPrice()),
            Map.entry("causticSodaUseUnitPrice", r -> r.getCausticSodaUseUnitPrice()),
            Map.entry("slakedLimeUnitPrice", r -> r.getSlakedLimeUnitPrice()),
            Map.entry("saltUnitPrice", r -> r.getSaltUnitPrice()),
            Map.entry("dieselUnitPrice", r -> r.getDieselUnitPrice()),
            Map.entry("silicaSandUseUnitPrice", r -> r.getSilicaSandUseUnitPrice()),
            Map.entry("srfUnitPrice", r -> r.getSrfUnitPrice()),
            Map.entry("flyAshGeneratedUnitPrice", r -> r.getFlyAshGeneratedUnitPrice()),
            Map.entry("flyAshOut01UnitPrice", r -> r.getFlyAshOut01UnitPrice()),
            Map.entry("flyAshOut02UnitPrice", r -> r.getFlyAshOut02UnitPrice()),
            Map.entry("flyAshOut03UnitPrice", r -> r.getFlyAshOut03UnitPrice()),
            Map.entry("flyAshOut04UnitPrice", r -> r.getFlyAshOut04UnitPrice()),
            Map.entry("flyAshOut05UnitPrice", r -> r.getFlyAshOut05UnitPrice()),
            Map.entry("flyAshOut06UnitPrice", r -> r.getFlyAshOut06UnitPrice()),
            Map.entry("flyAshOut07UnitPrice", r -> r.getFlyAshOut07UnitPrice()),
            Map.entry("flyAshOut08UnitPrice", r -> r.getFlyAshOut08UnitPrice()),
            Map.entry("bottomAshGeneratedUnitPrice", r -> r.getBottomAshGeneratedUnitPrice()),
            Map.entry("bottomAshOut01UnitPrice", r -> r.getBottomAshOut01UnitPrice()),
            Map.entry("bottomAshOut02UnitPrice", r -> r.getBottomAshOut02UnitPrice()),
            Map.entry("bottomAshOut03UnitPrice", r -> r.getBottomAshOut03UnitPrice()),
            Map.entry("bottomAshOut04UnitPrice", r -> r.getBottomAshOut04UnitPrice()),
            Map.entry("powerUnitPrice", r -> r.getPowerUnitPrice()),
            Map.entry("powerUreaUnitPrice", r -> r.getPowerUreaUnitPrice()),
            Map.entry("powerFdFanUnitPrice", r -> r.getPowerFdFanUnitPrice()),
            Map.entry("reuseWaterUnitPrice", r -> r.getReuseWaterUnitPrice()),
            Map.entry("condensateUnitPrice", r -> r.getCondensateUnitPrice()),
            Map.entry("boilerFeedWaterUnitPrice", r -> r.getBoilerFeedWaterUnitPrice()),
            Map.entry("wasteWaterUnitPrice", r -> r.getWasteWaterUnitPrice()),
            Map.entry("softWaterUnitPrice", r -> r.getSoftWaterUnitPrice()),
            Map.entry("sludgeOutsource01UnitPrice", r -> r.getSludgeOutsource01UnitPrice()),
            Map.entry("sludgeOutsource02UnitPrice", r -> r.getSludgeOutsource02UnitPrice()),
            Map.entry("sludgeOutsource03UnitPrice", r -> r.getSludgeOutsource03UnitPrice()),
            Map.entry("sludgeOutsource04UnitPrice", r -> r.getSludgeOutsource04UnitPrice()),
            Map.entry("sludgeOutsource05UnitPrice", r -> r.getSludgeOutsource05UnitPrice()),
            Map.entry("sludgeOutsource06UnitPrice", r -> r.getSludgeOutsource06UnitPrice()),
            Map.entry("sludgeOutsource07UnitPrice", r -> r.getSludgeOutsource07UnitPrice()),
            Map.entry("sludgeOutsource08UnitPrice", r -> r.getSludgeOutsource08UnitPrice()),
            Map.entry("sludgeOutsource09UnitPrice", r -> r.getSludgeOutsource09UnitPrice()),
            Map.entry("sludgeOutsource10UnitPrice", r -> r.getSludgeOutsource10UnitPrice()),
            Map.entry("sludgeOutsource11UnitPrice", r -> r.getSludgeOutsource11UnitPrice()),
            Map.entry("inspectionFeeUnitPrice", r -> r.getInspectionFeeUnitPrice()),
            Map.entry("silicaSandUseNote", r -> r.getSilicaSandUseNote())
    );

    private static final Map<String, BiConsumer<SteamFluidizedDetailMonthSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("improvementCost", (r, v) -> r.setImprovementCost((BigDecimal) v)),
            Map.entry("extraCost", (r, v) -> r.setExtraCost((BigDecimal) v)),
            Map.entry("steamProducedUnitPrice", (r, v) -> r.setSteamProducedUnitPrice((BigDecimal) v)),
            Map.entry("ammoniumCarbonateUnitPrice", (r, v) -> r.setAmmoniumCarbonateUnitPrice((BigDecimal) v)),
            Map.entry("boilerChemInUnitPrice", (r, v) -> r.setBoilerChemInUnitPrice((BigDecimal) v)),
            Map.entry("boilerChemUseUnitPrice", (r, v) -> r.setBoilerChemUseUnitPrice((BigDecimal) v)),
            Map.entry("activatedCarbonInUnitPrice", (r, v) -> r.setActivatedCarbonInUnitPrice((BigDecimal) v)),
            Map.entry("activatedCarbonUseUnitPrice", (r, v) -> r.setActivatedCarbonUseUnitPrice((BigDecimal) v)),
            Map.entry("causticSodaInUnitPrice", (r, v) -> r.setCausticSodaInUnitPrice((BigDecimal) v)),
            Map.entry("causticSodaUseUnitPrice", (r, v) -> r.setCausticSodaUseUnitPrice((BigDecimal) v)),
            Map.entry("slakedLimeUnitPrice", (r, v) -> r.setSlakedLimeUnitPrice((BigDecimal) v)),
            Map.entry("saltUnitPrice", (r, v) -> r.setSaltUnitPrice((BigDecimal) v)),
            Map.entry("dieselUnitPrice", (r, v) -> r.setDieselUnitPrice((BigDecimal) v)),
            Map.entry("silicaSandUseUnitPrice", (r, v) -> r.setSilicaSandUseUnitPrice((BigDecimal) v)),
            Map.entry("srfUnitPrice", (r, v) -> r.setSrfUnitPrice((BigDecimal) v)),
            Map.entry("flyAshGeneratedUnitPrice", (r, v) -> r.setFlyAshGeneratedUnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut01UnitPrice", (r, v) -> r.setFlyAshOut01UnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut02UnitPrice", (r, v) -> r.setFlyAshOut02UnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut03UnitPrice", (r, v) -> r.setFlyAshOut03UnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut04UnitPrice", (r, v) -> r.setFlyAshOut04UnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut05UnitPrice", (r, v) -> r.setFlyAshOut05UnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut06UnitPrice", (r, v) -> r.setFlyAshOut06UnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut07UnitPrice", (r, v) -> r.setFlyAshOut07UnitPrice((BigDecimal) v)),
            Map.entry("flyAshOut08UnitPrice", (r, v) -> r.setFlyAshOut08UnitPrice((BigDecimal) v)),
            Map.entry("bottomAshGeneratedUnitPrice", (r, v) -> r.setBottomAshGeneratedUnitPrice((BigDecimal) v)),
            Map.entry("bottomAshOut01UnitPrice", (r, v) -> r.setBottomAshOut01UnitPrice((BigDecimal) v)),
            Map.entry("bottomAshOut02UnitPrice", (r, v) -> r.setBottomAshOut02UnitPrice((BigDecimal) v)),
            Map.entry("bottomAshOut03UnitPrice", (r, v) -> r.setBottomAshOut03UnitPrice((BigDecimal) v)),
            Map.entry("bottomAshOut04UnitPrice", (r, v) -> r.setBottomAshOut04UnitPrice((BigDecimal) v)),
            Map.entry("powerUnitPrice", (r, v) -> r.setPowerUnitPrice((BigDecimal) v)),
            Map.entry("powerUreaUnitPrice", (r, v) -> r.setPowerUreaUnitPrice((BigDecimal) v)),
            Map.entry("powerFdFanUnitPrice", (r, v) -> r.setPowerFdFanUnitPrice((BigDecimal) v)),
            Map.entry("reuseWaterUnitPrice", (r, v) -> r.setReuseWaterUnitPrice((BigDecimal) v)),
            Map.entry("condensateUnitPrice", (r, v) -> r.setCondensateUnitPrice((BigDecimal) v)),
            Map.entry("boilerFeedWaterUnitPrice", (r, v) -> r.setBoilerFeedWaterUnitPrice((BigDecimal) v)),
            Map.entry("wasteWaterUnitPrice", (r, v) -> r.setWasteWaterUnitPrice((BigDecimal) v)),
            Map.entry("softWaterUnitPrice", (r, v) -> r.setSoftWaterUnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource01UnitPrice", (r, v) -> r.setSludgeOutsource01UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource02UnitPrice", (r, v) -> r.setSludgeOutsource02UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource03UnitPrice", (r, v) -> r.setSludgeOutsource03UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource04UnitPrice", (r, v) -> r.setSludgeOutsource04UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource05UnitPrice", (r, v) -> r.setSludgeOutsource05UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource06UnitPrice", (r, v) -> r.setSludgeOutsource06UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource07UnitPrice", (r, v) -> r.setSludgeOutsource07UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource08UnitPrice", (r, v) -> r.setSludgeOutsource08UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource09UnitPrice", (r, v) -> r.setSludgeOutsource09UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource10UnitPrice", (r, v) -> r.setSludgeOutsource10UnitPrice((BigDecimal) v)),
            Map.entry("sludgeOutsource11UnitPrice", (r, v) -> r.setSludgeOutsource11UnitPrice((BigDecimal) v)),
            Map.entry("inspectionFeeUnitPrice", (r, v) -> r.setInspectionFeeUnitPrice((BigDecimal) v)),
            Map.entry("silicaSandUseNote", (r, v) -> r.setSilicaSandUseNote((String) v))
    );

    private final SteamFluidizedDetailMonthService service;

    public SteamFluidizedDetailMonthAdapter(SteamFluidizedDetailMonthService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidDetailMonth"; }

    @Override
    public String time() { return "month"; }

    @Override
    public List<String> keyFields() { return KEY_FIELDS; }

    @Override
    public List<String> valueFields() { return VALUE_FIELDS; }

    @Override
    public Class<?> typeOf(String field) { return TYPES.get(field); }

    @Override
    public List<Map<String, Object>> load(int year, Integer month) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SteamFluidizedDetailMonthResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedDetailMonthBulkRequest request = new SteamFluidizedDetailMonthBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedDetailMonthSaveRequest r = new SteamFluidizedDetailMonthSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedDetailMonthSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
