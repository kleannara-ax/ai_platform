package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboPlanBulkRequest;
import com.company.module.steamenergy.dto.SteamComboPlanResponse;
import com.company.module.steamenergy.dto.SteamComboPlanSaveRequest;
import com.company.module.steamenergy.service.SteamComboPlanService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 복합보일러 연간 계획 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_combo_plan / alias: comboPlan
 */
@Component
public class SteamComboPlanAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "targetYm");
    private static final List<String> VALUE_FIELDS = List.of("comboBasic", "comboSpecial", "comboActual", "jescoPlan", "jescoActual", "lngBoiler", "lngCombo");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("targetYm", String.class),
            Map.entry("comboBasic", BigDecimal.class),
            Map.entry("comboSpecial", BigDecimal.class),
            Map.entry("comboActual", BigDecimal.class),
            Map.entry("jescoPlan", BigDecimal.class),
            Map.entry("jescoActual", BigDecimal.class),
            Map.entry("lngBoiler", BigDecimal.class),
            Map.entry("lngCombo", BigDecimal.class)
    );

    private static final Map<String, Function<SteamComboPlanResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("targetYm", r -> r.getTargetYm()),
            Map.entry("comboBasic", r -> r.getComboBasic()),
            Map.entry("comboSpecial", r -> r.getComboSpecial()),
            Map.entry("comboActual", r -> r.getComboActual()),
            Map.entry("jescoPlan", r -> r.getJescoPlan()),
            Map.entry("jescoActual", r -> r.getJescoActual()),
            Map.entry("lngBoiler", r -> r.getLngBoiler()),
            Map.entry("lngCombo", r -> r.getLngCombo())
    );

    private static final Map<String, BiConsumer<SteamComboPlanSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("targetYm", (r, v) -> r.setTargetYm((String) v)),
            Map.entry("comboBasic", (r, v) -> r.setComboBasic((BigDecimal) v)),
            Map.entry("comboSpecial", (r, v) -> r.setComboSpecial((BigDecimal) v)),
            Map.entry("comboActual", (r, v) -> r.setComboActual((BigDecimal) v)),
            Map.entry("jescoPlan", (r, v) -> r.setJescoPlan((BigDecimal) v)),
            Map.entry("jescoActual", (r, v) -> r.setJescoActual((BigDecimal) v)),
            Map.entry("lngBoiler", (r, v) -> r.setLngBoiler((BigDecimal) v)),
            Map.entry("lngCombo", (r, v) -> r.setLngCombo((BigDecimal) v))
    );

    private final SteamComboPlanService service;

    public SteamComboPlanAdapter(SteamComboPlanService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "comboPlan"; }

    @Override
    public String time() { return "year"; }

    @Override
    public List<String> keyFields() { return KEY_FIELDS; }

    @Override
    public List<String> valueFields() { return VALUE_FIELDS; }

    @Override
    public Class<?> typeOf(String field) { return TYPES.get(field); }

    @Override
    public List<Map<String, Object>> load(int year, Integer month) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SteamComboPlanResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamComboPlanBulkRequest request = new SteamComboPlanBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamComboPlanSaveRequest r = new SteamComboPlanSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamComboPlanSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
