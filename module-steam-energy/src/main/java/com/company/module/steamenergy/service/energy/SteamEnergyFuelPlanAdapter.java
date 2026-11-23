package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyFuelPlanResponse;
import com.company.module.steamenergy.dto.SteamEnergyFuelPlanSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyFuelPlanService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 연료비 사업계획 — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_fuel_plan / alias: fuelPlan
 */
@Component
public class SteamEnergyFuelPlanAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyFuelPlanResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("paperProdTon", r -> r.getPaperProdTon()),
            Map.entry("tissueProdTon", r -> r.getTissueProdTon()),
            Map.entry("paperUnit", r -> r.getPaperUnit()),
            Map.entry("tissueUnit", r -> r.getTissueUnit()),
            Map.entry("paperTotalSteam", r -> r.getPaperTotalSteam()),
            Map.entry("tissueTotalSteam", r -> r.getTissueTotalSteam()),
            Map.entry("etcDisperser", r -> r.getEtcDisperser()),
            Map.entry("etcToc", r -> r.getEtcToc()),
            Map.entry("operatingDays", r -> r.getOperatingDays()),
            Map.entry("prodIncinerator", r -> r.getProdIncinerator()),
            Map.entry("prodComposite", r -> r.getProdComposite()),
            Map.entry("prodExtBoiler", r -> r.getProdExtBoiler()),
            Map.entry("prodFluid", r -> r.getProdFluid()),
            Map.entry("lngComposite", r -> r.getLngComposite()),
            Map.entry("lngTm3", r -> r.getLngTm3()),
            Map.entry("lngTm4", r -> r.getLngTm4()),
            Map.entry("lngTm5", r -> r.getLngTm5()),
            Map.entry("lngPm3", r -> r.getLngPm3()),
            Map.entry("priceLng", r -> r.getPriceLng()),
            Map.entry("priceIncinerator", r -> r.getPriceIncinerator()),
            Map.entry("priceComposite", r -> r.getPriceComposite()),
            Map.entry("priceExtBoiler", r -> r.getPriceExtBoiler()),
            Map.entry("priceFluid", r -> r.getPriceFluid()),
            Map.entry("opexIncinerator", r -> r.getOpexIncinerator()),
            Map.entry("opexFluid", r -> r.getOpexFluid()),
            Map.entry("payExtAdvance", r -> r.getPayExtAdvance()),
            Map.entry("payExtPenalty", r -> r.getPayExtPenalty()),
            Map.entry("payIncinerator", r -> r.getPayIncinerator()),
            Map.entry("payFluid", r -> r.getPayFluid())
    );
    private static final Map<String, BiConsumer<SteamEnergyFuelPlanSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("paperProdTon", (r, v) -> r.setPaperProdTon((BigDecimal) v)),
            Map.entry("tissueProdTon", (r, v) -> r.setTissueProdTon((BigDecimal) v)),
            Map.entry("paperUnit", (r, v) -> r.setPaperUnit((BigDecimal) v)),
            Map.entry("tissueUnit", (r, v) -> r.setTissueUnit((BigDecimal) v)),
            Map.entry("paperTotalSteam", (r, v) -> r.setPaperTotalSteam((BigDecimal) v)),
            Map.entry("tissueTotalSteam", (r, v) -> r.setTissueTotalSteam((BigDecimal) v)),
            Map.entry("etcDisperser", (r, v) -> r.setEtcDisperser((BigDecimal) v)),
            Map.entry("etcToc", (r, v) -> r.setEtcToc((BigDecimal) v)),
            Map.entry("operatingDays", (r, v) -> r.setOperatingDays((BigDecimal) v)),
            Map.entry("prodIncinerator", (r, v) -> r.setProdIncinerator((BigDecimal) v)),
            Map.entry("prodComposite", (r, v) -> r.setProdComposite((BigDecimal) v)),
            Map.entry("prodExtBoiler", (r, v) -> r.setProdExtBoiler((BigDecimal) v)),
            Map.entry("prodFluid", (r, v) -> r.setProdFluid((BigDecimal) v)),
            Map.entry("lngComposite", (r, v) -> r.setLngComposite((BigDecimal) v)),
            Map.entry("lngTm3", (r, v) -> r.setLngTm3((BigDecimal) v)),
            Map.entry("lngTm4", (r, v) -> r.setLngTm4((BigDecimal) v)),
            Map.entry("lngTm5", (r, v) -> r.setLngTm5((BigDecimal) v)),
            Map.entry("lngPm3", (r, v) -> r.setLngPm3((BigDecimal) v)),
            Map.entry("priceLng", (r, v) -> r.setPriceLng((BigDecimal) v)),
            Map.entry("priceIncinerator", (r, v) -> r.setPriceIncinerator((BigDecimal) v)),
            Map.entry("priceComposite", (r, v) -> r.setPriceComposite((BigDecimal) v)),
            Map.entry("priceExtBoiler", (r, v) -> r.setPriceExtBoiler((BigDecimal) v)),
            Map.entry("priceFluid", (r, v) -> r.setPriceFluid((BigDecimal) v)),
            Map.entry("opexIncinerator", (r, v) -> r.setOpexIncinerator((BigDecimal) v)),
            Map.entry("opexFluid", (r, v) -> r.setOpexFluid((BigDecimal) v)),
            Map.entry("payExtAdvance", (r, v) -> r.setPayExtAdvance((BigDecimal) v)),
            Map.entry("payExtPenalty", (r, v) -> r.setPayExtPenalty((BigDecimal) v)),
            Map.entry("payIncinerator", (r, v) -> r.setPayIncinerator((BigDecimal) v)),
            Map.entry("payFluid", (r, v) -> r.setPayFluid((BigDecimal) v))
    );

    private final SteamEnergyFuelPlanService service;

    public SteamEnergyFuelPlanAdapter(SteamEnergyFuelPlanService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fuelPlan"; }

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
        SteamEnergyFuelPlanSaveRequest request = new SteamEnergyFuelPlanSaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyFuelPlanSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
