package com.company.module.steamenergy.service.table;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 운전일지 셀 ↔ 규격 표(하루 한 행) 변환 — 엑셀 서비스 공용.
 *
 * <p>운전일지 엑셀 서비스는 값을 "일 + 항목 번호" 로 다룬다. 여기서 항목 번호 → 필드 표를 받아
 * 하루 한 행인 규격 표의 컬럼으로 옮긴다. 셀 키는 {@code "일|항목번호"} (일은 앞 0 없이).
 */
public final class DailyLogCells {

    private DailyLogCells() {
    }

    public static String key(int day, int colIndex) {
        return day + "|" + colIndex;
    }

    /** 그 달의 값 — {@code "일|항목번호"} → 셀 문자열 (값이 없는 칸은 없다) */
    public static Map<String, String> load(SteamTableAdapter adapter, Map<Integer, String> fields, int year, int month) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (Map<String, Object> row : adapter.load(year, month)) {
            int day = ((LocalDate) row.get("usageDate")).getDayOfMonth();
            for (Map.Entry<Integer, String> field : fields.entrySet()) {
                String text = CellValues.text(row.get(field.getValue()));
                if (text != null) cells.put(key(day, field.getKey()), text);
            }
        }
        return cells;
    }

    /**
     * 주어진 칸만 덮어쓴다 — 그 날의 다른 칸과 목록에 없는 날은 그대로 둔다.
     * 빈 값, 숫자 칸의 글자, 그 달에 없는 날, 표에 없는 항목 번호는 건너뛴다. 저장한 칸 수를 돌려준다.
     */
    public static int merge(SteamTableAdapter adapter, Map<Integer, String> fields, int year, int month, Map<String, String> cells) {
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        Map<Integer, Map<String, Object>> existing = new TreeMap<>();
        for (Map<String, Object> row : adapter.load(year, month)) {
            existing.put(((LocalDate) row.get("usageDate")).getDayOfMonth(), row);
        }
        Map<Integer, Map<String, Object>> changed = new TreeMap<>();
        int saved = 0;
        for (Map.Entry<String, String> cell : cells.entrySet()) {
            int bar = cell.getKey().indexOf('|');
            if (bar < 1) continue;
            int day;
            int colIndex;
            try {
                day = Integer.parseInt(cell.getKey().substring(0, bar));
                colIndex = Integer.parseInt(cell.getKey().substring(bar + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            String field = fields.get(colIndex);
            if (field == null || day < 1 || day > lastDay) continue;
            Object value = CellValues.convert(cell.getValue(), adapter.typeOf(field));
            if (value == null) continue;
            Map<String, Object> row = changed.computeIfAbsent(day, key -> {
                Map<String, Object> base = existing.get(key);
                Map<String, Object> created = base == null ? new LinkedHashMap<>() : new LinkedHashMap<>(base);
                created.put("usageDate", LocalDate.of(year, month, key));
                return created;
            });
            row.put(field, value);
            saved += 1;
        }
        if (!changed.isEmpty()) adapter.saveBulk(year, month, new ArrayList<>(changed.values()), false);
        return saved;
    }
}
