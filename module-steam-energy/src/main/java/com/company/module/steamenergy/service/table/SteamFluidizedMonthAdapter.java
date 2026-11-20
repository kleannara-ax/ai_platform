package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedMonthBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedMonthResponse;
import com.company.module.steamenergy.dto.SteamFluidizedMonthSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedMonthService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 유동상 월별 계획·비용 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_month / alias: fluidMonth
 */
@Component
public class SteamFluidizedMonthAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("planOperating", "planPower", "planWaste", "planSrfIncome", "summaryRemark", "monthNote", "laborCost", "operatingPayCost", "improvementPayCost", "improvementCost", "deductionCost", "extraPayment", "wastewaterQty", "soilQty", "soilCost", "contractCost", "operatingCost");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("planOperating", BigDecimal.class),
            Map.entry("planPower", BigDecimal.class),
            Map.entry("planWaste", BigDecimal.class),
            Map.entry("planSrfIncome", BigDecimal.class),
            Map.entry("summaryRemark", String.class),
            Map.entry("monthNote", String.class),
            Map.entry("laborCost", BigDecimal.class),
            Map.entry("operatingPayCost", BigDecimal.class),
            Map.entry("improvementPayCost", BigDecimal.class),
            Map.entry("improvementCost", BigDecimal.class),
            Map.entry("deductionCost", BigDecimal.class),
            Map.entry("extraPayment", BigDecimal.class),
            Map.entry("wastewaterQty", BigDecimal.class),
            Map.entry("soilQty", BigDecimal.class),
            Map.entry("soilCost", BigDecimal.class),
            Map.entry("contractCost", BigDecimal.class),
            Map.entry("operatingCost", BigDecimal.class)
    );

    private static final Map<String, Function<SteamFluidizedMonthResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("planOperating", r -> r.getPlanOperating()),
            Map.entry("planPower", r -> r.getPlanPower()),
            Map.entry("planWaste", r -> r.getPlanWaste()),
            Map.entry("planSrfIncome", r -> r.getPlanSrfIncome()),
            Map.entry("summaryRemark", r -> r.getSummaryRemark()),
            Map.entry("monthNote", r -> r.getMonthNote()),
            Map.entry("laborCost", r -> r.getLaborCost()),
            Map.entry("operatingPayCost", r -> r.getOperatingPayCost()),
            Map.entry("improvementPayCost", r -> r.getImprovementPayCost()),
            Map.entry("improvementCost", r -> r.getImprovementCost()),
            Map.entry("deductionCost", r -> r.getDeductionCost()),
            Map.entry("extraPayment", r -> r.getExtraPayment()),
            Map.entry("wastewaterQty", r -> r.getWastewaterQty()),
            Map.entry("soilQty", r -> r.getSoilQty()),
            Map.entry("soilCost", r -> r.getSoilCost()),
            Map.entry("contractCost", r -> r.getContractCost()),
            Map.entry("operatingCost", r -> r.getOperatingCost())
    );

    private static final Map<String, BiConsumer<SteamFluidizedMonthSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("planOperating", (r, v) -> r.setPlanOperating((BigDecimal) v)),
            Map.entry("planPower", (r, v) -> r.setPlanPower((BigDecimal) v)),
            Map.entry("planWaste", (r, v) -> r.setPlanWaste((BigDecimal) v)),
            Map.entry("planSrfIncome", (r, v) -> r.setPlanSrfIncome((BigDecimal) v)),
            Map.entry("summaryRemark", (r, v) -> r.setSummaryRemark((String) v)),
            Map.entry("monthNote", (r, v) -> r.setMonthNote((String) v)),
            Map.entry("laborCost", (r, v) -> r.setLaborCost((BigDecimal) v)),
            Map.entry("operatingPayCost", (r, v) -> r.setOperatingPayCost((BigDecimal) v)),
            Map.entry("improvementPayCost", (r, v) -> r.setImprovementPayCost((BigDecimal) v)),
            Map.entry("improvementCost", (r, v) -> r.setImprovementCost((BigDecimal) v)),
            Map.entry("deductionCost", (r, v) -> r.setDeductionCost((BigDecimal) v)),
            Map.entry("extraPayment", (r, v) -> r.setExtraPayment((BigDecimal) v)),
            Map.entry("wastewaterQty", (r, v) -> r.setWastewaterQty((BigDecimal) v)),
            Map.entry("soilQty", (r, v) -> r.setSoilQty((BigDecimal) v)),
            Map.entry("soilCost", (r, v) -> r.setSoilCost((BigDecimal) v)),
            Map.entry("contractCost", (r, v) -> r.setContractCost((BigDecimal) v)),
            Map.entry("operatingCost", (r, v) -> r.setOperatingCost((BigDecimal) v))
    );

    private final SteamFluidizedMonthService service;

    public SteamFluidizedMonthAdapter(SteamFluidizedMonthService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidMonth"; }

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
        for (SteamFluidizedMonthResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedMonthBulkRequest request = new SteamFluidizedMonthBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedMonthSaveRequest r = new SteamFluidizedMonthSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedMonthSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
