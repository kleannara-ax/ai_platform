package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossBulkRequest;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossResponse;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossSaveRequest;
import com.company.module.steamenergy.service.SteamComboRecoveryLossService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 복합보일러 스팀 회수 감소 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_combo_recovery_loss / alias: comboRecovery
 */
@Component
public class SteamComboRecoveryLossAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo", "lineNo");
    private static final List<String> VALUE_FIELDS = List.of("lossDate", "lossTime", "lossNote", "lossSteamRate", "lossBaseRate", "lossMinutes");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("lineNo", Integer.class),
            Map.entry("lossDate", String.class),
            Map.entry("lossTime", String.class),
            Map.entry("lossNote", String.class),
            Map.entry("lossSteamRate", BigDecimal.class),
            Map.entry("lossBaseRate", BigDecimal.class),
            Map.entry("lossMinutes", BigDecimal.class)
    );

    private static final Map<String, Function<SteamComboRecoveryLossResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("lineNo", r -> r.getLineNo()),
            Map.entry("lossDate", r -> r.getLossDate()),
            Map.entry("lossTime", r -> r.getLossTime()),
            Map.entry("lossNote", r -> r.getLossNote()),
            Map.entry("lossSteamRate", r -> r.getLossSteamRate()),
            Map.entry("lossBaseRate", r -> r.getLossBaseRate()),
            Map.entry("lossMinutes", r -> r.getLossMinutes())
    );

    private static final Map<String, BiConsumer<SteamComboRecoveryLossSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("lineNo", (r, v) -> r.setLineNo((Integer) v)),
            Map.entry("lossDate", (r, v) -> r.setLossDate((String) v)),
            Map.entry("lossTime", (r, v) -> r.setLossTime((String) v)),
            Map.entry("lossNote", (r, v) -> r.setLossNote((String) v)),
            Map.entry("lossSteamRate", (r, v) -> r.setLossSteamRate((BigDecimal) v)),
            Map.entry("lossBaseRate", (r, v) -> r.setLossBaseRate((BigDecimal) v)),
            Map.entry("lossMinutes", (r, v) -> r.setLossMinutes((BigDecimal) v))
    );

    private final SteamComboRecoveryLossService service;

    public SteamComboRecoveryLossAdapter(SteamComboRecoveryLossService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "comboRecovery"; }

    @Override
    public String time() { return "month"; }

    @Override
    public List<String> keyFields() { return KEY_FIELDS; }

    @Override
    public List<String> valueFields() { return VALUE_FIELDS; }

    @Override
    public Class<?> typeOf(String field) { return TYPES.get(field); }

    @Override
    public List<Map<String, Object>> load(int year, Integer month) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SteamComboRecoveryLossResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamComboRecoveryLossBulkRequest request = new SteamComboRecoveryLossBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamComboRecoveryLossSaveRequest r = new SteamComboRecoveryLossSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamComboRecoveryLossSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
