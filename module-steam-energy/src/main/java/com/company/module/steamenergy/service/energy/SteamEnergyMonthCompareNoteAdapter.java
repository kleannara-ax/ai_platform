package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyMonthCompareNoteResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareNoteSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyMonthCompareNoteService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 회계비용 마감비교 비고 — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_month_compare_note / alias: monthCompareNote
 */
@Component
public class SteamEnergyMonthCompareNoteAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyMonthCompareNoteResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("noteFuel", r -> r.getNoteFuel()),
            Map.entry("noteDeduct", r -> r.getNoteDeduct()),
            Map.entry("noteActual", r -> r.getNoteActual()),
            Map.entry("noteSteamRatio", r -> r.getNoteSteamRatio()),
            Map.entry("notePlan", r -> r.getNotePlan()),
            Map.entry("noteProd", r -> r.getNoteProd()),
            Map.entry("noteProduct", r -> r.getNoteProduct()),
            Map.entry("noteUnit", r -> r.getNoteUnit()),
            Map.entry("noteProfit", r -> r.getNoteProfit()),
            Map.entry("noteAux", r -> r.getNoteAux())
    );
    private static final Map<String, BiConsumer<SteamEnergyMonthCompareNoteSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("noteFuel", (r, v) -> r.setNoteFuel((String) v)),
            Map.entry("noteDeduct", (r, v) -> r.setNoteDeduct((String) v)),
            Map.entry("noteActual", (r, v) -> r.setNoteActual((String) v)),
            Map.entry("noteSteamRatio", (r, v) -> r.setNoteSteamRatio((String) v)),
            Map.entry("notePlan", (r, v) -> r.setNotePlan((String) v)),
            Map.entry("noteProd", (r, v) -> r.setNoteProd((String) v)),
            Map.entry("noteProduct", (r, v) -> r.setNoteProduct((String) v)),
            Map.entry("noteUnit", (r, v) -> r.setNoteUnit((String) v)),
            Map.entry("noteProfit", (r, v) -> r.setNoteProfit((String) v)),
            Map.entry("noteAux", (r, v) -> r.setNoteAux((String) v))
    );

    private final SteamEnergyMonthCompareNoteService service;

    public SteamEnergyMonthCompareNoteAdapter(SteamEnergyMonthCompareNoteService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "monthCompareNote"; }

    @Override
    public boolean monthly() { return false; }

    @Override
    public java.util.Set<String> fields() { return SETTERS.keySet(); }

    @Override
    public List<EnergyRow> load(int year) {
        return service.getList(year).stream().map(r -> {
            Map<String, Object> values = new LinkedHashMap<>();
            GETTERS.forEach((field, getter) -> values.put(field, getter.apply(r)));
            return new EnergyRow(r.getYearNo(), null, values);
        }).toList();
    }

    @Override
    public void save(int yearNo, Integer monthNo, Map<String, Object> values) {
        SteamEnergyMonthCompareNoteSaveRequest request = new SteamEnergyMonthCompareNoteSaveRequest();
        request.setYearNo(yearNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyMonthCompareNoteSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
