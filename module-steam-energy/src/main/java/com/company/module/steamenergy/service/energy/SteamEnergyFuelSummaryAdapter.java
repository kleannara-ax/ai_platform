package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyFuelSummaryResponse;
import com.company.module.steamenergy.dto.SteamEnergyFuelSummarySaveRequest;
import com.company.module.steamenergy.service.SteamEnergyFuelSummaryService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 연료비 요약 — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_fuel_summary / alias: fuelSummary
 */
@Component
public class SteamEnergyFuelSummaryAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyFuelSummaryResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("t1Pm2Prod", r -> r.getT1Pm2Prod()),
            Map.entry("t1Pm2Fuel", r -> r.getT1Pm2Fuel()),
            Map.entry("t1Pm3Prod", r -> r.getT1Pm3Prod()),
            Map.entry("t1Pm3Fuel", r -> r.getT1Pm3Fuel()),
            Map.entry("t1Tm3Prod", r -> r.getT1Tm3Prod()),
            Map.entry("t1Tm3Fuel", r -> r.getT1Tm3Fuel()),
            Map.entry("t1Tm4Prod", r -> r.getT1Tm4Prod()),
            Map.entry("t1Tm4Fuel", r -> r.getT1Tm4Fuel()),
            Map.entry("t1Tm5Prod", r -> r.getT1Tm5Prod()),
            Map.entry("t1Tm5Fuel", r -> r.getT1Tm5Fuel()),
            Map.entry("t2LngUsage", r -> r.getT2LngUsage()),
            Map.entry("t2LngPrice", r -> r.getT2LngPrice()),
            Map.entry("t2LngCost", r -> r.getT2LngCost()),
            Map.entry("t2CompositeUsage", r -> r.getT2CompositeUsage()),
            Map.entry("t2CompositePrice", r -> r.getT2CompositePrice()),
            Map.entry("t2CompositeCost", r -> r.getT2CompositeCost()),
            Map.entry("t2IncineratorUsage", r -> r.getT2IncineratorUsage()),
            Map.entry("t2IncineratorPrice", r -> r.getT2IncineratorPrice()),
            Map.entry("t2IncineratorCost", r -> r.getT2IncineratorCost()),
            Map.entry("t2FluidUsage", r -> r.getT2FluidUsage()),
            Map.entry("t2FluidPrice", r -> r.getT2FluidPrice()),
            Map.entry("t2FluidCost", r -> r.getT2FluidCost()),
            Map.entry("t2ExtUsage", r -> r.getT2ExtUsage()),
            Map.entry("t2ExtPrice", r -> r.getT2ExtPrice()),
            Map.entry("t2ExtCost", r -> r.getT2ExtCost()),
            Map.entry("t2LngElec", r -> r.getT2LngElec()),
            Map.entry("t2LngPenalty", r -> r.getT2LngPenalty()),
            Map.entry("t2LngOverprice", r -> r.getT2LngOverprice()),
            Map.entry("t2CompositeElec", r -> r.getT2CompositeElec()),
            Map.entry("t2CompositePenalty", r -> r.getT2CompositePenalty()),
            Map.entry("t2CompositeOverprice", r -> r.getT2CompositeOverprice()),
            Map.entry("t2IncineratorElec", r -> r.getT2IncineratorElec()),
            Map.entry("t2IncineratorPenalty", r -> r.getT2IncineratorPenalty()),
            Map.entry("t2IncineratorOverprice", r -> r.getT2IncineratorOverprice()),
            Map.entry("t2FluidElec", r -> r.getT2FluidElec()),
            Map.entry("t2FluidPenalty", r -> r.getT2FluidPenalty()),
            Map.entry("t2FluidOverprice", r -> r.getT2FluidOverprice()),
            Map.entry("t2ExtElec", r -> r.getT2ExtElec()),
            Map.entry("t2ExtPenalty", r -> r.getT2ExtPenalty()),
            Map.entry("t2ExtOverprice", r -> r.getT2ExtOverprice()),
            Map.entry("t2SubtotalElec", r -> r.getT2SubtotalElec()),
            Map.entry("t2SubtotalPenalty", r -> r.getT2SubtotalPenalty()),
            Map.entry("t2SubtotalOverprice", r -> r.getT2SubtotalOverprice()),
            Map.entry("t2BurnerLngElec", r -> r.getT2BurnerLngElec()),
            Map.entry("t2BurnerLngPenalty", r -> r.getT2BurnerLngPenalty()),
            Map.entry("t2BurnerLngOverprice", r -> r.getT2BurnerLngOverprice()),
            Map.entry("t2TotalElec", r -> r.getT2TotalElec()),
            Map.entry("t2TotalPenalty", r -> r.getT2TotalPenalty()),
            Map.entry("t2TotalOverprice", r -> r.getT2TotalOverprice())
    );
    private static final Map<String, BiConsumer<SteamEnergyFuelSummarySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("t1Pm2Prod", (r, v) -> r.setT1Pm2Prod((BigDecimal) v)),
            Map.entry("t1Pm2Fuel", (r, v) -> r.setT1Pm2Fuel((BigDecimal) v)),
            Map.entry("t1Pm3Prod", (r, v) -> r.setT1Pm3Prod((BigDecimal) v)),
            Map.entry("t1Pm3Fuel", (r, v) -> r.setT1Pm3Fuel((BigDecimal) v)),
            Map.entry("t1Tm3Prod", (r, v) -> r.setT1Tm3Prod((BigDecimal) v)),
            Map.entry("t1Tm3Fuel", (r, v) -> r.setT1Tm3Fuel((BigDecimal) v)),
            Map.entry("t1Tm4Prod", (r, v) -> r.setT1Tm4Prod((BigDecimal) v)),
            Map.entry("t1Tm4Fuel", (r, v) -> r.setT1Tm4Fuel((BigDecimal) v)),
            Map.entry("t1Tm5Prod", (r, v) -> r.setT1Tm5Prod((BigDecimal) v)),
            Map.entry("t1Tm5Fuel", (r, v) -> r.setT1Tm5Fuel((BigDecimal) v)),
            Map.entry("t2LngUsage", (r, v) -> r.setT2LngUsage((BigDecimal) v)),
            Map.entry("t2LngPrice", (r, v) -> r.setT2LngPrice((BigDecimal) v)),
            Map.entry("t2LngCost", (r, v) -> r.setT2LngCost((BigDecimal) v)),
            Map.entry("t2CompositeUsage", (r, v) -> r.setT2CompositeUsage((BigDecimal) v)),
            Map.entry("t2CompositePrice", (r, v) -> r.setT2CompositePrice((BigDecimal) v)),
            Map.entry("t2CompositeCost", (r, v) -> r.setT2CompositeCost((BigDecimal) v)),
            Map.entry("t2IncineratorUsage", (r, v) -> r.setT2IncineratorUsage((BigDecimal) v)),
            Map.entry("t2IncineratorPrice", (r, v) -> r.setT2IncineratorPrice((BigDecimal) v)),
            Map.entry("t2IncineratorCost", (r, v) -> r.setT2IncineratorCost((BigDecimal) v)),
            Map.entry("t2FluidUsage", (r, v) -> r.setT2FluidUsage((BigDecimal) v)),
            Map.entry("t2FluidPrice", (r, v) -> r.setT2FluidPrice((BigDecimal) v)),
            Map.entry("t2FluidCost", (r, v) -> r.setT2FluidCost((BigDecimal) v)),
            Map.entry("t2ExtUsage", (r, v) -> r.setT2ExtUsage((BigDecimal) v)),
            Map.entry("t2ExtPrice", (r, v) -> r.setT2ExtPrice((BigDecimal) v)),
            Map.entry("t2ExtCost", (r, v) -> r.setT2ExtCost((BigDecimal) v)),
            Map.entry("t2LngElec", (r, v) -> r.setT2LngElec((BigDecimal) v)),
            Map.entry("t2LngPenalty", (r, v) -> r.setT2LngPenalty((BigDecimal) v)),
            Map.entry("t2LngOverprice", (r, v) -> r.setT2LngOverprice((BigDecimal) v)),
            Map.entry("t2CompositeElec", (r, v) -> r.setT2CompositeElec((BigDecimal) v)),
            Map.entry("t2CompositePenalty", (r, v) -> r.setT2CompositePenalty((BigDecimal) v)),
            Map.entry("t2CompositeOverprice", (r, v) -> r.setT2CompositeOverprice((BigDecimal) v)),
            Map.entry("t2IncineratorElec", (r, v) -> r.setT2IncineratorElec((BigDecimal) v)),
            Map.entry("t2IncineratorPenalty", (r, v) -> r.setT2IncineratorPenalty((BigDecimal) v)),
            Map.entry("t2IncineratorOverprice", (r, v) -> r.setT2IncineratorOverprice((BigDecimal) v)),
            Map.entry("t2FluidElec", (r, v) -> r.setT2FluidElec((BigDecimal) v)),
            Map.entry("t2FluidPenalty", (r, v) -> r.setT2FluidPenalty((BigDecimal) v)),
            Map.entry("t2FluidOverprice", (r, v) -> r.setT2FluidOverprice((BigDecimal) v)),
            Map.entry("t2ExtElec", (r, v) -> r.setT2ExtElec((BigDecimal) v)),
            Map.entry("t2ExtPenalty", (r, v) -> r.setT2ExtPenalty((BigDecimal) v)),
            Map.entry("t2ExtOverprice", (r, v) -> r.setT2ExtOverprice((BigDecimal) v)),
            Map.entry("t2SubtotalElec", (r, v) -> r.setT2SubtotalElec((BigDecimal) v)),
            Map.entry("t2SubtotalPenalty", (r, v) -> r.setT2SubtotalPenalty((BigDecimal) v)),
            Map.entry("t2SubtotalOverprice", (r, v) -> r.setT2SubtotalOverprice((BigDecimal) v)),
            Map.entry("t2BurnerLngElec", (r, v) -> r.setT2BurnerLngElec((BigDecimal) v)),
            Map.entry("t2BurnerLngPenalty", (r, v) -> r.setT2BurnerLngPenalty((BigDecimal) v)),
            Map.entry("t2BurnerLngOverprice", (r, v) -> r.setT2BurnerLngOverprice((BigDecimal) v)),
            Map.entry("t2TotalElec", (r, v) -> r.setT2TotalElec((BigDecimal) v)),
            Map.entry("t2TotalPenalty", (r, v) -> r.setT2TotalPenalty((BigDecimal) v)),
            Map.entry("t2TotalOverprice", (r, v) -> r.setT2TotalOverprice((BigDecimal) v))
    );

    private final SteamEnergyFuelSummaryService service;

    public SteamEnergyFuelSummaryAdapter(SteamEnergyFuelSummaryService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fuelSummary"; }

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
        SteamEnergyFuelSummarySaveRequest request = new SteamEnergyFuelSummarySaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyFuelSummarySaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
