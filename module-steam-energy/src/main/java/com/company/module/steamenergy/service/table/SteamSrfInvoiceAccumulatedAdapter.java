package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAccumulatedBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAccumulatedResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAccumulatedSaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceAccumulatedService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SRF 인보이스 누계 스팀량 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_srf_invoice_accumulated / alias: srfAccumulated
 */
@Component
public class SteamSrfInvoiceAccumulatedAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("contractVolume", "bscVolume", "ascVolume");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("contractVolume", BigDecimal.class),
            Map.entry("bscVolume", BigDecimal.class),
            Map.entry("ascVolume", BigDecimal.class)
    );

    private static final Map<String, Function<SteamSrfInvoiceAccumulatedResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("contractVolume", r -> r.getContractVolume()),
            Map.entry("bscVolume", r -> r.getBscVolume()),
            Map.entry("ascVolume", r -> r.getAscVolume())
    );

    private static final Map<String, BiConsumer<SteamSrfInvoiceAccumulatedSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("contractVolume", (r, v) -> r.setContractVolume((BigDecimal) v)),
            Map.entry("bscVolume", (r, v) -> r.setBscVolume((BigDecimal) v)),
            Map.entry("ascVolume", (r, v) -> r.setAscVolume((BigDecimal) v))
    );

    private final SteamSrfInvoiceAccumulatedService service;

    public SteamSrfInvoiceAccumulatedAdapter(SteamSrfInvoiceAccumulatedService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "srfAccumulated"; }

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
        for (SteamSrfInvoiceAccumulatedResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamSrfInvoiceAccumulatedBulkRequest request = new SteamSrfInvoiceAccumulatedBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamSrfInvoiceAccumulatedSaveRequest r = new SteamSrfInvoiceAccumulatedSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamSrfInvoiceAccumulatedSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
