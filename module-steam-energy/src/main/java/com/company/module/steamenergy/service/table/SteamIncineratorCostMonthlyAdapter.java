package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorCostMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorCostMonthlyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorCostMonthlySaveRequest;
import com.company.module.steamenergy.service.SteamIncineratorCostMonthlyService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 소각로 총 운영비용 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_incinerator_cost_monthly / alias: incineratorCost
 */
@Component
public class SteamIncineratorCostMonthlyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("opBurnBurn1", "opBurnBurn2", "opBurnRate1", "opBurnRate2", "opCostUnit", "opCostDeduct", "selfSrfInQty", "selfSrfInRev", "selfSrfFloorRecycle", "selfSrfFloorBury", "selfSrfFlySolid", "selfSrfFlyBury", "selfSrfCostFloor", "selfSrfCostFly", "selfSrfCostMisc", "selfUtilP1", "selfUtilP2", "selfUtilPrate", "selfUtilNaohQty", "selfUtilNaohUnit", "selfUtilWater", "selfUtilWasteCharge", "selfUtilEtc", "selfTotalTotal", "selfTotalBurnRate", "selfTotalSteamRate", "matOilU1", "matOilU2", "matOilUnit", "matOilCkQty", "matOilCkUnit", "matChemUQty", "matChemUUnit", "matChemSQty", "matChemSUnit", "matMiscAQty", "matMiscAUnit", "matMiscKQty", "matMiscKUnit");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("opBurnBurn1", BigDecimal.class),
            Map.entry("opBurnBurn2", BigDecimal.class),
            Map.entry("opBurnRate1", BigDecimal.class),
            Map.entry("opBurnRate2", BigDecimal.class),
            Map.entry("opCostUnit", BigDecimal.class),
            Map.entry("opCostDeduct", BigDecimal.class),
            Map.entry("selfSrfInQty", BigDecimal.class),
            Map.entry("selfSrfInRev", BigDecimal.class),
            Map.entry("selfSrfFloorRecycle", BigDecimal.class),
            Map.entry("selfSrfFloorBury", BigDecimal.class),
            Map.entry("selfSrfFlySolid", BigDecimal.class),
            Map.entry("selfSrfFlyBury", BigDecimal.class),
            Map.entry("selfSrfCostFloor", BigDecimal.class),
            Map.entry("selfSrfCostFly", BigDecimal.class),
            Map.entry("selfSrfCostMisc", BigDecimal.class),
            Map.entry("selfUtilP1", BigDecimal.class),
            Map.entry("selfUtilP2", BigDecimal.class),
            Map.entry("selfUtilPrate", BigDecimal.class),
            Map.entry("selfUtilNaohQty", BigDecimal.class),
            Map.entry("selfUtilNaohUnit", BigDecimal.class),
            Map.entry("selfUtilWater", BigDecimal.class),
            Map.entry("selfUtilWasteCharge", BigDecimal.class),
            Map.entry("selfUtilEtc", BigDecimal.class),
            Map.entry("selfTotalTotal", BigDecimal.class),
            Map.entry("selfTotalBurnRate", BigDecimal.class),
            Map.entry("selfTotalSteamRate", BigDecimal.class),
            Map.entry("matOilU1", BigDecimal.class),
            Map.entry("matOilU2", BigDecimal.class),
            Map.entry("matOilUnit", BigDecimal.class),
            Map.entry("matOilCkQty", BigDecimal.class),
            Map.entry("matOilCkUnit", BigDecimal.class),
            Map.entry("matChemUQty", BigDecimal.class),
            Map.entry("matChemUUnit", BigDecimal.class),
            Map.entry("matChemSQty", BigDecimal.class),
            Map.entry("matChemSUnit", BigDecimal.class),
            Map.entry("matMiscAQty", BigDecimal.class),
            Map.entry("matMiscAUnit", BigDecimal.class),
            Map.entry("matMiscKQty", BigDecimal.class),
            Map.entry("matMiscKUnit", BigDecimal.class)
    );

    private static final Map<String, Function<SteamIncineratorCostMonthlyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("opBurnBurn1", r -> r.getOpBurnBurn1()),
            Map.entry("opBurnBurn2", r -> r.getOpBurnBurn2()),
            Map.entry("opBurnRate1", r -> r.getOpBurnRate1()),
            Map.entry("opBurnRate2", r -> r.getOpBurnRate2()),
            Map.entry("opCostUnit", r -> r.getOpCostUnit()),
            Map.entry("opCostDeduct", r -> r.getOpCostDeduct()),
            Map.entry("selfSrfInQty", r -> r.getSelfSrfInQty()),
            Map.entry("selfSrfInRev", r -> r.getSelfSrfInRev()),
            Map.entry("selfSrfFloorRecycle", r -> r.getSelfSrfFloorRecycle()),
            Map.entry("selfSrfFloorBury", r -> r.getSelfSrfFloorBury()),
            Map.entry("selfSrfFlySolid", r -> r.getSelfSrfFlySolid()),
            Map.entry("selfSrfFlyBury", r -> r.getSelfSrfFlyBury()),
            Map.entry("selfSrfCostFloor", r -> r.getSelfSrfCostFloor()),
            Map.entry("selfSrfCostFly", r -> r.getSelfSrfCostFly()),
            Map.entry("selfSrfCostMisc", r -> r.getSelfSrfCostMisc()),
            Map.entry("selfUtilP1", r -> r.getSelfUtilP1()),
            Map.entry("selfUtilP2", r -> r.getSelfUtilP2()),
            Map.entry("selfUtilPrate", r -> r.getSelfUtilPrate()),
            Map.entry("selfUtilNaohQty", r -> r.getSelfUtilNaohQty()),
            Map.entry("selfUtilNaohUnit", r -> r.getSelfUtilNaohUnit()),
            Map.entry("selfUtilWater", r -> r.getSelfUtilWater()),
            Map.entry("selfUtilWasteCharge", r -> r.getSelfUtilWasteCharge()),
            Map.entry("selfUtilEtc", r -> r.getSelfUtilEtc()),
            Map.entry("selfTotalTotal", r -> r.getSelfTotalTotal()),
            Map.entry("selfTotalBurnRate", r -> r.getSelfTotalBurnRate()),
            Map.entry("selfTotalSteamRate", r -> r.getSelfTotalSteamRate()),
            Map.entry("matOilU1", r -> r.getMatOilU1()),
            Map.entry("matOilU2", r -> r.getMatOilU2()),
            Map.entry("matOilUnit", r -> r.getMatOilUnit()),
            Map.entry("matOilCkQty", r -> r.getMatOilCkQty()),
            Map.entry("matOilCkUnit", r -> r.getMatOilCkUnit()),
            Map.entry("matChemUQty", r -> r.getMatChemUQty()),
            Map.entry("matChemUUnit", r -> r.getMatChemUUnit()),
            Map.entry("matChemSQty", r -> r.getMatChemSQty()),
            Map.entry("matChemSUnit", r -> r.getMatChemSUnit()),
            Map.entry("matMiscAQty", r -> r.getMatMiscAQty()),
            Map.entry("matMiscAUnit", r -> r.getMatMiscAUnit()),
            Map.entry("matMiscKQty", r -> r.getMatMiscKQty()),
            Map.entry("matMiscKUnit", r -> r.getMatMiscKUnit())
    );

    private static final Map<String, BiConsumer<SteamIncineratorCostMonthlySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("opBurnBurn1", (r, v) -> r.setOpBurnBurn1((BigDecimal) v)),
            Map.entry("opBurnBurn2", (r, v) -> r.setOpBurnBurn2((BigDecimal) v)),
            Map.entry("opBurnRate1", (r, v) -> r.setOpBurnRate1((BigDecimal) v)),
            Map.entry("opBurnRate2", (r, v) -> r.setOpBurnRate2((BigDecimal) v)),
            Map.entry("opCostUnit", (r, v) -> r.setOpCostUnit((BigDecimal) v)),
            Map.entry("opCostDeduct", (r, v) -> r.setOpCostDeduct((BigDecimal) v)),
            Map.entry("selfSrfInQty", (r, v) -> r.setSelfSrfInQty((BigDecimal) v)),
            Map.entry("selfSrfInRev", (r, v) -> r.setSelfSrfInRev((BigDecimal) v)),
            Map.entry("selfSrfFloorRecycle", (r, v) -> r.setSelfSrfFloorRecycle((BigDecimal) v)),
            Map.entry("selfSrfFloorBury", (r, v) -> r.setSelfSrfFloorBury((BigDecimal) v)),
            Map.entry("selfSrfFlySolid", (r, v) -> r.setSelfSrfFlySolid((BigDecimal) v)),
            Map.entry("selfSrfFlyBury", (r, v) -> r.setSelfSrfFlyBury((BigDecimal) v)),
            Map.entry("selfSrfCostFloor", (r, v) -> r.setSelfSrfCostFloor((BigDecimal) v)),
            Map.entry("selfSrfCostFly", (r, v) -> r.setSelfSrfCostFly((BigDecimal) v)),
            Map.entry("selfSrfCostMisc", (r, v) -> r.setSelfSrfCostMisc((BigDecimal) v)),
            Map.entry("selfUtilP1", (r, v) -> r.setSelfUtilP1((BigDecimal) v)),
            Map.entry("selfUtilP2", (r, v) -> r.setSelfUtilP2((BigDecimal) v)),
            Map.entry("selfUtilPrate", (r, v) -> r.setSelfUtilPrate((BigDecimal) v)),
            Map.entry("selfUtilNaohQty", (r, v) -> r.setSelfUtilNaohQty((BigDecimal) v)),
            Map.entry("selfUtilNaohUnit", (r, v) -> r.setSelfUtilNaohUnit((BigDecimal) v)),
            Map.entry("selfUtilWater", (r, v) -> r.setSelfUtilWater((BigDecimal) v)),
            Map.entry("selfUtilWasteCharge", (r, v) -> r.setSelfUtilWasteCharge((BigDecimal) v)),
            Map.entry("selfUtilEtc", (r, v) -> r.setSelfUtilEtc((BigDecimal) v)),
            Map.entry("selfTotalTotal", (r, v) -> r.setSelfTotalTotal((BigDecimal) v)),
            Map.entry("selfTotalBurnRate", (r, v) -> r.setSelfTotalBurnRate((BigDecimal) v)),
            Map.entry("selfTotalSteamRate", (r, v) -> r.setSelfTotalSteamRate((BigDecimal) v)),
            Map.entry("matOilU1", (r, v) -> r.setMatOilU1((BigDecimal) v)),
            Map.entry("matOilU2", (r, v) -> r.setMatOilU2((BigDecimal) v)),
            Map.entry("matOilUnit", (r, v) -> r.setMatOilUnit((BigDecimal) v)),
            Map.entry("matOilCkQty", (r, v) -> r.setMatOilCkQty((BigDecimal) v)),
            Map.entry("matOilCkUnit", (r, v) -> r.setMatOilCkUnit((BigDecimal) v)),
            Map.entry("matChemUQty", (r, v) -> r.setMatChemUQty((BigDecimal) v)),
            Map.entry("matChemUUnit", (r, v) -> r.setMatChemUUnit((BigDecimal) v)),
            Map.entry("matChemSQty", (r, v) -> r.setMatChemSQty((BigDecimal) v)),
            Map.entry("matChemSUnit", (r, v) -> r.setMatChemSUnit((BigDecimal) v)),
            Map.entry("matMiscAQty", (r, v) -> r.setMatMiscAQty((BigDecimal) v)),
            Map.entry("matMiscAUnit", (r, v) -> r.setMatMiscAUnit((BigDecimal) v)),
            Map.entry("matMiscKQty", (r, v) -> r.setMatMiscKQty((BigDecimal) v)),
            Map.entry("matMiscKUnit", (r, v) -> r.setMatMiscKUnit((BigDecimal) v))
    );

    private final SteamIncineratorCostMonthlyService service;

    public SteamIncineratorCostMonthlyAdapter(SteamIncineratorCostMonthlyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "incineratorCost"; }

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
        for (SteamIncineratorCostMonthlyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamIncineratorCostMonthlyBulkRequest request = new SteamIncineratorCostMonthlyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamIncineratorCostMonthlySaveRequest r = new SteamIncineratorCostMonthlySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamIncineratorCostMonthlySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
