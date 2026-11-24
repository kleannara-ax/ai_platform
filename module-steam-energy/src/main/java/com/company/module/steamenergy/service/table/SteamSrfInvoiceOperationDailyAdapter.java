package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceOperationDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceOperationDailyResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceOperationDailySaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceOperationDailyService;
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
 * SRF 인보이스 운영내역 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_srf_invoice_operation_daily / alias: srfOperation
 */
@Component
public class SteamSrfInvoiceOperationDailyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("powerKwh");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("powerKwh", BigDecimal.class)
    );

    private static final Map<String, Function<SteamSrfInvoiceOperationDailyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("powerKwh", r -> r.getPowerKwh())
    );

    private static final Map<String, BiConsumer<SteamSrfInvoiceOperationDailySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("powerKwh", (r, v) -> r.setPowerKwh((BigDecimal) v))
    );

    private final SteamSrfInvoiceOperationDailyService service;

    public SteamSrfInvoiceOperationDailyAdapter(SteamSrfInvoiceOperationDailyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "srfOperation"; }

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
        for (SteamSrfInvoiceOperationDailyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamSrfInvoiceOperationDailyBulkRequest request = new SteamSrfInvoiceOperationDailyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamSrfInvoiceOperationDailySaveRequest r = new SteamSrfInvoiceOperationDailySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamSrfInvoiceOperationDailySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
