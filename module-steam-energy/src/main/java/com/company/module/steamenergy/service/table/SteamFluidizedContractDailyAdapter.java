package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedContractDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedContractDailyResponse;
import com.company.module.steamenergy.dto.SteamFluidizedContractDailySaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedContractDailyService;
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
 * 유동상 소각로 도급비용 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_contract_daily / alias: fluidContract
 */
@Component
public class SteamFluidizedContractDailyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("steamTon", "steamUnitPrice", "stopHours", "fixedCostPerHour");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("steamTon", BigDecimal.class),
            Map.entry("steamUnitPrice", BigDecimal.class),
            Map.entry("stopHours", BigDecimal.class),
            Map.entry("fixedCostPerHour", BigDecimal.class)
    );

    private static final Map<String, Function<SteamFluidizedContractDailyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("steamTon", r -> r.getSteamTon()),
            Map.entry("steamUnitPrice", r -> r.getSteamUnitPrice()),
            Map.entry("stopHours", r -> r.getStopHours()),
            Map.entry("fixedCostPerHour", r -> r.getFixedCostPerHour())
    );

    private static final Map<String, BiConsumer<SteamFluidizedContractDailySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("steamTon", (r, v) -> r.setSteamTon((BigDecimal) v)),
            Map.entry("steamUnitPrice", (r, v) -> r.setSteamUnitPrice((BigDecimal) v)),
            Map.entry("stopHours", (r, v) -> r.setStopHours((BigDecimal) v)),
            Map.entry("fixedCostPerHour", (r, v) -> r.setFixedCostPerHour((BigDecimal) v))
    );

    private final SteamFluidizedContractDailyService service;

    public SteamFluidizedContractDailyAdapter(SteamFluidizedContractDailyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidContract"; }

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
        for (SteamFluidizedContractDailyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedContractDailyBulkRequest request = new SteamFluidizedContractDailyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedContractDailySaveRequest r = new SteamFluidizedContractDailySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedContractDailySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
