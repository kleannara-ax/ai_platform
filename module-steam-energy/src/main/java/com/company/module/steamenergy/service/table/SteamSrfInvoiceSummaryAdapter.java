package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceSummaryBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceSummaryResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceSummarySaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceSummaryService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * SRF 인보이스 Summary — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_srf_invoice_summary / alias: srfSummary
 */
@Component
public class SteamSrfInvoiceSummaryAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("bscRate", "bscFactor", "afbscrBsc", "bscSteamAmount", "ascRate", "ascFactor", "ascSteamAmount", "others0", "others1", "others2", "others3", "payableAdjust", "liquidatedDamage", "ascRefundAnnual", "utilitiesDeduction", "otherDeduction1", "otherDeduction2");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("bscRate", BigDecimal.class),
            Map.entry("bscFactor", BigDecimal.class),
            Map.entry("afbscrBsc", BigDecimal.class),
            Map.entry("bscSteamAmount", BigDecimal.class),
            Map.entry("ascRate", BigDecimal.class),
            Map.entry("ascFactor", BigDecimal.class),
            Map.entry("ascSteamAmount", BigDecimal.class),
            Map.entry("others0", BigDecimal.class),
            Map.entry("others1", BigDecimal.class),
            Map.entry("others2", BigDecimal.class),
            Map.entry("others3", BigDecimal.class),
            Map.entry("payableAdjust", BigDecimal.class),
            Map.entry("liquidatedDamage", BigDecimal.class),
            Map.entry("ascRefundAnnual", BigDecimal.class),
            Map.entry("utilitiesDeduction", BigDecimal.class),
            Map.entry("otherDeduction1", BigDecimal.class),
            Map.entry("otherDeduction2", BigDecimal.class)
    );

    private static final Map<String, Function<SteamSrfInvoiceSummaryResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("bscRate", r -> r.getBscRate()),
            Map.entry("bscFactor", r -> r.getBscFactor()),
            Map.entry("afbscrBsc", r -> r.getAfbscrBsc()),
            Map.entry("bscSteamAmount", r -> r.getBscSteamAmount()),
            Map.entry("ascRate", r -> r.getAscRate()),
            Map.entry("ascFactor", r -> r.getAscFactor()),
            Map.entry("ascSteamAmount", r -> r.getAscSteamAmount()),
            Map.entry("others0", r -> r.getOthers0()),
            Map.entry("others1", r -> r.getOthers1()),
            Map.entry("others2", r -> r.getOthers2()),
            Map.entry("others3", r -> r.getOthers3()),
            Map.entry("payableAdjust", r -> r.getPayableAdjust()),
            Map.entry("liquidatedDamage", r -> r.getLiquidatedDamage()),
            Map.entry("ascRefundAnnual", r -> r.getAscRefundAnnual()),
            Map.entry("utilitiesDeduction", r -> r.getUtilitiesDeduction()),
            Map.entry("otherDeduction1", r -> r.getOtherDeduction1()),
            Map.entry("otherDeduction2", r -> r.getOtherDeduction2())
    );

    private static final Map<String, BiConsumer<SteamSrfInvoiceSummarySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("bscRate", (r, v) -> r.setBscRate((BigDecimal) v)),
            Map.entry("bscFactor", (r, v) -> r.setBscFactor((BigDecimal) v)),
            Map.entry("afbscrBsc", (r, v) -> r.setAfbscrBsc((BigDecimal) v)),
            Map.entry("bscSteamAmount", (r, v) -> r.setBscSteamAmount((BigDecimal) v)),
            Map.entry("ascRate", (r, v) -> r.setAscRate((BigDecimal) v)),
            Map.entry("ascFactor", (r, v) -> r.setAscFactor((BigDecimal) v)),
            Map.entry("ascSteamAmount", (r, v) -> r.setAscSteamAmount((BigDecimal) v)),
            Map.entry("others0", (r, v) -> r.setOthers0((BigDecimal) v)),
            Map.entry("others1", (r, v) -> r.setOthers1((BigDecimal) v)),
            Map.entry("others2", (r, v) -> r.setOthers2((BigDecimal) v)),
            Map.entry("others3", (r, v) -> r.setOthers3((BigDecimal) v)),
            Map.entry("payableAdjust", (r, v) -> r.setPayableAdjust((BigDecimal) v)),
            Map.entry("liquidatedDamage", (r, v) -> r.setLiquidatedDamage((BigDecimal) v)),
            Map.entry("ascRefundAnnual", (r, v) -> r.setAscRefundAnnual((BigDecimal) v)),
            Map.entry("utilitiesDeduction", (r, v) -> r.setUtilitiesDeduction((BigDecimal) v)),
            Map.entry("otherDeduction1", (r, v) -> r.setOtherDeduction1((BigDecimal) v)),
            Map.entry("otherDeduction2", (r, v) -> r.setOtherDeduction2((BigDecimal) v))
    );

    private final SteamSrfInvoiceSummaryService service;

    public SteamSrfInvoiceSummaryAdapter(SteamSrfInvoiceSummaryService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "srfSummary"; }

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
        for (SteamSrfInvoiceSummaryResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamSrfInvoiceSummaryBulkRequest request = new SteamSrfInvoiceSummaryBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamSrfInvoiceSummarySaveRequest r = new SteamSrfInvoiceSummarySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamSrfInvoiceSummarySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
