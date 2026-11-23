package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorOperationMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorOperationMonthlyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorOperationMonthlySaveRequest;
import com.company.module.steamenergy.service.SteamIncineratorOperationMonthlyService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 소각로 계획대비 가동실적 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_incinerator_operation_monthly / alias: incineratorOperation
 */
@Component
public class SteamIncineratorOperationMonthlyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("u1Plan", "u1Actual", "u2Plan", "u2Actual", "steamPlan", "steamActual", "ratePlan", "rateActual", "costPlan", "costActual");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("u1Plan", BigDecimal.class),
            Map.entry("u1Actual", BigDecimal.class),
            Map.entry("u2Plan", BigDecimal.class),
            Map.entry("u2Actual", BigDecimal.class),
            Map.entry("steamPlan", BigDecimal.class),
            Map.entry("steamActual", BigDecimal.class),
            Map.entry("ratePlan", BigDecimal.class),
            Map.entry("rateActual", BigDecimal.class),
            Map.entry("costPlan", BigDecimal.class),
            Map.entry("costActual", BigDecimal.class)
    );

    private static final Map<String, Function<SteamIncineratorOperationMonthlyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("u1Plan", r -> r.getU1Plan()),
            Map.entry("u1Actual", r -> r.getU1Actual()),
            Map.entry("u2Plan", r -> r.getU2Plan()),
            Map.entry("u2Actual", r -> r.getU2Actual()),
            Map.entry("steamPlan", r -> r.getSteamPlan()),
            Map.entry("steamActual", r -> r.getSteamActual()),
            Map.entry("ratePlan", r -> r.getRatePlan()),
            Map.entry("rateActual", r -> r.getRateActual()),
            Map.entry("costPlan", r -> r.getCostPlan()),
            Map.entry("costActual", r -> r.getCostActual())
    );

    private static final Map<String, BiConsumer<SteamIncineratorOperationMonthlySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("u1Plan", (r, v) -> r.setU1Plan((BigDecimal) v)),
            Map.entry("u1Actual", (r, v) -> r.setU1Actual((BigDecimal) v)),
            Map.entry("u2Plan", (r, v) -> r.setU2Plan((BigDecimal) v)),
            Map.entry("u2Actual", (r, v) -> r.setU2Actual((BigDecimal) v)),
            Map.entry("steamPlan", (r, v) -> r.setSteamPlan((BigDecimal) v)),
            Map.entry("steamActual", (r, v) -> r.setSteamActual((BigDecimal) v)),
            Map.entry("ratePlan", (r, v) -> r.setRatePlan((BigDecimal) v)),
            Map.entry("rateActual", (r, v) -> r.setRateActual((BigDecimal) v)),
            Map.entry("costPlan", (r, v) -> r.setCostPlan((BigDecimal) v)),
            Map.entry("costActual", (r, v) -> r.setCostActual((BigDecimal) v))
    );

    private final SteamIncineratorOperationMonthlyService service;

    public SteamIncineratorOperationMonthlyAdapter(SteamIncineratorOperationMonthlyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "incineratorOperation"; }

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
        for (SteamIncineratorOperationMonthlyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamIncineratorOperationMonthlyBulkRequest request = new SteamIncineratorOperationMonthlyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamIncineratorOperationMonthlySaveRequest r = new SteamIncineratorOperationMonthlySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamIncineratorOperationMonthlySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
