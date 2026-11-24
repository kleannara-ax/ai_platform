package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceMonthlyResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceMonthlySaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceMonthlyService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SRF 인보이스 월별 운영내역 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_srf_invoice_monthly / alias: srfMonthly
 */
@Component
public class SteamSrfInvoiceMonthlyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("steamQty", "steamUnit", "powerQty", "powerUnit", "lngQty", "lngUnit", "processQty", "processUnit", "softQty", "softUnit", "softenQty", "softenUnit", "lifeQty", "lifeUnit", "etcSpecial", "etcBsc7", "etcSrfShare", "etcInspect", "etcOver7", "etcAirOver", "etcWaste");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("steamQty", BigDecimal.class),
            Map.entry("steamUnit", BigDecimal.class),
            Map.entry("powerQty", BigDecimal.class),
            Map.entry("powerUnit", BigDecimal.class),
            Map.entry("lngQty", BigDecimal.class),
            Map.entry("lngUnit", BigDecimal.class),
            Map.entry("processQty", BigDecimal.class),
            Map.entry("processUnit", BigDecimal.class),
            Map.entry("softQty", BigDecimal.class),
            Map.entry("softUnit", BigDecimal.class),
            Map.entry("softenQty", BigDecimal.class),
            Map.entry("softenUnit", BigDecimal.class),
            Map.entry("lifeQty", BigDecimal.class),
            Map.entry("lifeUnit", BigDecimal.class),
            Map.entry("etcSpecial", BigDecimal.class),
            Map.entry("etcBsc7", BigDecimal.class),
            Map.entry("etcSrfShare", BigDecimal.class),
            Map.entry("etcInspect", BigDecimal.class),
            Map.entry("etcOver7", BigDecimal.class),
            Map.entry("etcAirOver", BigDecimal.class),
            Map.entry("etcWaste", BigDecimal.class)
    );

    private static final Map<String, Function<SteamSrfInvoiceMonthlyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("steamQty", r -> r.getSteamQty()),
            Map.entry("steamUnit", r -> r.getSteamUnit()),
            Map.entry("powerQty", r -> r.getPowerQty()),
            Map.entry("powerUnit", r -> r.getPowerUnit()),
            Map.entry("lngQty", r -> r.getLngQty()),
            Map.entry("lngUnit", r -> r.getLngUnit()),
            Map.entry("processQty", r -> r.getProcessQty()),
            Map.entry("processUnit", r -> r.getProcessUnit()),
            Map.entry("softQty", r -> r.getSoftQty()),
            Map.entry("softUnit", r -> r.getSoftUnit()),
            Map.entry("softenQty", r -> r.getSoftenQty()),
            Map.entry("softenUnit", r -> r.getSoftenUnit()),
            Map.entry("lifeQty", r -> r.getLifeQty()),
            Map.entry("lifeUnit", r -> r.getLifeUnit()),
            Map.entry("etcSpecial", r -> r.getEtcSpecial()),
            Map.entry("etcBsc7", r -> r.getEtcBsc7()),
            Map.entry("etcSrfShare", r -> r.getEtcSrfShare()),
            Map.entry("etcInspect", r -> r.getEtcInspect()),
            Map.entry("etcOver7", r -> r.getEtcOver7()),
            Map.entry("etcAirOver", r -> r.getEtcAirOver()),
            Map.entry("etcWaste", r -> r.getEtcWaste())
    );

    private static final Map<String, BiConsumer<SteamSrfInvoiceMonthlySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("steamQty", (r, v) -> r.setSteamQty((BigDecimal) v)),
            Map.entry("steamUnit", (r, v) -> r.setSteamUnit((BigDecimal) v)),
            Map.entry("powerQty", (r, v) -> r.setPowerQty((BigDecimal) v)),
            Map.entry("powerUnit", (r, v) -> r.setPowerUnit((BigDecimal) v)),
            Map.entry("lngQty", (r, v) -> r.setLngQty((BigDecimal) v)),
            Map.entry("lngUnit", (r, v) -> r.setLngUnit((BigDecimal) v)),
            Map.entry("processQty", (r, v) -> r.setProcessQty((BigDecimal) v)),
            Map.entry("processUnit", (r, v) -> r.setProcessUnit((BigDecimal) v)),
            Map.entry("softQty", (r, v) -> r.setSoftQty((BigDecimal) v)),
            Map.entry("softUnit", (r, v) -> r.setSoftUnit((BigDecimal) v)),
            Map.entry("softenQty", (r, v) -> r.setSoftenQty((BigDecimal) v)),
            Map.entry("softenUnit", (r, v) -> r.setSoftenUnit((BigDecimal) v)),
            Map.entry("lifeQty", (r, v) -> r.setLifeQty((BigDecimal) v)),
            Map.entry("lifeUnit", (r, v) -> r.setLifeUnit((BigDecimal) v)),
            Map.entry("etcSpecial", (r, v) -> r.setEtcSpecial((BigDecimal) v)),
            Map.entry("etcBsc7", (r, v) -> r.setEtcBsc7((BigDecimal) v)),
            Map.entry("etcSrfShare", (r, v) -> r.setEtcSrfShare((BigDecimal) v)),
            Map.entry("etcInspect", (r, v) -> r.setEtcInspect((BigDecimal) v)),
            Map.entry("etcOver7", (r, v) -> r.setEtcOver7((BigDecimal) v)),
            Map.entry("etcAirOver", (r, v) -> r.setEtcAirOver((BigDecimal) v)),
            Map.entry("etcWaste", (r, v) -> r.setEtcWaste((BigDecimal) v))
    );

    private final SteamSrfInvoiceMonthlyService service;

    public SteamSrfInvoiceMonthlyAdapter(SteamSrfInvoiceMonthlyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "srfMonthly"; }

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
        for (SteamSrfInvoiceMonthlyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamSrfInvoiceMonthlyBulkRequest request = new SteamSrfInvoiceMonthlyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamSrfInvoiceMonthlySaveRequest r = new SteamSrfInvoiceMonthlySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamSrfInvoiceMonthlySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
