package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyMonthCompareResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyMonthCompareService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 회계비용 마감비교 — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_month_compare / alias: monthCompare
 */
@Component
public class SteamEnergyMonthCompareAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyMonthCompareResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("zescoSteamBuy", r -> r.getZescoSteamBuy()),
            Map.entry("lngBuy", r -> r.getLngBuy()),
            Map.entry("zescoOffset", r -> r.getZescoOffset()),
            Map.entry("zescoPenalty", r -> r.getZescoPenalty()),
            Map.entry("fuelPlan", r -> r.getFuelPlan()),
            Map.entry("incineratorSteamBuy", r -> r.getIncineratorSteamBuy()),
            Map.entry("fluidSteamBuy", r -> r.getFluidSteamBuy()),
            Map.entry("fuelActual", r -> r.getFuelActual()),
            Map.entry("fuelSteamUnit", r -> r.getFuelSteamUnit())
    );
    private static final Map<String, BiConsumer<SteamEnergyMonthCompareSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("zescoSteamBuy", (r, v) -> r.setZescoSteamBuy((BigDecimal) v)),
            Map.entry("lngBuy", (r, v) -> r.setLngBuy((BigDecimal) v)),
            Map.entry("zescoOffset", (r, v) -> r.setZescoOffset((BigDecimal) v)),
            Map.entry("zescoPenalty", (r, v) -> r.setZescoPenalty((BigDecimal) v)),
            Map.entry("fuelPlan", (r, v) -> r.setFuelPlan((BigDecimal) v)),
            Map.entry("incineratorSteamBuy", (r, v) -> r.setIncineratorSteamBuy((BigDecimal) v)),
            Map.entry("fluidSteamBuy", (r, v) -> r.setFluidSteamBuy((BigDecimal) v)),
            Map.entry("fuelActual", (r, v) -> r.setFuelActual((BigDecimal) v)),
            Map.entry("fuelSteamUnit", (r, v) -> r.setFuelSteamUnit((BigDecimal) v))
    );

    private final SteamEnergyMonthCompareService service;

    public SteamEnergyMonthCompareAdapter(SteamEnergyMonthCompareService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "monthCompare"; }

    @Override
    public boolean monthly() { return true; }

    @Override
    public java.util.Set<String> fields() { return SETTERS.keySet(); }

    @Override
    public List<EnergyRow> load(int year) {
        return service.getList(year).stream().map(r -> {
            Map<String, Object> values = new LinkedHashMap<>();
            GETTERS.forEach((field, getter) -> values.put(field, getter.apply(r)));
            return new EnergyRow(r.getYearNo(), r.getMonthNo(), values);
        }).toList();
    }

    @Override
    public void save(int yearNo, Integer monthNo, Map<String, Object> values) {
        SteamEnergyMonthCompareSaveRequest request = new SteamEnergyMonthCompareSaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyMonthCompareSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
