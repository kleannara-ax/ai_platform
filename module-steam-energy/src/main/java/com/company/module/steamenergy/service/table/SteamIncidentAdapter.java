package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncidentBulkRequest;
import com.company.module.steamenergy.dto.SteamIncidentResponse;
import com.company.module.steamenergy.dto.SteamIncidentSaveRequest;
import com.company.module.steamenergy.service.SteamIncidentService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 사고·정비 이력 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_incident / alias: incident
 */
@Component
public class SteamIncidentAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "recordKey");
    private static final List<String> VALUE_FIELDS = List.of("facility", "kind", "occurDate", "reason", "actionTaken", "downtimeHours", "lossAmount", "note", "sortOrder");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("recordKey", String.class),
            Map.entry("facility", String.class),
            Map.entry("kind", String.class),
            Map.entry("occurDate", String.class),
            Map.entry("reason", String.class),
            Map.entry("actionTaken", String.class),
            Map.entry("downtimeHours", String.class),
            Map.entry("lossAmount", String.class),
            Map.entry("note", String.class),
            Map.entry("sortOrder", Integer.class)
    );

    private static final Map<String, Function<SteamIncidentResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("recordKey", r -> r.getRecordKey()),
            Map.entry("facility", r -> r.getFacility()),
            Map.entry("kind", r -> r.getKind()),
            Map.entry("occurDate", r -> r.getOccurDate()),
            Map.entry("reason", r -> r.getReason()),
            Map.entry("actionTaken", r -> r.getActionTaken()),
            Map.entry("downtimeHours", r -> r.getDowntimeHours()),
            Map.entry("lossAmount", r -> r.getLossAmount()),
            Map.entry("note", r -> r.getNote()),
            Map.entry("sortOrder", r -> r.getSortOrder())
    );

    private static final Map<String, BiConsumer<SteamIncidentSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("recordKey", (r, v) -> r.setRecordKey((String) v)),
            Map.entry("facility", (r, v) -> r.setFacility((String) v)),
            Map.entry("kind", (r, v) -> r.setKind((String) v)),
            Map.entry("occurDate", (r, v) -> r.setOccurDate((String) v)),
            Map.entry("reason", (r, v) -> r.setReason((String) v)),
            Map.entry("actionTaken", (r, v) -> r.setActionTaken((String) v)),
            Map.entry("downtimeHours", (r, v) -> r.setDowntimeHours((String) v)),
            Map.entry("lossAmount", (r, v) -> r.setLossAmount((String) v)),
            Map.entry("note", (r, v) -> r.setNote((String) v)),
            Map.entry("sortOrder", (r, v) -> r.setSortOrder((Integer) v))
    );

    private final SteamIncidentService service;

    public SteamIncidentAdapter(SteamIncidentService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "incident"; }

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
        for (SteamIncidentResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamIncidentBulkRequest request = new SteamIncidentBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamIncidentSaveRequest r = new SteamIncidentSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamIncidentSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
