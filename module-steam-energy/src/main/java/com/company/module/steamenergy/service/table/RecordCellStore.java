package com.company.module.steamenergy.service.table;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사고·정비 이력과 유동상 설비개선 목록의 셀 저장소 — 엑셀 서비스가 쓰는 옛 키(접두어:번호, col_index = 항목)를
 * 규격 표 3개의 행·컬럼으로 옮긴다. 옛 저장 위치는 steam_cell_value (표 이름 steam_cell_value, 월 키 YYYY-00).
 *
 * <ul>
 *   <li>{@code accident-record:} · {@code maintenance-record:} (유동상), {@code srf-accident-record:} (SRF),
 *       {@code incinerator-accident-record:} · {@code incinerator-maintenance-record:} (소각로) → steam_incident</li>
 *   <li>{@code improvement-monthly:} → steam_fluidized_improvement_payment</li>
 *   <li>{@code improvement-item:} → steam_fluidized_improvement_item</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RecordCellStore {

    public static final String LEGACY_TABLE = "steam_cell_value";

    /** 옛 셀 하나 (월 키는 그 해의 YYYY-00 으로 정해져 있다) */
    public record Cell(String rowKey, int col, String value) {}

    /** 접두어 → 표 종류 (+ 사고·정비 이력이면 시설·종류) */
    record Kind(String table, String facility, String kind) {}

    static final Map<String, Kind> PREFIXES = Map.of(
            "accident-record:", new Kind("incident", "FLUIDIZED", "ACCIDENT"),
            "maintenance-record:", new Kind("incident", "FLUIDIZED", "MAINTENANCE"),
            "srf-accident-record:", new Kind("incident", "SRF", "ACCIDENT"),
            "incinerator-accident-record:", new Kind("incident", "INCINERATOR", "ACCIDENT"),
            "incinerator-maintenance-record:", new Kind("incident", "INCINERATOR", "MAINTENANCE"),
            "improvement-monthly:", new Kind("payment", null, null),
            "improvement-item:", new Kind("item", null, null));

    static final Map<Integer, String> INCIDENT_COLS = Map.of(
            1, "occurDate", 2, "reason", 3, "actionTaken", 4, "downtimeHours", 5, "lossAmount", 6, "note", 7, "sortOrder");
    static final Map<Integer, String> PAYMENT_COLS = Map.of(
            1, "payMonth", 2, "paymentAmount", 3, "spentAmount", 4, "vendorName", 5, "usageDesc", 6, "sortOrder");
    static final Map<Integer, String> ITEM_COLS = Map.of(
            1, "category", 2, "itemNo", 3, "itemName", 4, "planCost", 5, "actualCost", 6, "status", 7, "remark", 8, "sortOrder");

    private final SteamIncidentAdapter incident;
    private final SteamFluidizedImprovementPaymentAdapter payment;
    private final SteamFluidizedImprovementItemAdapter item;

    static Kind kindOf(String rowKey) {
        if (rowKey == null) return null;
        int colon = rowKey.indexOf(':');
        return colon < 0 ? null : PREFIXES.get(rowKey.substring(0, colon + 1));
    }

    private SteamTableAdapter adapterOf(String table) {
        return switch (table) {
            case "incident" -> incident;
            case "payment" -> payment;
            default -> item;
        };
    }

    private static Map<Integer, String> colsOf(String table) {
        return switch (table) {
            case "incident" -> INCIDENT_COLS;
            case "payment" -> PAYMENT_COLS;
            default -> ITEM_COLS;
        };
    }

    /** 그 해의 목록을 옛 형식 셀로 편 것: month(YYYY-00), table_name, row_key, col_index, cell_value. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByYear(int year) {
        List<Map<String, Object>> out = new ArrayList<>();
        String monthKey = year + "-00";
        for (String table : List.of("incident", "payment", "item")) {
            Map<Integer, String> cols = colsOf(table);
            for (Map<String, Object> row : adapterOf(table).load(year, null)) {
                String rowKey = String.valueOf(row.get("recordKey"));
                cols.forEach((col, field) -> {
                    String text = CellValues.text(row.get(field));
                    if (text == null) return;
                    Map<String, Object> cell = new LinkedHashMap<>();
                    cell.put("month", monthKey);
                    cell.put("table_name", LEGACY_TABLE);
                    cell.put("row_key", rowKey);
                    cell.put("col_index", col);
                    cell.put("cell_value", text);
                    out.add(cell);
                });
            }
        }
        return out;
    }

    /**
     * 그 해의 목록 가운데 주어진 접두어의 기록을 cells 로 바꾼다 (파일이 그 목록의 기준).
     * 다른 접두어(다른 시설)의 기록은 그대로 둔다. 정렬순서 말고는 값이 없는 빈 줄은 저장하지 않는다.
     *
     * @return 저장한 칸 수
     */
    @Transactional
    public int replace(int year, List<String> prefixes, List<Cell> cells) {
        int saved = 0;
        for (String table : List.of("incident", "payment", "item")) {
            List<String> mine = prefixes.stream().filter(p -> PREFIXES.containsKey(p) && PREFIXES.get(p).table().equals(table)).toList();
            if (mine.isEmpty()) continue;
            SteamTableAdapter adapter = adapterOf(table);
            Map<Integer, String> cols = colsOf(table);
            Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
            // 다른 접두어의 기록은 그대로 둔다
            for (Map<String, Object> row : adapter.load(year, null)) {
                String key = String.valueOf(row.get("recordKey"));
                if (mine.stream().noneMatch(key::startsWith)) rows.put(key, new LinkedHashMap<>(row));
            }
            Map<String, Map<String, Object>> fresh = new LinkedHashMap<>();
            Map<String, Integer> filled = new LinkedHashMap<>();
            for (Cell cell : cells) {
                if (mine.stream().noneMatch(p -> cell.rowKey() != null && cell.rowKey().startsWith(p))) continue;
                String field = cols.get(cell.col());
                if (field == null) continue;
                Object value = CellValues.convert(cell.value(), adapter.typeOf(field));
                if (value == null) continue;
                Map<String, Object> row = fresh.computeIfAbsent(cell.rowKey(), key -> {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("yearNo", year);
                    created.put("recordKey", key);
                    Kind kind = kindOf(key);
                    if (kind != null && kind.facility() != null) {
                        created.put("facility", kind.facility());
                        created.put("kind", kind.kind());
                    }
                    return created;
                });
                row.put(field, value);
                if (!"sortOrder".equals(field)) filled.merge(cell.rowKey(), 1, Integer::sum);
                saved += 1;
            }
            fresh.forEach((key, row) -> {
                if (filled.containsKey(key)) rows.put(key, row);
            });
            adapter.saveBulk(year, null, new ArrayList<>(rows.values()), true);
        }
        return saved;
    }
}
