package com.company.module.steamenergy.service.table;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 * 소각로 위탁 셀 저장소 — 엑셀 서비스가 쓰는 옛 키를 규격 표 4개의 행·컬럼으로 옮긴다.
 *
 * <ul>
 *   <li>{@code incinerator_contract} (월 키 YYYY-MM): {@code unit1|일|steam} … → steam_incinerator_contract_daily,
 *       {@code summary|0|labor} → steam_incinerator_plan_monthly.CONTRACT_LABOR</li>
 *   <li>{@code incinerator_plan_vs} (월 키 YYYY-MM = 고른 달): {@code labor|plan-m월} … → 그 월의 행,
 *       {@code break|…} → 고른 달의 행, {@code notes|m월-u1} → 그 월의 행</li>
 *   <li>{@code incinerator_operation} (연 키 YYYY): {@code hours|월|u1-plan} … → steam_incinerator_operation_monthly</li>
 *   <li>{@code incinerator_total_cost} (연 키 YYYY): {@code 구분|월|항목} → steam_incinerator_cost_monthly (필드 = 구분-항목 camelCase)</li>
 * </ul>
 *
 * <p>계산값 키(oper, stop, sub, total|actual 등)는 표에 컬럼이 없어 저장하지 않는다 — 읽을 때는 엑셀 서비스가 입력값에서 다시 계산한다.
 */
@Component
@RequiredArgsConstructor
public class IncineratorCellStore {

    public static final String CONTRACT = "incinerator_contract";
    public static final String PLAN_VS = "incinerator_plan_vs";
    public static final String OPERATION = "incinerator_operation";
    public static final String TOTAL_COST = "incinerator_total_cost";

    /** 도급내역: "구분|항목" → 필드 */
    static final Map<String, String> CONTRACT_KEYS = Map.ofEntries(
            Map.entry("unit1|steam", "u1Steam"), Map.entry("unit1|unit", "u1Unit"),
            Map.entry("unit1|runtime", "u1Runtime"), Map.entry("unit1|fixed", "u1Fixed"),
            Map.entry("unit2|steam", "u2Steam"), Map.entry("unit2|unit", "u2Unit"),
            Map.entry("unit2|runtime", "u2Runtime"), Map.entry("unit2|fixed", "u2Fixed"),
            Map.entry("real|steam-1", "realSteam1"), Map.entry("real|unit-1", "realUnit1"),
            Map.entry("real|steam-2", "realSteam2"), Map.entry("real|unit-2", "realUnit2"));

    /** 사업계획대비 12개월 표: "구분|종류" → 필드 (키는 구분|종류-m월) */
    static final Map<String, String> PLAN_GRID_KEYS = Map.of(
            "labor|plan", "laborPlan", "labor|actual", "laborActual",
            "operation|plan", "operationPlan", "operation|actual", "operationActual",
            "total|plan", "totalPlan");

    /** 사업계획대비 위탁운영비 (고른 달 한 행): 키 → 필드 */
    static final Map<String, String> PLAN_BREAK_KEYS = Map.of(
            "break|m-plan", "brkMonthPlan", "break|m-actual", "brkMonthActual",
            "break|y-plan", "brkYtdPlan", "break|y-actual", "brkYtdActual",
            "break|prev-actual", "brkPrevActual", "break|note", "brkNote");

    /** 가동실적: "구분|항목" → 필드 (키는 구분|월|항목) */
    static final Map<String, String> OPERATION_KEYS = Map.of(
            "hours|u1-plan", "u1Plan", "hours|u1-actual", "u1Actual",
            "hours|u2-plan", "u2Plan", "hours|u2-actual", "u2Actual",
            "metric|steam-plan", "steamPlan", "metric|steam-actual", "steamActual",
            "metric|rate-plan", "ratePlan", "metric|rate-actual", "rateActual",
            "metric|cost-plan", "costPlan", "metric|cost-actual", "costActual");

    private static final String LABOR_KEY = "summary|0|labor";
    private static final Pattern THREE_PART = Pattern.compile("^([^|]+)\\|(\\d+)\\|([^|]+)$");
    private static final Pattern GRID = Pattern.compile("^([a-z]+\\|[a-z]+)-m(\\d{1,2})$");
    private static final Pattern NOTE = Pattern.compile("^notes\\|m(\\d{1,2})-u([12])$");

    private final SteamIncineratorContractDailyAdapter contract;
    private final SteamIncineratorPlanMonthlyAdapter plan;
    private final SteamIncineratorOperationMonthlyAdapter operation;
    private final SteamIncineratorCostMonthlyAdapter cost;

    /**
     * 옛 형식의 셀 목록. monthKey 가 {@code YYYY-MM} 이면 도급내역과 사업계획대비, {@code YYYY} 면 가동실적과 총 운영비용.
     * 각 항목: table_name, row_key, col_index(=1), cell_value.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByMonthKey(String monthKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (monthKey.matches("\\d{4}-\\d{2}")) {
            int year = Integer.parseInt(monthKey.substring(0, 4));
            int month = Integer.parseInt(monthKey.substring(5, 7));
            if (month < 1 || month > 12) return out;
            for (Map<String, Object> row : contract.load(year, month)) {
                int day = ((LocalDate) row.get("usageDate")).getDayOfMonth();
                CONTRACT_KEYS.forEach((key, field) -> {
                    String[] parts = key.split("\\|");
                    add(out, CONTRACT, parts[0] + "|" + day + "|" + parts[1], row.get(field));
                });
            }
            for (Map<String, Object> row : plan.load(year, null)) {
                int n = (Integer) row.get("monthNo");
                PLAN_GRID_KEYS.forEach((key, field) -> add(out, PLAN_VS, key + "-m" + n, row.get(field)));
                add(out, PLAN_VS, "notes|m" + n + "-u1", row.get("noteU1"));
                add(out, PLAN_VS, "notes|m" + n + "-u2", row.get("noteU2"));
                if (n == month) {
                    PLAN_BREAK_KEYS.forEach((key, field) -> add(out, PLAN_VS, key, row.get(field)));
                    add(out, CONTRACT, LABOR_KEY, row.get("contractLabor"));
                }
            }
        } else if (monthKey.matches("\\d{4}")) {
            int year = Integer.parseInt(monthKey);
            for (Map<String, Object> row : operation.load(year, null)) {
                int n = (Integer) row.get("monthNo");
                OPERATION_KEYS.forEach((key, field) -> {
                    String[] parts = key.split("\\|");
                    add(out, OPERATION, parts[0] + "|" + n + "|" + parts[1], row.get(field));
                });
            }
            for (Map<String, Object> row : cost.load(year, null)) {
                int n = (Integer) row.get("monthNo");
                for (String field : cost.valueFields()) {
                    String key = costKey(field);
                    if (key == null) continue;
                    String[] parts = key.split("\\|");
                    add(out, TOTAL_COST, parts[0] + "|" + n + "|" + parts[1], row.get(field));
                }
            }
        }
        return out;
    }

    /**
     * 엑셀에서 읽은 셀을 반영한다. cells = 옛 논리 표 이름 → (키 → 값). 맵에 없는 표는 건드리지 않는다.
     *
     * <ul>
     *   <li>도급내역: 고른 달만 파일 기준으로 바꾼다 (다른 달은 그대로).</li>
     *   <li>사업계획대비: 그 해 12개월 표와 비고, 고른 달의 위탁운영비 칸을 파일 기준으로 바꾼다. 다른 달의 위탁운영비 칸·인건비는 그대로.</li>
     *   <li>가동실적 · 총 운영비용: 그 해를 파일 기준으로 바꾼다.</li>
     * </ul>
     *
     * @return 저장한 칸 수
     */
    @Transactional
    public int replaceFromImport(int year, int month, Map<String, Map<String, String>> cells) {
        int saved = 0;
        Map<String, String> contractCells = cells.get(CONTRACT);
        if (contractCells != null) {
            int lastDay = YearMonth.of(year, month).lengthOfMonth();
            Map<Integer, Map<String, Object>> days = new TreeMap<>();
            for (Map.Entry<String, String> cell : contractCells.entrySet()) {
                Matcher matcher = THREE_PART.matcher(cell.getKey());
                if (!matcher.matches()) continue;
                String field = CONTRACT_KEYS.get(matcher.group(1) + "|" + matcher.group(3));
                int day = Integer.parseInt(matcher.group(2));
                if (field == null || day < 1 || day > lastDay) continue;
                Object value = CellValues.convert(cell.getValue(), contract.typeOf(field));
                if (value == null) continue;
                days.computeIfAbsent(day, key -> {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("usageDate", LocalDate.of(year, month, key));
                    return created;
                }).put(field, value);
                saved += 1;
            }
            contract.saveBulk(year, month, new ArrayList<>(days.values()), true);
        }

        Map<String, String> planCells = cells.get(PLAN_VS);
        if (planCells != null || contractCells != null) {
            Map<Integer, Map<String, Object>> rows = new TreeMap<>();
            for (Map<String, Object> row : plan.load(year, null)) rows.put((Integer) row.get("monthNo"), new LinkedHashMap<>(row));
            if (planCells != null) {
                // 파일이 기준인 칸을 먼저 비운다
                for (Map<String, Object> row : rows.values()) {
                    PLAN_GRID_KEYS.values().forEach(field -> row.put(field, null));
                    row.put("noteU1", null);
                    row.put("noteU2", null);
                    if (row.get("monthNo").equals(month)) PLAN_BREAK_KEYS.values().forEach(field -> row.put(field, null));
                }
                for (Map.Entry<String, String> cell : planCells.entrySet()) {
                    String field;
                    int n;
                    Matcher grid = GRID.matcher(cell.getKey());
                    Matcher note = NOTE.matcher(cell.getKey());
                    if (grid.matches()) {
                        field = PLAN_GRID_KEYS.get(grid.group(1));
                        n = Integer.parseInt(grid.group(2));
                    } else if (note.matches()) {
                        field = "1".equals(note.group(2)) ? "noteU1" : "noteU2";
                        n = Integer.parseInt(note.group(1));
                    } else {
                        field = PLAN_BREAK_KEYS.get(cell.getKey());
                        n = month;
                    }
                    if (field == null || n < 1 || n > 12) continue;
                    Object value = CellValues.convert(cell.getValue(), plan.typeOf(field));
                    if (value == null) continue;
                    planRow(rows, year, n).put(field, value);
                    saved += 1;
                }
            }
            if (contractCells != null) {
                Object labor = CellValues.convert(contractCells.get(LABOR_KEY), plan.typeOf("contractLabor"));
                if (labor != null || rows.containsKey(month)) planRow(rows, year, month).put("contractLabor", labor);
                if (labor != null) saved += 1;
            }
            plan.saveBulk(year, null, new ArrayList<>(rows.values()), false);
        }

        saved += replaceYear(operation, year, cells.get(OPERATION), OPERATION_KEYS::get);
        saved += replaceYear(cost, year, cells.get(TOTAL_COST), key -> {
            String field = costField(key);
            return cost.valueFields().contains(field) ? field : null;
        });
        return saved;
    }

    private static Map<String, Object> planRow(Map<Integer, Map<String, Object>> rows, int year, int month) {
        return rows.computeIfAbsent(month, key -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("yearNo", year);
            created.put("monthNo", key);
            return created;
        });
    }

    /** 연 키 표(구분|월|항목)를 그 해 통째로 바꾼다. */
    private static int replaceYear(SteamTableAdapter adapter, int year, Map<String, String> cells,
                                   java.util.function.Function<String, String> fieldOf) {
        if (cells == null) return 0;
        Map<Integer, Map<String, Object>> rows = new TreeMap<>();
        int saved = 0;
        for (Map.Entry<String, String> cell : cells.entrySet()) {
            Matcher matcher = THREE_PART.matcher(cell.getKey());
            if (!matcher.matches()) continue;
            String field = fieldOf.apply(matcher.group(1) + "|" + matcher.group(3));
            int n = Integer.parseInt(matcher.group(2));
            if (field == null || n < 1 || n > 12) continue;
            Object value = CellValues.convert(cell.getValue(), adapter.typeOf(field));
            if (value == null) continue;
            rows.computeIfAbsent(n, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("yearNo", year);
                created.put("monthNo", key);
                return created;
            }).put(field, value);
            saved += 1;
        }
        adapter.saveBulk(year, null, new ArrayList<>(rows.values()), true);
        return saved;
    }

    private static void add(List<Map<String, Object>> out, String table, String rowKey, Object value) {
        String text = CellValues.text(value);
        if (text == null) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("table_name", table);
        row.put("row_key", rowKey);
        row.put("col_index", 1);
        row.put("cell_value", text);
        out.add(row);
    }

    /** "op-burn|burn-1" → "opBurnBurn1" */
    static String costField(String key) {
        String[] words = key.replace('|', '-').split("-");
        StringBuilder field = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i += 1) {
            if (words[i].isEmpty()) continue;
            field.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return field.toString();
    }

    /** 총 운영비용 입력 항목 "구분|항목" 39개 (화면의 입력 칸과 같다) */
    static final List<String> COST_ITEMS = List.of(
            "op-burn|burn-1", "op-burn|burn-2", "op-burn|rate-1", "op-burn|rate-2",
            "op-cost|unit", "op-cost|deduct", "self-srf|in-qty", "self-srf|in-rev",
            "self-srf|floor-recycle", "self-srf|floor-bury", "self-srf|fly-solid", "self-srf|fly-bury",
            "self-srf|cost-floor", "self-srf|cost-fly", "self-srf|cost-misc", "self-util|p1",
            "self-util|p2", "self-util|prate", "self-util|naoh-qty", "self-util|naoh-unit",
            "self-util|water", "self-util|waste-charge", "self-util|etc", "self-total|total",
            "self-total|burn-rate", "self-total|steam-rate", "mat-oil|u1", "mat-oil|u2",
            "mat-oil|unit", "mat-oil|ck-qty", "mat-oil|ck-unit", "mat-chem|u-qty",
            "mat-chem|u-unit", "mat-chem|s-qty", "mat-chem|s-unit", "mat-misc|a-qty",
            "mat-misc|a-unit", "mat-misc|k-qty", "mat-misc|k-unit");

    private static final Map<String, String> COST_KEY_BY_FIELD = new LinkedHashMap<>();

    static {
        for (String key : COST_ITEMS) COST_KEY_BY_FIELD.put(costField(key), key);
    }

    /** 총 운영비용 필드 → "구분|항목" */
    static String costKey(String field) {
        return COST_KEY_BY_FIELD.get(field);
    }
}
