package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyRatioSteamUnitResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioSteamUnitSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyRatioSteamUnitService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 스팀사용량 원단위(기타 포함) — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_ratio_steam_unit / alias: ratioUnit
 */
@Component
public class SteamEnergyRatioSteamUnitAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyRatioSteamUnitResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("unitPm2", r -> r.getUnitPm2()),
            Map.entry("unitPm3", r -> r.getUnitPm3()),
            Map.entry("unitPaperSubtotal", r -> r.getUnitPaperSubtotal()),
            Map.entry("unitTm3", r -> r.getUnitTm3()),
            Map.entry("unitTm4", r -> r.getUnitTm4()),
            Map.entry("unitTm5", r -> r.getUnitTm5()),
            Map.entry("unitTissueSubtotal", r -> r.getUnitTissueSubtotal())
    );
    private static final Map<String, BiConsumer<SteamEnergyRatioSteamUnitSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("unitPm2", (r, v) -> r.setUnitPm2((BigDecimal) v)),
            Map.entry("unitPm3", (r, v) -> r.setUnitPm3((BigDecimal) v)),
            Map.entry("unitPaperSubtotal", (r, v) -> r.setUnitPaperSubtotal((BigDecimal) v)),
            Map.entry("unitTm3", (r, v) -> r.setUnitTm3((BigDecimal) v)),
            Map.entry("unitTm4", (r, v) -> r.setUnitTm4((BigDecimal) v)),
            Map.entry("unitTm5", (r, v) -> r.setUnitTm5((BigDecimal) v)),
            Map.entry("unitTissueSubtotal", (r, v) -> r.setUnitTissueSubtotal((BigDecimal) v))
    );

    private final SteamEnergyRatioSteamUnitService service;

    public SteamEnergyRatioSteamUnitAdapter(SteamEnergyRatioSteamUnitService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "ratioUnit"; }

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
        SteamEnergyRatioSteamUnitSaveRequest request = new SteamEnergyRatioSteamUnitSaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyRatioSteamUnitSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
