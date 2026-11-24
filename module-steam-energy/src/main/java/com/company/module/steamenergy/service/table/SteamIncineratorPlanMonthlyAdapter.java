package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorPlanMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorPlanMonthlyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorPlanMonthlySaveRequest;
import com.company.module.steamenergy.service.SteamIncineratorPlanMonthlyService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 소각로 사업계획대비 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_incinerator_plan_monthly / alias: incineratorPlan
 */
@Component
public class SteamIncineratorPlanMonthlyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("contractLabor", "laborPlan", "laborActual", "operationPlan", "operationActual", "totalPlan", "noteU1", "noteU2", "brkMonthPlan", "brkMonthActual", "brkYtdPlan", "brkYtdActual", "brkPrevActual", "brkNote");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("contractLabor", BigDecimal.class),
            Map.entry("laborPlan", BigDecimal.class),
            Map.entry("laborActual", BigDecimal.class),
            Map.entry("operationPlan", BigDecimal.class),
            Map.entry("operationActual", BigDecimal.class),
            Map.entry("totalPlan", BigDecimal.class),
            Map.entry("noteU1", String.class),
            Map.entry("noteU2", String.class),
            Map.entry("brkMonthPlan", BigDecimal.class),
            Map.entry("brkMonthActual", BigDecimal.class),
            Map.entry("brkYtdPlan", BigDecimal.class),
            Map.entry("brkYtdActual", BigDecimal.class),
            Map.entry("brkPrevActual", BigDecimal.class),
            Map.entry("brkNote", String.class)
    );

    private static final Map<String, Function<SteamIncineratorPlanMonthlyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("contractLabor", r -> r.getContractLabor()),
            Map.entry("laborPlan", r -> r.getLaborPlan()),
            Map.entry("laborActual", r -> r.getLaborActual()),
            Map.entry("operationPlan", r -> r.getOperationPlan()),
            Map.entry("operationActual", r -> r.getOperationActual()),
            Map.entry("totalPlan", r -> r.getTotalPlan()),
            Map.entry("noteU1", r -> r.getNoteU1()),
            Map.entry("noteU2", r -> r.getNoteU2()),
            Map.entry("brkMonthPlan", r -> r.getBrkMonthPlan()),
            Map.entry("brkMonthActual", r -> r.getBrkMonthActual()),
            Map.entry("brkYtdPlan", r -> r.getBrkYtdPlan()),
            Map.entry("brkYtdActual", r -> r.getBrkYtdActual()),
            Map.entry("brkPrevActual", r -> r.getBrkPrevActual()),
            Map.entry("brkNote", r -> r.getBrkNote())
    );

    private static final Map<String, BiConsumer<SteamIncineratorPlanMonthlySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("contractLabor", (r, v) -> r.setContractLabor((BigDecimal) v)),
            Map.entry("laborPlan", (r, v) -> r.setLaborPlan((BigDecimal) v)),
            Map.entry("laborActual", (r, v) -> r.setLaborActual((BigDecimal) v)),
            Map.entry("operationPlan", (r, v) -> r.setOperationPlan((BigDecimal) v)),
            Map.entry("operationActual", (r, v) -> r.setOperationActual((BigDecimal) v)),
            Map.entry("totalPlan", (r, v) -> r.setTotalPlan((BigDecimal) v)),
            Map.entry("noteU1", (r, v) -> r.setNoteU1((String) v)),
            Map.entry("noteU2", (r, v) -> r.setNoteU2((String) v)),
            Map.entry("brkMonthPlan", (r, v) -> r.setBrkMonthPlan((BigDecimal) v)),
            Map.entry("brkMonthActual", (r, v) -> r.setBrkMonthActual((BigDecimal) v)),
            Map.entry("brkYtdPlan", (r, v) -> r.setBrkYtdPlan((BigDecimal) v)),
            Map.entry("brkYtdActual", (r, v) -> r.setBrkYtdActual((BigDecimal) v)),
            Map.entry("brkPrevActual", (r, v) -> r.setBrkPrevActual((BigDecimal) v)),
            Map.entry("brkNote", (r, v) -> r.setBrkNote((String) v))
    );

    private final SteamIncineratorPlanMonthlyService service;

    public SteamIncineratorPlanMonthlyAdapter(SteamIncineratorPlanMonthlyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "incineratorPlan"; }

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
        for (SteamIncineratorPlanMonthlyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamIncineratorPlanMonthlyBulkRequest request = new SteamIncineratorPlanMonthlyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamIncineratorPlanMonthlySaveRequest r = new SteamIncineratorPlanMonthlySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamIncineratorPlanMonthlySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
