package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyRatioBurnerService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 연료비 배분비율(버너 LNG) — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_ratio_burner / alias: ratioBurner
 */
@Component
public class SteamEnergyRatioBurnerAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyRatioBurnerResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("lngPm3", r -> r.getLngPm3()),
            Map.entry("lngTm3", r -> r.getLngTm3()),
            Map.entry("lngTm4", r -> r.getLngTm4()),
            Map.entry("lngTm5", r -> r.getLngTm5()),
            Map.entry("lngPricePrevYear", r -> r.getLngPricePrevYear()),
            Map.entry("lngPrice", r -> r.getLngPrice()),
            Map.entry("lngPriceChangeRate", r -> r.getLngPriceChangeRate()),
            Map.entry("lngCostTotal", r -> r.getLngCostTotal()),
            Map.entry("lngUnitPm3", r -> r.getLngUnitPm3()),
            Map.entry("lngUnitTm3", r -> r.getLngUnitTm3()),
            Map.entry("lngUnitTm4", r -> r.getLngUnitTm4()),
            Map.entry("lngUnitTm5", r -> r.getLngUnitTm5()),
            Map.entry("lngUnitTissueSubtotal", r -> r.getLngUnitTissueSubtotal())
    );
    private static final Map<String, BiConsumer<SteamEnergyRatioBurnerSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("lngPm3", (r, v) -> r.setLngPm3((BigDecimal) v)),
            Map.entry("lngTm3", (r, v) -> r.setLngTm3((BigDecimal) v)),
            Map.entry("lngTm4", (r, v) -> r.setLngTm4((BigDecimal) v)),
            Map.entry("lngTm5", (r, v) -> r.setLngTm5((BigDecimal) v)),
            Map.entry("lngPricePrevYear", (r, v) -> r.setLngPricePrevYear((BigDecimal) v)),
            Map.entry("lngPrice", (r, v) -> r.setLngPrice((BigDecimal) v)),
            Map.entry("lngPriceChangeRate", (r, v) -> r.setLngPriceChangeRate((BigDecimal) v)),
            Map.entry("lngCostTotal", (r, v) -> r.setLngCostTotal((BigDecimal) v)),
            Map.entry("lngUnitPm3", (r, v) -> r.setLngUnitPm3((BigDecimal) v)),
            Map.entry("lngUnitTm3", (r, v) -> r.setLngUnitTm3((BigDecimal) v)),
            Map.entry("lngUnitTm4", (r, v) -> r.setLngUnitTm4((BigDecimal) v)),
            Map.entry("lngUnitTm5", (r, v) -> r.setLngUnitTm5((BigDecimal) v)),
            Map.entry("lngUnitTissueSubtotal", (r, v) -> r.setLngUnitTissueSubtotal((BigDecimal) v))
    );

    private final SteamEnergyRatioBurnerService service;

    public SteamEnergyRatioBurnerAdapter(SteamEnergyRatioBurnerService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "ratioBurner"; }

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
        SteamEnergyRatioBurnerSaveRequest request = new SteamEnergyRatioBurnerSaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyRatioBurnerSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
