package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAdjustmentBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAdjustmentResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAdjustmentSaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceAdjustmentService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SRF 인보이스 Adjustment Factor — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_srf_invoice_adjustment / alias: srfAdjustment
 */
@Component
public class SteamSrfInvoiceAdjustmentAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "section", "itemCode");
    private static final List<String> VALUE_FIELDS = List.of("coefficient", "baseValue", "presentValue");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("section", String.class),
            Map.entry("itemCode", String.class),
            Map.entry("coefficient", BigDecimal.class),
            Map.entry("baseValue", BigDecimal.class),
            Map.entry("presentValue", BigDecimal.class)
    );

    private static final Map<String, Function<SteamSrfInvoiceAdjustmentResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("section", r -> r.getSection()),
            Map.entry("itemCode", r -> r.getItemCode()),
            Map.entry("coefficient", r -> r.getCoefficient()),
            Map.entry("baseValue", r -> r.getBaseValue()),
            Map.entry("presentValue", r -> r.getPresentValue())
    );

    private static final Map<String, BiConsumer<SteamSrfInvoiceAdjustmentSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("section", (r, v) -> r.setSection((String) v)),
            Map.entry("itemCode", (r, v) -> r.setItemCode((String) v)),
            Map.entry("coefficient", (r, v) -> r.setCoefficient((BigDecimal) v)),
            Map.entry("baseValue", (r, v) -> r.setBaseValue((BigDecimal) v)),
            Map.entry("presentValue", (r, v) -> r.setPresentValue((BigDecimal) v))
    );

    private final SteamSrfInvoiceAdjustmentService service;

    public SteamSrfInvoiceAdjustmentAdapter(SteamSrfInvoiceAdjustmentService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "srfAdjustment"; }

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
        for (SteamSrfInvoiceAdjustmentResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamSrfInvoiceAdjustmentBulkRequest request = new SteamSrfInvoiceAdjustmentBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamSrfInvoiceAdjustmentSaveRequest r = new SteamSrfInvoiceAdjustmentSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamSrfInvoiceAdjustmentSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
