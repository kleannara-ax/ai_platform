package com.company.module.steamenergy.service.table;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 유동상 세부 운영내역 셀 저장소 — 계산·엑셀 서비스가 쓰는 셀 주소({@code C18}, {@code AJ50}, {@code AK15},
 * {@code custom|구역|행|label·insertAfter·d일})를 규격 표 3개의 행·컬럼으로 옮긴다.
 *
 * <ul>
 *   <li>{@code {C~AG}{행}} (일 = 열 번호 − 2) → steam_fluidized_detail_daily ({@link FluidizedDetailFields#DAY_ROWS})</li>
 *   <li>{@code AJ{행}} · {@code AK15} · {@code AK16} · {@code C40} 의 글자 → steam_fluidized_detail_month</li>
 *   <li>{@code custom|…} → steam_fluidized_detail_custom_row</li>
 * </ul>
 * 돌려주는 행은 옛 셀 형식이다: month, table_name({@value #TABLE}), row_key, col_index(0), cell_value.
 */
@Component
@RequiredArgsConstructor
public class FluidizedDetailCellStore {

    public static final String TABLE = "fluidized_detail_main";
    /** 재고 이어 계산을 시작할 수 있는 가장 이른 해 (그 전에는 데이터가 없다) */
    static final int FIRST_YEAR = 2020;

    private static final Pattern CELL_REF = Pattern.compile("^([A-Z]{1,2})(\\d{1,3})$");
    private static final int FIRST_DAY_COL = 3;    // C
    private static final int LAST_DAY_COL = 33;    // AG
    private static final int PRICE_COL = 36;       // AJ
    private static final int COST_COL = 37;        // AK

    private final SteamFluidizedDetailDailyAdapter daily;
    private final SteamFluidizedDetailMonthAdapter monthly;
    private final SteamFluidizedDetailCustomRowAdapter custom;

    // ── 읽기 ─────────────────────────────────────────────────────────

    /** 월 키(YYYY-MM)의 셀 목록 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByMonthKey(String monthKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        YearMonth ym = parse(monthKey);
        if (ym == null) return out;
        emit(out, monthKey, daily.load(ym.getYear(), ym.getMonthValue()),
                monthly.load(ym.getYear(), ym.getMonthValue()), custom.load(ym.getYear(), ym.getMonthValue()));
        return out;
    }

    /** fromMonth ~ toMonth (YYYY-MM, 양 끝 포함)의 셀 목록 — 달 순서대로 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBetween(String fromMonth, String toMonth) {
        List<Map<String, Object>> out = new ArrayList<>();
        YearMonth from = parse(fromMonth), to = parse(toMonth);
        if (from == null || to == null) return out;
        for (int year = from.getYear(); year <= to.getYear(); year += 1) {
            Map<Integer, List<Map<String, Object>>> days = byMonth(daily.load(year, null), true);
            Map<Integer, List<Map<String, Object>>> months = byMonth(monthly.load(year, null), false);
            Map<Integer, List<Map<String, Object>>> customs = byMonth(custom.load(year, null), false);
            for (int month = 1; month <= 12; month += 1) {
                YearMonth ym = YearMonth.of(year, month);
                if (ym.isBefore(from) || ym.isAfter(to)) continue;
                emit(out, ym.toString(), days.getOrDefault(month, List.of()), months.getOrDefault(month, List.of()),
                        customs.getOrDefault(month, List.of()));
            }
        }
        return out;
    }

    /** 처음부터 toMonth 까지의 셀 목록 (재고를 첫 달부터 이어 계산할 때) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listThrough(String toMonth) {
        return listBetween(FIRST_YEAR + "-01", toMonth);
    }

    private void emit(List<Map<String, Object>> out, String monthKey, List<Map<String, Object>> dayRows,
                      List<Map<String, Object>> monthRows, List<Map<String, Object>> customRows) {
        boolean firstDayNote = false;
        for (Map<String, Object> row : dayRows) {
            int day = ((LocalDate) row.get("usageDate")).getDayOfMonth();
            String col = columnLabel(day + 2);
            for (Map.Entry<Integer, String> field : FluidizedDetailFields.DAY_ROWS.entrySet()) {
                String text = CellValues.text(row.get(field.getValue()));
                if (text == null) continue;
                add(out, monthKey, col + field.getKey(), text);
                if (day == 1 && field.getKey() == FluidizedDetailFields.NOTE_ROW) firstDayNote = true;
            }
        }
        for (Map<String, Object> row : monthRows) {
            FluidizedDetailFields.PRICE_ROWS.forEach((r, field) -> add(out, monthKey, "AJ" + r, CellValues.text(row.get(field))));
            FluidizedDetailFields.COST_ROWS.forEach((r, field) -> add(out, monthKey, "AK" + r, CellValues.text(row.get(field))));
            // 규사 사용 줄의 글자는 1일 칸 자리에 보인다 (그 칸에 숫자가 있으면 숫자가 먼저)
            if (!firstDayNote) add(out, monthKey, "C" + FluidizedDetailFields.NOTE_ROW, CellValues.text(row.get(FluidizedDetailFields.NOTE_FIELD)));
        }
        for (Map<String, Object> row : customRows) {
            String prefix = "custom|" + row.get("section") + "|" + row.get("rowKey") + "|";
            // 이름이 비어 있어도 행이 있다는 것을 알리려고 label 칸은 늘 낸다
            addRaw(out, monthKey, prefix + "label", row.get("label") == null ? "" : String.valueOf(row.get("label")));
            add(out, monthKey, prefix + "insertAfter", CellValues.text(row.get("insertAfter")));
            for (int day = 1; day <= 31; day += 1) add(out, monthKey, prefix + "d" + day, CellValues.text(row.get(dayField(day))));
        }
    }

    // ── 쓰기 ─────────────────────────────────────────────────────────

    /**
     * 그 달의 일별 · 월별 입력을 주어진 셀로 바꾼다 (파일이 그 달의 기준 — 목록에 없는 칸은 비운다).
     * 사용자가 추가한 행은 건드리지 않는다. 저장한 칸 수를 돌려준다.
     */
    @Transactional
    public int replaceMonth(int year, int month, Map<String, String> cells) {
        Parsed parsed = parseCells(year, month, cells, Map.of(), Map.of());
        daily.saveBulk(year, month, new ArrayList<>(parsed.days.values()), true);
        monthly.saveBulk(year, month, parsed.monthRow.size() > 2 ? List.of(parsed.monthRow) : List.of(), true);
        return parsed.saved;
    }

    /**
     * 주어진 칸만 덮어쓴다 — 그 날 · 그 달의 다른 칸과 목록에 없는 날은 그대로 둔다. 빈 값은 건너뛴다.
     * 저장한 칸 수를 돌려준다.
     */
    @Transactional
    public int mergeCells(int year, int month, Map<String, String> cells) {
        Map<Integer, Map<String, Object>> existingDays = new TreeMap<>();
        for (Map<String, Object> row : daily.load(year, month)) existingDays.put(((LocalDate) row.get("usageDate")).getDayOfMonth(), row);
        Map<String, Object> existingMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : monthly.load(year, month)) existingMonth.putAll(row);
        Parsed parsed = parseCells(year, month, cells, existingDays, existingMonth);
        if (!parsed.days.isEmpty()) daily.saveBulk(year, month, new ArrayList<>(parsed.days.values()), false);
        if (parsed.monthTouched) monthly.saveBulk(year, month, List.of(parsed.monthRow), false);
        return parsed.saved;
    }

    private static final class Parsed {
        final Map<Integer, Map<String, Object>> days = new TreeMap<>();
        final Map<String, Object> monthRow = new LinkedHashMap<>();
        boolean monthTouched;
        int saved;
    }

    private Parsed parseCells(int year, int month, Map<String, String> cells,
                              Map<Integer, Map<String, Object>> existingDays, Map<String, Object> existingMonth) {
        Parsed parsed = new Parsed();
        parsed.monthRow.putAll(existingMonth);
        parsed.monthRow.put("yearNo", year);
        parsed.monthRow.put("monthNo", month);
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        for (Map.Entry<String, String> cell : cells.entrySet()) {
            Matcher matcher = CELL_REF.matcher(cell.getKey() == null ? "" : cell.getKey());
            if (!matcher.matches()) continue;
            int col = columnNumber(matcher.group(1));
            int row = Integer.parseInt(matcher.group(2));
            String text = cell.getValue() == null ? "" : cell.getValue().trim();
            if (text.isEmpty()) continue;
            if (col >= FIRST_DAY_COL && col <= LAST_DAY_COL) {
                String field = FluidizedDetailFields.DAY_ROWS.get(row);
                int day = col - 2;
                if (field == null || day > lastDay) continue;
                Object value = CellValues.convert(text, BigDecimal.class);
                if (value == null) {
                    // 숫자 줄의 글자: 규사 사용 줄 1일 칸만 월별 비고로 받는다
                    if (row == FluidizedDetailFields.NOTE_ROW && day == 1) {
                        parsed.monthRow.put(FluidizedDetailFields.NOTE_FIELD, CellValues.convert(text, String.class));
                        parsed.monthTouched = true;
                        parsed.saved += 1;
                    }
                    continue;
                }
                parsed.days.computeIfAbsent(day, key -> {
                    Map<String, Object> base = existingDays.get(key);
                    Map<String, Object> created = base == null ? new LinkedHashMap<>() : new LinkedHashMap<>(base);
                    created.put("usageDate", LocalDate.of(year, month, key));
                    return created;
                }).put(field, value);
                parsed.saved += 1;
            } else if (col == PRICE_COL || col == COST_COL) {
                String field = (col == PRICE_COL ? FluidizedDetailFields.PRICE_ROWS : FluidizedDetailFields.COST_ROWS).get(row);
                Object value = field == null ? null : CellValues.convert(text, BigDecimal.class);
                if (value == null) continue;
                parsed.monthRow.put(field, value);
                parsed.monthTouched = true;
                parsed.saved += 1;
            }
        }
        return parsed;
    }

    // ── 도우미 ───────────────────────────────────────────────────────

    private static Map<Integer, List<Map<String, Object>>> byMonth(List<Map<String, Object>> rows, boolean dayTable) {
        Map<Integer, List<Map<String, Object>>> out = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            int month = dayTable ? ((LocalDate) row.get("usageDate")).getMonthValue() : (Integer) row.get("monthNo");
            out.computeIfAbsent(month, key -> new ArrayList<>()).add(row);
        }
        return out;
    }

    private static void add(List<Map<String, Object>> out, String monthKey, String rowKey, String text) {
        if (text == null || text.isEmpty()) return;
        addRaw(out, monthKey, rowKey, text);
    }

    private static void addRaw(List<Map<String, Object>> out, String monthKey, String rowKey, String text) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("month", monthKey);
        row.put("table_name", TABLE);
        row.put("row_key", rowKey);
        row.put("col_index", 0);
        row.put("cell_value", text);
        out.add(row);
    }

    private static YearMonth parse(String monthKey) {
        if (monthKey == null || !monthKey.matches("\\d{4}-\\d{2}")) return null;
        int month = Integer.parseInt(monthKey.substring(5, 7));
        return month < 1 || month > 12 ? null : YearMonth.of(Integer.parseInt(monthKey.substring(0, 4)), month);
    }

    static String dayField(int day) {
        return String.format("day%02d", day);
    }

    static int columnNumber(String label) {
        int n = 0;
        for (int i = 0; i < label.length(); i += 1) n = n * 26 + (label.charAt(i) - 'A' + 1);
        return n;
    }

    static String columnLabel(int oneBased) {
        StringBuilder sb = new StringBuilder();
        for (int n = oneBased; n > 0; n = (n - 1) / 26) sb.insert(0, (char) ('A' + (n - 1) % 26));
        return sb.toString();
    }
}
