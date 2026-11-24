package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceBscBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceBscResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceBscSaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceBscService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SRF 인보이스 BSC 단가 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_srf_invoice_bsc / alias: srfBsc
 */
@Component
public class SteamSrfInvoiceBscAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo");
    private static final List<String> VALUE_FIELDS = List.of("bscRate");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("bscRate", BigDecimal.class)
    );

    private static final Map<String, Function<SteamSrfInvoiceBscResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("bscRate", r -> r.getBscRate())
    );

    private static final Map<String, BiConsumer<SteamSrfInvoiceBscSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("bscRate", (r, v) -> r.setBscRate((BigDecimal) v))
    );

    private final SteamSrfInvoiceBscService service;

    public SteamSrfInvoiceBscAdapter(SteamSrfInvoiceBscService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "srfBsc"; }

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
        for (SteamSrfInvoiceBscResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamSrfInvoiceBscBulkRequest request = new SteamSrfInvoiceBscBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamSrfInvoiceBscSaveRequest r = new SteamSrfInvoiceBscSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamSrfInvoiceBscSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
