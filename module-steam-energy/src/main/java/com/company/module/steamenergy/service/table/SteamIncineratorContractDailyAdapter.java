package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorContractDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorContractDailyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorContractDailySaveRequest;
import com.company.module.steamenergy.service.SteamIncineratorContractDailyService;
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
 * 소각로 도급내역 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_incinerator_contract_daily / alias: incineratorContract
 */
@Component
public class SteamIncineratorContractDailyAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("u1Steam", "u1Unit", "u1Runtime", "u1Fixed", "u2Steam", "u2Unit", "u2Runtime", "u2Fixed", "realSteam1", "realUnit1", "realSteam2", "realUnit2");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("u1Steam", BigDecimal.class),
            Map.entry("u1Unit", BigDecimal.class),
            Map.entry("u1Runtime", BigDecimal.class),
            Map.entry("u1Fixed", BigDecimal.class),
            Map.entry("u2Steam", BigDecimal.class),
            Map.entry("u2Unit", BigDecimal.class),
            Map.entry("u2Runtime", BigDecimal.class),
            Map.entry("u2Fixed", BigDecimal.class),
            Map.entry("realSteam1", BigDecimal.class),
            Map.entry("realUnit1", BigDecimal.class),
            Map.entry("realSteam2", BigDecimal.class),
            Map.entry("realUnit2", BigDecimal.class)
    );

    private static final Map<String, Function<SteamIncineratorContractDailyResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("u1Steam", r -> r.getU1Steam()),
            Map.entry("u1Unit", r -> r.getU1Unit()),
            Map.entry("u1Runtime", r -> r.getU1Runtime()),
            Map.entry("u1Fixed", r -> r.getU1Fixed()),
            Map.entry("u2Steam", r -> r.getU2Steam()),
            Map.entry("u2Unit", r -> r.getU2Unit()),
            Map.entry("u2Runtime", r -> r.getU2Runtime()),
            Map.entry("u2Fixed", r -> r.getU2Fixed()),
            Map.entry("realSteam1", r -> r.getRealSteam1()),
            Map.entry("realUnit1", r -> r.getRealUnit1()),
            Map.entry("realSteam2", r -> r.getRealSteam2()),
            Map.entry("realUnit2", r -> r.getRealUnit2())
    );

    private static final Map<String, BiConsumer<SteamIncineratorContractDailySaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("u1Steam", (r, v) -> r.setU1Steam((BigDecimal) v)),
            Map.entry("u1Unit", (r, v) -> r.setU1Unit((BigDecimal) v)),
            Map.entry("u1Runtime", (r, v) -> r.setU1Runtime((BigDecimal) v)),
            Map.entry("u1Fixed", (r, v) -> r.setU1Fixed((BigDecimal) v)),
            Map.entry("u2Steam", (r, v) -> r.setU2Steam((BigDecimal) v)),
            Map.entry("u2Unit", (r, v) -> r.setU2Unit((BigDecimal) v)),
            Map.entry("u2Runtime", (r, v) -> r.setU2Runtime((BigDecimal) v)),
            Map.entry("u2Fixed", (r, v) -> r.setU2Fixed((BigDecimal) v)),
            Map.entry("realSteam1", (r, v) -> r.setRealSteam1((BigDecimal) v)),
            Map.entry("realUnit1", (r, v) -> r.setRealUnit1((BigDecimal) v)),
            Map.entry("realSteam2", (r, v) -> r.setRealSteam2((BigDecimal) v)),
            Map.entry("realUnit2", (r, v) -> r.setRealUnit2((BigDecimal) v))
    );

    private final SteamIncineratorContractDailyService service;

    public SteamIncineratorContractDailyAdapter(SteamIncineratorContractDailyService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "incineratorContract"; }

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
        for (SteamIncineratorContractDailyResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamIncineratorContractDailyBulkRequest request = new SteamIncineratorContractDailyBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamIncineratorContractDailySaveRequest r = new SteamIncineratorContractDailySaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamIncineratorContractDailySaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
