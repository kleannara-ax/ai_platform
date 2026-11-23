package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyRatioBoilerResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBoilerSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyRatioBoilerService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 연료비 배분비율(보일러별) — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_ratio_boiler / alias: ratioBoiler
 */
@Component
public class SteamEnergyRatioBoilerAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyRatioBoilerResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("steamLngBoiler", r -> r.getSteamLngBoiler()),
            Map.entry("steamComposite", r -> r.getSteamComposite()),
            Map.entry("steamIncinerator", r -> r.getSteamIncinerator()),
            Map.entry("steamFluid", r -> r.getSteamFluid()),
            Map.entry("steamExtBoiler", r -> r.getSteamExtBoiler()),
            Map.entry("fluidVentTon", r -> r.getFluidVentTon()),
            Map.entry("compositePenaltyTon", r -> r.getCompositePenaltyTon()),
            Map.entry("compositeGuaranteeTon", r -> r.getCompositeGuaranteeTon()),
            Map.entry("costLngBoiler", r -> r.getCostLngBoiler()),
            Map.entry("costComposite", r -> r.getCostComposite()),
            Map.entry("costIncinerator", r -> r.getCostIncinerator()),
            Map.entry("costFluid", r -> r.getCostFluid()),
            Map.entry("costExtBoiler", r -> r.getCostExtBoiler()),
            Map.entry("fluidInternalVentCost", r -> r.getFluidInternalVentCost()),
            Map.entry("priceLngBoiler", r -> r.getPriceLngBoiler()),
            Map.entry("priceComposite", r -> r.getPriceComposite()),
            Map.entry("priceIncinerator", r -> r.getPriceIncinerator()),
            Map.entry("priceFluid", r -> r.getPriceFluid()),
            Map.entry("priceExtBoiler", r -> r.getPriceExtBoiler())
    );
    private static final Map<String, BiConsumer<SteamEnergyRatioBoilerSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("steamLngBoiler", (r, v) -> r.setSteamLngBoiler((BigDecimal) v)),
            Map.entry("steamComposite", (r, v) -> r.setSteamComposite((BigDecimal) v)),
            Map.entry("steamIncinerator", (r, v) -> r.setSteamIncinerator((BigDecimal) v)),
            Map.entry("steamFluid", (r, v) -> r.setSteamFluid((BigDecimal) v)),
            Map.entry("steamExtBoiler", (r, v) -> r.setSteamExtBoiler((BigDecimal) v)),
            Map.entry("fluidVentTon", (r, v) -> r.setFluidVentTon((BigDecimal) v)),
            Map.entry("compositePenaltyTon", (r, v) -> r.setCompositePenaltyTon((BigDecimal) v)),
            Map.entry("compositeGuaranteeTon", (r, v) -> r.setCompositeGuaranteeTon((BigDecimal) v)),
            Map.entry("costLngBoiler", (r, v) -> r.setCostLngBoiler((BigDecimal) v)),
            Map.entry("costComposite", (r, v) -> r.setCostComposite((BigDecimal) v)),
            Map.entry("costIncinerator", (r, v) -> r.setCostIncinerator((BigDecimal) v)),
            Map.entry("costFluid", (r, v) -> r.setCostFluid((BigDecimal) v)),
            Map.entry("costExtBoiler", (r, v) -> r.setCostExtBoiler((BigDecimal) v)),
            Map.entry("fluidInternalVentCost", (r, v) -> r.setFluidInternalVentCost((BigDecimal) v)),
            Map.entry("priceLngBoiler", (r, v) -> r.setPriceLngBoiler((BigDecimal) v)),
            Map.entry("priceComposite", (r, v) -> r.setPriceComposite((BigDecimal) v)),
            Map.entry("priceIncinerator", (r, v) -> r.setPriceIncinerator((BigDecimal) v)),
            Map.entry("priceFluid", (r, v) -> r.setPriceFluid((BigDecimal) v)),
            Map.entry("priceExtBoiler", (r, v) -> r.setPriceExtBoiler((BigDecimal) v))
    );

    private final SteamEnergyRatioBoilerService service;

    public SteamEnergyRatioBoilerAdapter(SteamEnergyRatioBoilerService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "ratioBoiler"; }

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
        SteamEnergyRatioBoilerSaveRequest request = new SteamEnergyRatioBoilerSaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyRatioBoilerSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
