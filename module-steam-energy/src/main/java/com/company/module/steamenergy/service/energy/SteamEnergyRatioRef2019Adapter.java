package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyRatioRef2019Response;
import com.company.module.steamenergy.dto.SteamEnergyRatioRef2019SaveRequest;
import com.company.module.steamenergy.service.SteamEnergyRatioRef2019Service;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 연료비 배분비율(2019 참고) — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_ratio_ref2019 / alias: ratioRef2019
 */
@Component
public class SteamEnergyRatioRef2019Adapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyRatioRef2019Response, Object>> GETTERS = Map.ofEntries(
            Map.entry("t18Pm1", r -> r.getT18Pm1()),
            Map.entry("t18Pm2", r -> r.getT18Pm2()),
            Map.entry("t18Pm3", r -> r.getT18Pm3()),
            Map.entry("t18PaperSubtotal", r -> r.getT18PaperSubtotal()),
            Map.entry("t18Tm1", r -> r.getT18Tm1()),
            Map.entry("t18Tm3", r -> r.getT18Tm3()),
            Map.entry("t18Tm4", r -> r.getT18Tm4()),
            Map.entry("t18Tm5", r -> r.getT18Tm5()),
            Map.entry("t18TissueSubtotal", r -> r.getT18TissueSubtotal()),
            Map.entry("t18Total", r -> r.getT18Total()),
            Map.entry("t19Pm1", r -> r.getT19Pm1()),
            Map.entry("t19Pm2", r -> r.getT19Pm2()),
            Map.entry("t19Pm3", r -> r.getT19Pm3()),
            Map.entry("t19PaperSubtotal", r -> r.getT19PaperSubtotal()),
            Map.entry("t19Tm1", r -> r.getT19Tm1()),
            Map.entry("t19Tm3", r -> r.getT19Tm3()),
            Map.entry("t19Tm4", r -> r.getT19Tm4()),
            Map.entry("t19Tm5", r -> r.getT19Tm5()),
            Map.entry("t19TissueSubtotal", r -> r.getT19TissueSubtotal()),
            Map.entry("t19Total", r -> r.getT19Total()),
            Map.entry("t20Pm1", r -> r.getT20Pm1()),
            Map.entry("t20Pm2", r -> r.getT20Pm2()),
            Map.entry("t20Pm3", r -> r.getT20Pm3()),
            Map.entry("t20PaperSubtotal", r -> r.getT20PaperSubtotal()),
            Map.entry("t20Tm1", r -> r.getT20Tm1()),
            Map.entry("t20Tm3", r -> r.getT20Tm3()),
            Map.entry("t20Tm4", r -> r.getT20Tm4()),
            Map.entry("t20Tm5", r -> r.getT20Tm5()),
            Map.entry("t20TissueSubtotal", r -> r.getT20TissueSubtotal()),
            Map.entry("t20Total", r -> r.getT20Total()),
            Map.entry("t20TotalEnergyCost", r -> r.getT20TotalEnergyCost())
    );
    private static final Map<String, BiConsumer<SteamEnergyRatioRef2019SaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("t18Pm1", (r, v) -> r.setT18Pm1((BigDecimal) v)),
            Map.entry("t18Pm2", (r, v) -> r.setT18Pm2((BigDecimal) v)),
            Map.entry("t18Pm3", (r, v) -> r.setT18Pm3((BigDecimal) v)),
            Map.entry("t18PaperSubtotal", (r, v) -> r.setT18PaperSubtotal((BigDecimal) v)),
            Map.entry("t18Tm1", (r, v) -> r.setT18Tm1((BigDecimal) v)),
            Map.entry("t18Tm3", (r, v) -> r.setT18Tm3((BigDecimal) v)),
            Map.entry("t18Tm4", (r, v) -> r.setT18Tm4((BigDecimal) v)),
            Map.entry("t18Tm5", (r, v) -> r.setT18Tm5((BigDecimal) v)),
            Map.entry("t18TissueSubtotal", (r, v) -> r.setT18TissueSubtotal((BigDecimal) v)),
            Map.entry("t18Total", (r, v) -> r.setT18Total((BigDecimal) v)),
            Map.entry("t19Pm1", (r, v) -> r.setT19Pm1((BigDecimal) v)),
            Map.entry("t19Pm2", (r, v) -> r.setT19Pm2((BigDecimal) v)),
            Map.entry("t19Pm3", (r, v) -> r.setT19Pm3((BigDecimal) v)),
            Map.entry("t19PaperSubtotal", (r, v) -> r.setT19PaperSubtotal((BigDecimal) v)),
            Map.entry("t19Tm1", (r, v) -> r.setT19Tm1((BigDecimal) v)),
            Map.entry("t19Tm3", (r, v) -> r.setT19Tm3((BigDecimal) v)),
            Map.entry("t19Tm4", (r, v) -> r.setT19Tm4((BigDecimal) v)),
            Map.entry("t19Tm5", (r, v) -> r.setT19Tm5((BigDecimal) v)),
            Map.entry("t19TissueSubtotal", (r, v) -> r.setT19TissueSubtotal((BigDecimal) v)),
            Map.entry("t19Total", (r, v) -> r.setT19Total((BigDecimal) v)),
            Map.entry("t20Pm1", (r, v) -> r.setT20Pm1((BigDecimal) v)),
            Map.entry("t20Pm2", (r, v) -> r.setT20Pm2((BigDecimal) v)),
            Map.entry("t20Pm3", (r, v) -> r.setT20Pm3((BigDecimal) v)),
            Map.entry("t20PaperSubtotal", (r, v) -> r.setT20PaperSubtotal((BigDecimal) v)),
            Map.entry("t20Tm1", (r, v) -> r.setT20Tm1((BigDecimal) v)),
            Map.entry("t20Tm3", (r, v) -> r.setT20Tm3((BigDecimal) v)),
            Map.entry("t20Tm4", (r, v) -> r.setT20Tm4((BigDecimal) v)),
            Map.entry("t20Tm5", (r, v) -> r.setT20Tm5((BigDecimal) v)),
            Map.entry("t20TissueSubtotal", (r, v) -> r.setT20TissueSubtotal((BigDecimal) v)),
            Map.entry("t20Total", (r, v) -> r.setT20Total((BigDecimal) v)),
            Map.entry("t20TotalEnergyCost", (r, v) -> r.setT20TotalEnergyCost((BigDecimal) v))
    );

    private final SteamEnergyRatioRef2019Service service;

    public SteamEnergyRatioRef2019Adapter(SteamEnergyRatioRef2019Service service) {
        this.service = service;
    }

    @Override
    public String alias() { return "ratioRef2019"; }

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
        SteamEnergyRatioRef2019SaveRequest request = new SteamEnergyRatioRef2019SaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyRatioRef2019SaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
