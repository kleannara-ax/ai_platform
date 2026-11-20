package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboDailyProductionBulkRequest;
import com.company.module.steamenergy.dto.SteamComboDailyProductionResponse;
import com.company.module.steamenergy.dto.SteamComboDailyProductionSaveRequest;
import com.company.module.steamenergy.service.SteamComboDailyProductionService;
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
 * 복합보일러 스팀생산실적 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_combo_daily_production / alias: comboProduction
 */
@Component
public class SteamComboDailyProductionAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("comboSteam", "comboCondensate", "comboMakeup", "comboDeaerator", "comboProcess", "comboDilution", "externalSteam", "externalCondensate", "externalMakeup", "wasteSteam1", "wasteSteam2", "kn20", "kn50", "fluidizedSteam", "fluidizedCondensate", "runtime20", "runtime50", "runtimeFluidized");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("comboSteam", BigDecimal.class),
            Map.entry("comboCondensate", BigDecimal.class),
            Map.entry("comboMakeup", BigDecimal.class),
            Map.entry("comboDeaerator", BigDecimal.class),
            Map.entry("comboProcess", BigDecimal.class),
            Map.entry("comboDilution", BigDecimal.class),
            Map.entry("externalSteam", BigDecimal.class),
            Map.entry("externalCondensate", BigDecimal.class),
            Map.entry("externalMakeup", BigDecimal.class),
            Map.entry("wasteSteam1", BigDecimal.class),
            Map.entry("wasteSteam2", BigDecimal.class),
            Map.entry("kn20", BigDecimal.class),
            Map.entry("kn50", BigDecimal.class),
            Map.entry("fluidizedSteam", BigDecimal.class),
            Map.entry("fluidizedCondensate", BigDecimal.class),
            Map.entry("runtime20", BigDecimal.class),
            Map.entry("runtime50", BigDecimal.class),
            Map.entry("runtimeFluidized", BigDecimal.class)
    );

    private static final Map<String, Function<SteamComboDailyProductionResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("comboSteam", r -> r.getComboSteam()),
            Map.entry("comboCondensate", r -> r.getComboCondensate()),
            Map.entry("comboMakeup", r -> r.getComboMakeup()),
            Map.entry("comboDeaerator", r -> r.getComboDeaerator()),
            Map.entry("comboProcess", r -> r.getComboProcess()),
            Map.entry("comboDilution", r -> r.getComboDilution()),
            Map.entry("externalSteam", r -> r.getExternalSteam()),
            Map.entry("externalCondensate", r -> r.getExternalCondensate()),
            Map.entry("externalMakeup", r -> r.getExternalMakeup()),
            Map.entry("wasteSteam1", r -> r.getWasteSteam1()),
            Map.entry("wasteSteam2", r -> r.getWasteSteam2()),
            Map.entry("kn20", r -> r.getKn20()),
            Map.entry("kn50", r -> r.getKn50()),
            Map.entry("fluidizedSteam", r -> r.getFluidizedSteam()),
            Map.entry("fluidizedCondensate", r -> r.getFluidizedCondensate()),
            Map.entry("runtime20", r -> r.getRuntime20()),
            Map.entry("runtime50", r -> r.getRuntime50()),
            Map.entry("runtimeFluidized", r -> r.getRuntimeFluidized())
    );

    private static final Map<String, BiConsumer<SteamComboDailyProductionSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("comboSteam", (r, v) -> r.setComboSteam((BigDecimal) v)),
            Map.entry("comboCondensate", (r, v) -> r.setComboCondensate((BigDecimal) v)),
            Map.entry("comboMakeup", (r, v) -> r.setComboMakeup((BigDecimal) v)),
            Map.entry("comboDeaerator", (r, v) -> r.setComboDeaerator((BigDecimal) v)),
            Map.entry("comboProcess", (r, v) -> r.setComboProcess((BigDecimal) v)),
            Map.entry("comboDilution", (r, v) -> r.setComboDilution((BigDecimal) v)),
            Map.entry("externalSteam", (r, v) -> r.setExternalSteam((BigDecimal) v)),
            Map.entry("externalCondensate", (r, v) -> r.setExternalCondensate((BigDecimal) v)),
            Map.entry("externalMakeup", (r, v) -> r.setExternalMakeup((BigDecimal) v)),
            Map.entry("wasteSteam1", (r, v) -> r.setWasteSteam1((BigDecimal) v)),
            Map.entry("wasteSteam2", (r, v) -> r.setWasteSteam2((BigDecimal) v)),
            Map.entry("kn20", (r, v) -> r.setKn20((BigDecimal) v)),
            Map.entry("kn50", (r, v) -> r.setKn50((BigDecimal) v)),
            Map.entry("fluidizedSteam", (r, v) -> r.setFluidizedSteam((BigDecimal) v)),
            Map.entry("fluidizedCondensate", (r, v) -> r.setFluidizedCondensate((BigDecimal) v)),
            Map.entry("runtime20", (r, v) -> r.setRuntime20((BigDecimal) v)),
            Map.entry("runtime50", (r, v) -> r.setRuntime50((BigDecimal) v)),
            Map.entry("runtimeFluidized", (r, v) -> r.setRuntimeFluidized((BigDecimal) v))
    );

    private final SteamComboDailyProductionService service;

    public SteamComboDailyProductionAdapter(SteamComboDailyProductionService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "comboProduction"; }

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
        for (SteamComboDailyProductionResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamComboDailyProductionBulkRequest request = new SteamComboDailyProductionBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamComboDailyProductionSaveRequest r = new SteamComboDailyProductionSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamComboDailyProductionSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
