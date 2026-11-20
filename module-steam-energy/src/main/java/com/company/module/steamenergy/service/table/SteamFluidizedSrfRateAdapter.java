package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedSrfRateBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedSrfRateResponse;
import com.company.module.steamenergy.dto.SteamFluidizedSrfRateSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedSrfRateService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 유동상 SRF 처리단가 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_srf_rate / alias: fluidSrfRate
 */
@Component
public class SteamFluidizedSrfRateAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("vendor01Rate", "vendor02Rate", "vendor03Rate", "vendor04Rate", "vendor05Rate", "vendor06Rate", "vendor07Rate", "vendor08Rate", "vendor09Rate", "vendor10Rate");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("vendor01Rate", BigDecimal.class),
            Map.entry("vendor02Rate", BigDecimal.class),
            Map.entry("vendor03Rate", BigDecimal.class),
            Map.entry("vendor04Rate", BigDecimal.class),
            Map.entry("vendor05Rate", BigDecimal.class),
            Map.entry("vendor06Rate", BigDecimal.class),
            Map.entry("vendor07Rate", BigDecimal.class),
            Map.entry("vendor08Rate", BigDecimal.class),
            Map.entry("vendor09Rate", BigDecimal.class),
            Map.entry("vendor10Rate", BigDecimal.class)
    );

    private static final Map<String, Function<SteamFluidizedSrfRateResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("vendor01Rate", r -> r.getVendor01Rate()),
            Map.entry("vendor02Rate", r -> r.getVendor02Rate()),
            Map.entry("vendor03Rate", r -> r.getVendor03Rate()),
            Map.entry("vendor04Rate", r -> r.getVendor04Rate()),
            Map.entry("vendor05Rate", r -> r.getVendor05Rate()),
            Map.entry("vendor06Rate", r -> r.getVendor06Rate()),
            Map.entry("vendor07Rate", r -> r.getVendor07Rate()),
            Map.entry("vendor08Rate", r -> r.getVendor08Rate()),
            Map.entry("vendor09Rate", r -> r.getVendor09Rate()),
            Map.entry("vendor10Rate", r -> r.getVendor10Rate())
    );

    private static final Map<String, BiConsumer<SteamFluidizedSrfRateSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("vendor01Rate", (r, v) -> r.setVendor01Rate((BigDecimal) v)),
            Map.entry("vendor02Rate", (r, v) -> r.setVendor02Rate((BigDecimal) v)),
            Map.entry("vendor03Rate", (r, v) -> r.setVendor03Rate((BigDecimal) v)),
            Map.entry("vendor04Rate", (r, v) -> r.setVendor04Rate((BigDecimal) v)),
            Map.entry("vendor05Rate", (r, v) -> r.setVendor05Rate((BigDecimal) v)),
            Map.entry("vendor06Rate", (r, v) -> r.setVendor06Rate((BigDecimal) v)),
            Map.entry("vendor07Rate", (r, v) -> r.setVendor07Rate((BigDecimal) v)),
            Map.entry("vendor08Rate", (r, v) -> r.setVendor08Rate((BigDecimal) v)),
            Map.entry("vendor09Rate", (r, v) -> r.setVendor09Rate((BigDecimal) v)),
            Map.entry("vendor10Rate", (r, v) -> r.setVendor10Rate((BigDecimal) v))
    );

    private final SteamFluidizedSrfRateService service;

    public SteamFluidizedSrfRateAdapter(SteamFluidizedSrfRateService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidSrfRate"; }

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
        for (SteamFluidizedSrfRateResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedSrfRateBulkRequest request = new SteamFluidizedSrfRateBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedSrfRateSaveRequest r = new SteamFluidizedSrfRateSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedSrfRateSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
