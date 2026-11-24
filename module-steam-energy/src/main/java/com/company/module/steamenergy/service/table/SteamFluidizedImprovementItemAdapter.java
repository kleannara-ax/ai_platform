package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementItemBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementItemResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementItemSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedImprovementItemService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 유동상 설비개선 항목 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_improvement_item / alias: improvementItem
 */
@Component
public class SteamFluidizedImprovementItemAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "recordKey");
    private static final List<String> VALUE_FIELDS = List.of("category", "itemNo", "itemName", "planCost", "actualCost", "status", "remark", "sortOrder");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("recordKey", String.class),
            Map.entry("category", String.class),
            Map.entry("itemNo", String.class),
            Map.entry("itemName", String.class),
            Map.entry("planCost", BigDecimal.class),
            Map.entry("actualCost", BigDecimal.class),
            Map.entry("status", String.class),
            Map.entry("remark", String.class),
            Map.entry("sortOrder", Integer.class)
    );

    private static final Map<String, Function<SteamFluidizedImprovementItemResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("recordKey", r -> r.getRecordKey()),
            Map.entry("category", r -> r.getCategory()),
            Map.entry("itemNo", r -> r.getItemNo()),
            Map.entry("itemName", r -> r.getItemName()),
            Map.entry("planCost", r -> r.getPlanCost()),
            Map.entry("actualCost", r -> r.getActualCost()),
            Map.entry("status", r -> r.getStatus()),
            Map.entry("remark", r -> r.getRemark()),
            Map.entry("sortOrder", r -> r.getSortOrder())
    );

    private static final Map<String, BiConsumer<SteamFluidizedImprovementItemSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("recordKey", (r, v) -> r.setRecordKey((String) v)),
            Map.entry("category", (r, v) -> r.setCategory((String) v)),
            Map.entry("itemNo", (r, v) -> r.setItemNo((String) v)),
            Map.entry("itemName", (r, v) -> r.setItemName((String) v)),
            Map.entry("planCost", (r, v) -> r.setPlanCost((BigDecimal) v)),
            Map.entry("actualCost", (r, v) -> r.setActualCost((BigDecimal) v)),
            Map.entry("status", (r, v) -> r.setStatus((String) v)),
            Map.entry("remark", (r, v) -> r.setRemark((String) v)),
            Map.entry("sortOrder", (r, v) -> r.setSortOrder((Integer) v))
    );

    private final SteamFluidizedImprovementItemService service;

    public SteamFluidizedImprovementItemAdapter(SteamFluidizedImprovementItemService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "improvementItem"; }

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
        for (SteamFluidizedImprovementItemResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedImprovementItemBulkRequest request = new SteamFluidizedImprovementItemBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedImprovementItemSaveRequest r = new SteamFluidizedImprovementItemSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedImprovementItemSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
