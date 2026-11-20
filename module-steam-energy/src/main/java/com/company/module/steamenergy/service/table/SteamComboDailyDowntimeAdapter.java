package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboDailyDowntimeBulkRequest;
import com.company.module.steamenergy.dto.SteamComboDailyDowntimeResponse;
import com.company.module.steamenergy.dto.SteamComboDailyDowntimeSaveRequest;
import com.company.module.steamenergy.service.SteamComboDailyDowntimeService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 복합보일러 운휴·디스퍼져 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_combo_daily_downtime / alias: comboDowntime
 */
@Component
public class SteamComboDailyDowntimeAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("changeCombo", "changeJesco", "down1", "down2", "disperserSteam", "runtimeMin", "ventProduction", "ventInternal", "ventUsage");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("changeCombo", String.class),
            Map.entry("changeJesco", String.class),
            Map.entry("down1", BigDecimal.class),
            Map.entry("down2", BigDecimal.class),
            Map.entry("disperserSteam", BigDecimal.class),
            Map.entry("runtimeMin", BigDecimal.class),
            Map.entry("ventProduction", BigDecimal.class),
            Map.entry("ventInternal", BigDecimal.class),
            Map.entry("ventUsage", BigDecimal.class)
    );

    private static final Map<String, Function<SteamComboDailyDowntimeResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("changeCombo", r -> r.getChangeCombo()),
            Map.entry("changeJesco", r -> r.getChangeJesco()),
            Map.entry("down1", r -> r.getDown1()),
            Map.entry("down2", r -> r.getDown2()),
            Map.entry("disperserSteam", r -> r.getDisperserSteam()),
            Map.entry("runtimeMin", r -> r.getRuntimeMin()),
            Map.entry("ventProduction", r -> r.getVentProduction()),
            Map.entry("ventInternal", r -> r.getVentInternal()),
            Map.entry("ventUsage", r -> r.getVentUsage())
    );

    private static final Map<String, BiConsumer<SteamComboDailyDowntimeSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("changeCombo", (r, v) -> r.setChangeCombo((String) v)),
            Map.entry("changeJesco", (r, v) -> r.setChangeJesco((String) v)),
            Map.entry("down1", (r, v) -> r.setDown1((BigDecimal) v)),
            Map.entry("down2", (r, v) -> r.setDown2((BigDecimal) v)),
            Map.entry("disperserSteam", (r, v) -> r.setDisperserSteam((BigDecimal) v)),
            Map.entry("runtimeMin", (r, v) -> r.setRuntimeMin((BigDecimal) v)),
            Map.entry("ventProduction", (r, v) -> r.setVentProduction((BigDecimal) v)),
            Map.entry("ventInternal", (r, v) -> r.setVentInternal((BigDecimal) v)),
            Map.entry("ventUsage", (r, v) -> r.setVentUsage((BigDecimal) v))
    );

    private final SteamComboDailyDowntimeService service;

    public SteamComboDailyDowntimeAdapter(SteamComboDailyDowntimeService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "comboDowntime"; }

    @Override
    public String time() { return "day"; }

    @Override
    public List<String> keyFields() { return KEY_FIELDS; }

    @Override
    public List<String> valueFields() { return VALUE_FIELDS; }

    @Override
    public Class<?> typeOf(String field) { return TYPES.get(field); }

    @Override
    public List<Map<String, Object>> load(int year, Integer month) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SteamComboDailyDowntimeResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamComboDailyDowntimeBulkRequest request = new SteamComboDailyDowntimeBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamComboDailyDowntimeSaveRequest r = new SteamComboDailyDowntimeSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamComboDailyDowntimeSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
