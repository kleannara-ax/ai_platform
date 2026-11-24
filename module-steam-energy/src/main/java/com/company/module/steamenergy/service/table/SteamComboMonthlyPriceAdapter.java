package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboMonthlyPriceBulkRequest;
import com.company.module.steamenergy.dto.SteamComboMonthlyPriceResponse;
import com.company.module.steamenergy.dto.SteamComboMonthlyPriceSaveRequest;
import com.company.module.steamenergy.service.SteamComboMonthlyPriceService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 복합보일러 월 연료단가 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_combo_monthly_price / alias: comboMonthly
 */
@Component
public class SteamComboMonthlyPriceAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo");
    private static final List<String> VALUE_FIELDS = List.of("fuelPriceLngBoiler", "fuelPriceFluidized", "fuelPriceCombo", "fuelPriceWaste", "fuelPriceExternal", "ventLossUnitPrice");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("fuelPriceLngBoiler", BigDecimal.class),
            Map.entry("fuelPriceFluidized", BigDecimal.class),
            Map.entry("fuelPriceCombo", BigDecimal.class),
            Map.entry("fuelPriceWaste", BigDecimal.class),
            Map.entry("fuelPriceExternal", BigDecimal.class),
            Map.entry("ventLossUnitPrice", BigDecimal.class)
    );

    private static final Map<String, Function<SteamComboMonthlyPriceResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("fuelPriceLngBoiler", r -> r.getFuelPriceLngBoiler()),
            Map.entry("fuelPriceFluidized", r -> r.getFuelPriceFluidized()),
            Map.entry("fuelPriceCombo", r -> r.getFuelPriceCombo()),
            Map.entry("fuelPriceWaste", r -> r.getFuelPriceWaste()),
            Map.entry("fuelPriceExternal", r -> r.getFuelPriceExternal()),
            Map.entry("ventLossUnitPrice", r -> r.getVentLossUnitPrice())
    );

    private static final Map<String, BiConsumer<SteamComboMonthlyPriceSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("fuelPriceLngBoiler", (r, v) -> r.setFuelPriceLngBoiler((BigDecimal) v)),
            Map.entry("fuelPriceFluidized", (r, v) -> r.setFuelPriceFluidized((BigDecimal) v)),
            Map.entry("fuelPriceCombo", (r, v) -> r.setFuelPriceCombo((BigDecimal) v)),
            Map.entry("fuelPriceWaste", (r, v) -> r.setFuelPriceWaste((BigDecimal) v)),
            Map.entry("fuelPriceExternal", (r, v) -> r.setFuelPriceExternal((BigDecimal) v)),
            Map.entry("ventLossUnitPrice", (r, v) -> r.setVentLossUnitPrice((BigDecimal) v))
    );

    private final SteamComboMonthlyPriceService service;

    public SteamComboMonthlyPriceAdapter(SteamComboMonthlyPriceService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "comboMonthly"; }

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
        for (SteamComboMonthlyPriceResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamComboMonthlyPriceBulkRequest request = new SteamComboMonthlyPriceBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamComboMonthlyPriceSaveRequest r = new SteamComboMonthlyPriceSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamComboMonthlyPriceSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
