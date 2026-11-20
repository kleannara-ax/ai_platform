package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedSrfInboundDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedSrfInboundDailyResponse;
import com.company.module.steamenergy.dto.SteamFluidizedSrfInboundDailySaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedSrfInboundDailyService;
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
 * 유동상 SRF 입고내역 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_srf_inbound_daily / alias: fluidSrfInbound
 */
@Component
public class SteamFluidizedSrfInboundDailyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("vendor01Kg", "vendor02Kg", "vendor03Kg", "vendor04Kg", "vendor05Kg", "vendor06Kg", "vendor07Kg", "vendor08Kg", "vendor09Kg", "vendor10Kg");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("vendor01Kg", BigDecimal.class),
            Map.entry("vendor02Kg", BigDecimal.class),
            Map.entry("vendor03Kg", BigDecimal.class),
            Map.entry("vendor04Kg", BigDecimal.class),
            Map.entry("vendor05Kg", BigDecimal.class),
            Map.entry("vendor06Kg", BigDecimal.class),
            Map.entry("vendor07Kg", BigDecimal.class),
            Map.entry("vendor08Kg", BigDecimal.class),
            Map.entry("vendor09Kg", BigDecimal.class),
            Map.entry("vendor10Kg", BigDecimal.class)
    );

    private static final Map<String, Function<SteamFluidizedSrfInboundDailyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("vendor01Kg", r -> r.getVendor01Kg()),
            Map.entry("vendor02Kg", r -> r.getVendor02Kg()),
            Map.entry("vendor03Kg", r -> r.getVendor03Kg()),
            Map.entry("vendor04Kg", r -> r.getVendor04Kg()),
            Map.entry("vendor05Kg", r -> r.getVendor05Kg()),
            Map.entry("vendor06Kg", r -> r.getVendor06Kg()),
            Map.entry("vendor07Kg", r -> r.getVendor07Kg()),
            Map.entry("vendor08Kg", r -> r.getVendor08Kg()),
            Map.entry("vendor09Kg", r -> r.getVendor09Kg()),
            Map.entry("vendor10Kg", r -> r.getVendor10Kg())
    );

    private static final Map<String, BiConsumer<SteamFluidizedSrfInboundDailySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("vendor01Kg", (r, v) -> r.setVendor01Kg((BigDecimal) v)),
            Map.entry("vendor02Kg", (r, v) -> r.setVendor02Kg((BigDecimal) v)),
            Map.entry("vendor03Kg", (r, v) -> r.setVendor03Kg((BigDecimal) v)),
            Map.entry("vendor04Kg", (r, v) -> r.setVendor04Kg((BigDecimal) v)),
            Map.entry("vendor05Kg", (r, v) -> r.setVendor05Kg((BigDecimal) v)),
            Map.entry("vendor06Kg", (r, v) -> r.setVendor06Kg((BigDecimal) v)),
            Map.entry("vendor07Kg", (r, v) -> r.setVendor07Kg((BigDecimal) v)),
            Map.entry("vendor08Kg", (r, v) -> r.setVendor08Kg((BigDecimal) v)),
            Map.entry("vendor09Kg", (r, v) -> r.setVendor09Kg((BigDecimal) v)),
            Map.entry("vendor10Kg", (r, v) -> r.setVendor10Kg((BigDecimal) v))
    );

    private final SteamFluidizedSrfInboundDailyService service;

    public SteamFluidizedSrfInboundDailyAdapter(SteamFluidizedSrfInboundDailyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidSrfInbound"; }

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
        for (SteamFluidizedSrfInboundDailyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedSrfInboundDailyBulkRequest request = new SteamFluidizedSrfInboundDailyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedSrfInboundDailySaveRequest r = new SteamFluidizedSrfInboundDailySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedSrfInboundDailySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
