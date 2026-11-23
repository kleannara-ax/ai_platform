package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementPaymentBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementPaymentResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementPaymentSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedImprovementPaymentService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 유동상 설비개선 월별 비용 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_improvement_payment / alias: improvementPayment
 */
@Component
public class SteamFluidizedImprovementPaymentAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "recordKey");
    private static final List<String> VALUE_FIELDS = List.of("payMonth", "paymentAmount", "spentAmount", "vendorName", "usageDesc", "sortOrder");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("recordKey", String.class),
            Map.entry("payMonth", String.class),
            Map.entry("paymentAmount", BigDecimal.class),
            Map.entry("spentAmount", BigDecimal.class),
            Map.entry("vendorName", String.class),
            Map.entry("usageDesc", String.class),
            Map.entry("sortOrder", Integer.class)
    );

    private static final Map<String, Function<SteamFluidizedImprovementPaymentResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("recordKey", r -> r.getRecordKey()),
            Map.entry("payMonth", r -> r.getPayMonth()),
            Map.entry("paymentAmount", r -> r.getPaymentAmount()),
            Map.entry("spentAmount", r -> r.getSpentAmount()),
            Map.entry("vendorName", r -> r.getVendorName()),
            Map.entry("usageDesc", r -> r.getUsageDesc()),
            Map.entry("sortOrder", r -> r.getSortOrder())
    );

    private static final Map<String, BiConsumer<SteamFluidizedImprovementPaymentSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("recordKey", (r, v) -> r.setRecordKey((String) v)),
            Map.entry("payMonth", (r, v) -> r.setPayMonth((String) v)),
            Map.entry("paymentAmount", (r, v) -> r.setPaymentAmount((BigDecimal) v)),
            Map.entry("spentAmount", (r, v) -> r.setSpentAmount((BigDecimal) v)),
            Map.entry("vendorName", (r, v) -> r.setVendorName((String) v)),
            Map.entry("usageDesc", (r, v) -> r.setUsageDesc((String) v)),
            Map.entry("sortOrder", (r, v) -> r.setSortOrder((Integer) v))
    );

    private final SteamFluidizedImprovementPaymentService service;

    public SteamFluidizedImprovementPaymentAdapter(SteamFluidizedImprovementPaymentService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "improvementPayment"; }

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
        for (SteamFluidizedImprovementPaymentResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedImprovementPaymentBulkRequest request = new SteamFluidizedImprovementPaymentBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedImprovementPaymentSaveRequest r = new SteamFluidizedImprovementPaymentSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedImprovementPaymentSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
