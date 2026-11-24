package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailCustomRowBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailCustomRowResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailCustomRowSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedDetailCustomRowService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 유동상 세부 운영내역 (추가 행) — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_fluidized_detail_custom_row / alias: fluidDetailCustomRow
 */
@Component
public class SteamFluidizedDetailCustomRowAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("yearNo", "monthNo", "section", "rowKey");
    private static final List<String> VALUE_FIELDS = List.of("label", "insertAfter", "day01", "day02", "day03", "day04", "day05", "day06", "day07", "day08", "day09", "day10", "day11", "day12", "day13", "day14", "day15", "day16", "day17", "day18", "day19", "day20", "day21", "day22", "day23", "day24", "day25", "day26", "day27", "day28", "day29", "day30", "day31");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("yearNo", Integer.class),
            Map.entry("monthNo", Integer.class),
            Map.entry("section", String.class),
            Map.entry("rowKey", String.class),
            Map.entry("label", String.class),
            Map.entry("insertAfter", Integer.class),
            Map.entry("day01", BigDecimal.class),
            Map.entry("day02", BigDecimal.class),
            Map.entry("day03", BigDecimal.class),
            Map.entry("day04", BigDecimal.class),
            Map.entry("day05", BigDecimal.class),
            Map.entry("day06", BigDecimal.class),
            Map.entry("day07", BigDecimal.class),
            Map.entry("day08", BigDecimal.class),
            Map.entry("day09", BigDecimal.class),
            Map.entry("day10", BigDecimal.class),
            Map.entry("day11", BigDecimal.class),
            Map.entry("day12", BigDecimal.class),
            Map.entry("day13", BigDecimal.class),
            Map.entry("day14", BigDecimal.class),
            Map.entry("day15", BigDecimal.class),
            Map.entry("day16", BigDecimal.class),
            Map.entry("day17", BigDecimal.class),
            Map.entry("day18", BigDecimal.class),
            Map.entry("day19", BigDecimal.class),
            Map.entry("day20", BigDecimal.class),
            Map.entry("day21", BigDecimal.class),
            Map.entry("day22", BigDecimal.class),
            Map.entry("day23", BigDecimal.class),
            Map.entry("day24", BigDecimal.class),
            Map.entry("day25", BigDecimal.class),
            Map.entry("day26", BigDecimal.class),
            Map.entry("day27", BigDecimal.class),
            Map.entry("day28", BigDecimal.class),
            Map.entry("day29", BigDecimal.class),
            Map.entry("day30", BigDecimal.class),
            Map.entry("day31", BigDecimal.class)
    );

    private static final Map<String, Function<SteamFluidizedDetailCustomRowResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("yearNo", r -> r.getYearNo()),
            Map.entry("monthNo", r -> r.getMonthNo()),
            Map.entry("section", r -> r.getSection()),
            Map.entry("rowKey", r -> r.getRowKey()),
            Map.entry("label", r -> r.getLabel()),
            Map.entry("insertAfter", r -> r.getInsertAfter()),
            Map.entry("day01", r -> r.getDay01()),
            Map.entry("day02", r -> r.getDay02()),
            Map.entry("day03", r -> r.getDay03()),
            Map.entry("day04", r -> r.getDay04()),
            Map.entry("day05", r -> r.getDay05()),
            Map.entry("day06", r -> r.getDay06()),
            Map.entry("day07", r -> r.getDay07()),
            Map.entry("day08", r -> r.getDay08()),
            Map.entry("day09", r -> r.getDay09()),
            Map.entry("day10", r -> r.getDay10()),
            Map.entry("day11", r -> r.getDay11()),
            Map.entry("day12", r -> r.getDay12()),
            Map.entry("day13", r -> r.getDay13()),
            Map.entry("day14", r -> r.getDay14()),
            Map.entry("day15", r -> r.getDay15()),
            Map.entry("day16", r -> r.getDay16()),
            Map.entry("day17", r -> r.getDay17()),
            Map.entry("day18", r -> r.getDay18()),
            Map.entry("day19", r -> r.getDay19()),
            Map.entry("day20", r -> r.getDay20()),
            Map.entry("day21", r -> r.getDay21()),
            Map.entry("day22", r -> r.getDay22()),
            Map.entry("day23", r -> r.getDay23()),
            Map.entry("day24", r -> r.getDay24()),
            Map.entry("day25", r -> r.getDay25()),
            Map.entry("day26", r -> r.getDay26()),
            Map.entry("day27", r -> r.getDay27()),
            Map.entry("day28", r -> r.getDay28()),
            Map.entry("day29", r -> r.getDay29()),
            Map.entry("day30", r -> r.getDay30()),
            Map.entry("day31", r -> r.getDay31())
    );

    private static final Map<String, BiConsumer<SteamFluidizedDetailCustomRowSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("yearNo", (r, v) -> r.setYearNo((Integer) v)),
            Map.entry("monthNo", (r, v) -> r.setMonthNo((Integer) v)),
            Map.entry("section", (r, v) -> r.setSection((String) v)),
            Map.entry("rowKey", (r, v) -> r.setRowKey((String) v)),
            Map.entry("label", (r, v) -> r.setLabel((String) v)),
            Map.entry("insertAfter", (r, v) -> r.setInsertAfter((Integer) v)),
            Map.entry("day01", (r, v) -> r.setDay01((BigDecimal) v)),
            Map.entry("day02", (r, v) -> r.setDay02((BigDecimal) v)),
            Map.entry("day03", (r, v) -> r.setDay03((BigDecimal) v)),
            Map.entry("day04", (r, v) -> r.setDay04((BigDecimal) v)),
            Map.entry("day05", (r, v) -> r.setDay05((BigDecimal) v)),
            Map.entry("day06", (r, v) -> r.setDay06((BigDecimal) v)),
            Map.entry("day07", (r, v) -> r.setDay07((BigDecimal) v)),
            Map.entry("day08", (r, v) -> r.setDay08((BigDecimal) v)),
            Map.entry("day09", (r, v) -> r.setDay09((BigDecimal) v)),
            Map.entry("day10", (r, v) -> r.setDay10((BigDecimal) v)),
            Map.entry("day11", (r, v) -> r.setDay11((BigDecimal) v)),
            Map.entry("day12", (r, v) -> r.setDay12((BigDecimal) v)),
            Map.entry("day13", (r, v) -> r.setDay13((BigDecimal) v)),
            Map.entry("day14", (r, v) -> r.setDay14((BigDecimal) v)),
            Map.entry("day15", (r, v) -> r.setDay15((BigDecimal) v)),
            Map.entry("day16", (r, v) -> r.setDay16((BigDecimal) v)),
            Map.entry("day17", (r, v) -> r.setDay17((BigDecimal) v)),
            Map.entry("day18", (r, v) -> r.setDay18((BigDecimal) v)),
            Map.entry("day19", (r, v) -> r.setDay19((BigDecimal) v)),
            Map.entry("day20", (r, v) -> r.setDay20((BigDecimal) v)),
            Map.entry("day21", (r, v) -> r.setDay21((BigDecimal) v)),
            Map.entry("day22", (r, v) -> r.setDay22((BigDecimal) v)),
            Map.entry("day23", (r, v) -> r.setDay23((BigDecimal) v)),
            Map.entry("day24", (r, v) -> r.setDay24((BigDecimal) v)),
            Map.entry("day25", (r, v) -> r.setDay25((BigDecimal) v)),
            Map.entry("day26", (r, v) -> r.setDay26((BigDecimal) v)),
            Map.entry("day27", (r, v) -> r.setDay27((BigDecimal) v)),
            Map.entry("day28", (r, v) -> r.setDay28((BigDecimal) v)),
            Map.entry("day29", (r, v) -> r.setDay29((BigDecimal) v)),
            Map.entry("day30", (r, v) -> r.setDay30((BigDecimal) v)),
            Map.entry("day31", (r, v) -> r.setDay31((BigDecimal) v))
    );

    private final SteamFluidizedDetailCustomRowService service;

    public SteamFluidizedDetailCustomRowAdapter(SteamFluidizedDetailCustomRowService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "fluidDetailCustomRow"; }

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
        for (SteamFluidizedDetailCustomRowResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamFluidizedDetailCustomRowBulkRequest request = new SteamFluidizedDetailCustomRowBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamFluidizedDetailCustomRowSaveRequest r = new SteamFluidizedDetailCustomRowSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamFluidizedDetailCustomRowSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
