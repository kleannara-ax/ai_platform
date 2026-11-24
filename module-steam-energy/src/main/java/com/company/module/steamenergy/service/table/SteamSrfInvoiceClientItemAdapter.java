package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceClientItemBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceClientItemResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceClientItemSaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceClientItemService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SRF 인보이스 Client Invoice — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_srf_invoice_client_item / alias: srfClient
 */
@Component
public class SteamSrfInvoiceClientItemAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo", "section", "lineNo");
    private static final List<String> VALUE_FIELDS = List.of("itemLabel", "unitLabel", "qty", "unitPrice", "note");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("section", String.class),
            Map.entry("lineNo", Integer.class),
            Map.entry("itemLabel", String.class),
            Map.entry("unitLabel", String.class),
            Map.entry("qty", BigDecimal.class),
            Map.entry("unitPrice", BigDecimal.class),
            Map.entry("note", String.class)
    );

    private static final Map<String, Function<SteamSrfInvoiceClientItemResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("section", r -> r.getSection()),
            Map.entry("lineNo", r -> r.getLineNo()),
            Map.entry("itemLabel", r -> r.getItemLabel()),
            Map.entry("unitLabel", r -> r.getUnitLabel()),
            Map.entry("qty", r -> r.getQty()),
            Map.entry("unitPrice", r -> r.getUnitPrice()),
            Map.entry("note", r -> r.getNote())
    );

    private static final Map<String, BiConsumer<SteamSrfInvoiceClientItemSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("section", (r, v) -> r.setSection((String) v)),
            Map.entry("lineNo", (r, v) -> r.setLineNo((Integer) v)),
            Map.entry("itemLabel", (r, v) -> r.setItemLabel((String) v)),
            Map.entry("unitLabel", (r, v) -> r.setUnitLabel((String) v)),
            Map.entry("qty", (r, v) -> r.setQty((BigDecimal) v)),
            Map.entry("unitPrice", (r, v) -> r.setUnitPrice((BigDecimal) v)),
            Map.entry("note", (r, v) -> r.setNote((String) v))
    );

    private final SteamSrfInvoiceClientItemService service;

    public SteamSrfInvoiceClientItemAdapter(SteamSrfInvoiceClientItemService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "srfClient"; }

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
        for (SteamSrfInvoiceClientItemResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamSrfInvoiceClientItemBulkRequest request = new SteamSrfInvoiceClientItemBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamSrfInvoiceClientItemSaveRequest r = new SteamSrfInvoiceClientItemSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamSrfInvoiceClientItemSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
