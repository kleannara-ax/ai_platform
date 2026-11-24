package com.company.module.steamenergy.service.table;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 복합보일러 셀 저장소 — 엑셀 서비스가 쓰는 옛 키({@code 항목-일}, {@code fuelPrice-설비}, {@code 항목-줄번호},
 * {@code combo-plan-행-칸})를 규격 표 5개의 행·컬럼으로 옮긴다.
 *
 * <ul>
 *   <li>{@code combo_boiler_production}: {@code <field>-<day>} → steam_combo_daily_production, {@code fuelPrice-<설비>} → steam_combo_monthly_price</li>
 *   <li>{@code combo_boiler_downtime}: {@code <field>-<day>} → steam_combo_daily_downtime, {@code ventLossUnitPrice} → steam_combo_monthly_price</li>
 *   <li>{@code combo_boiler_recovery}: {@code <field>-<줄번호>} → steam_combo_recovery_loss</li>
 *   <li>{@code combo_boiler_etc}: 계획 표 칸 → steam_combo_plan (기준연도, 대상 월)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ComboCellStore {

    public static final String PRODUCTION = "combo_boiler_production";
    public static final String DOWNTIME = "combo_boiler_downtime";
    public static final String RECOVERY = "combo_boiler_recovery";

    private static final Pattern INDEXED = Pattern.compile("^(.+)-(\\d+)$");
    private static final String FUEL_PRICE = "fuelPrice-";
    private static final String VENT_LOSS = "ventLossUnitPrice";

    /** 계획 표: 옛 키 접두 → (필드, 첫 칸의 월이 기준연도 1월에서 몇 달 떨어졌는지, 첫 칸 번호) */
    record PlanRow(String field, int firstOffset, int firstIndex) {}

    static final Map<String, PlanRow> PLAN_ROWS = Map.of(
            "combo-plan-1-", new PlanRow("comboBasic", -7, 0),      // 전년 6월 ~ 당해 8월
            "combo-plan-2-", new PlanRow("comboSpecial", -7, 0),
            "combo-plan-4-", new PlanRow("comboActual", -7, 0),
            "jesco-plan-0-", new PlanRow("jescoPlan", -8, 0),       // 전년 5월 ~ 당해 5월
            "jesco-plan-1-", new PlanRow("jescoActual", -8, 0),
            "lngBoiler-", new PlanRow("lngBoiler", 0, 1),           // 당해 1 ~ 12월
            "lngCombo-", new PlanRow("lngCombo", 0, 1)
    );

    private final SteamComboDailyProductionAdapter production;
    private final SteamComboDailyDowntimeAdapter downtime;
    private final SteamComboMonthlyPriceAdapter monthly;
    private final SteamComboRecoveryLossAdapter recovery;
    private final SteamComboPlanAdapter plan;

    /** 그 달의 셀 — 옛 논리 표 이름 → (키 → 값) */
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> loadMonth(int year, int month) {
        Map<String, String> productionCells = dayCells(production, year, month);
        Map<String, String> downtimeCells = dayCells(downtime, year, month);
        for (Map<String, Object> row : monthly.load(year, month)) {
            for (String field : monthly.valueFields()) {
                String text = CellValues.text(row.get(field));
                if (text == null) continue;
                if (VENT_LOSS.equals(field)) downtimeCells.put(VENT_LOSS, text);
                else productionCells.put(FUEL_PRICE + lowerFirst(field.substring("fuelPrice".length())), text);
            }
        }
        Map<String, String> recoveryCells = new LinkedHashMap<>();
        for (Map<String, Object> row : recovery.load(year, month)) {
            for (String field : recovery.valueFields()) {
                String text = CellValues.text(row.get(field));
                if (text != null) recoveryCells.put(field + "-" + row.get("lineNo"), text);
            }
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        result.put(PRODUCTION, productionCells);
        result.put(DOWNTIME, downtimeCells);
        result.put(RECOVERY, recoveryCells);
        return result;
    }

    /** 그 달을 주어진 셀로 바꾼다 (파일이 그 달의 기준 — 없는 날·줄은 비우거나 지운다). 저장한 칸 수를 돌려준다. */
    @Transactional
    public int replaceMonth(int year, int month, Map<String, Map<String, String>> cells) {
        Map<String, String> productionCells = cells.getOrDefault(PRODUCTION, Map.of());
        Map<String, String> downtimeCells = cells.getOrDefault(DOWNTIME, Map.of());
        int saved = saveDayCells(production, year, month, productionCells);
        saved += saveDayCells(downtime, year, month, downtimeCells);

        Map<String, Object> monthRow = new LinkedHashMap<>();
        monthRow.put("yearNo", year);
        monthRow.put("monthNo", month);
        int monthValues = 0;
        for (Map.Entry<String, String> cell : productionCells.entrySet()) {
            if (!cell.getKey().startsWith(FUEL_PRICE)) continue;
            String field = "fuelPrice" + upperFirst(cell.getKey().substring(FUEL_PRICE.length()));
            if (!monthly.valueFields().contains(field)) continue;
            Object value = CellValues.convert(cell.getValue(), monthly.typeOf(field));
            if (value == null) continue;
            monthRow.put(field, value);
            monthValues += 1;
        }
        Object ventLoss = CellValues.convert(downtimeCells.get(VENT_LOSS), monthly.typeOf(VENT_LOSS));
        if (ventLoss != null) {
            monthRow.put(VENT_LOSS, ventLoss);
            monthValues += 1;
        }
        monthly.saveBulk(year, month, monthValues == 0 ? List.of() : List.of(monthRow), true);
        saved += monthValues;

        Map<Integer, Map<String, Object>> lines = new TreeMap<>();
        for (Map.Entry<String, String> cell : cells.getOrDefault(RECOVERY, Map.of()).entrySet()) {
            Matcher matcher = INDEXED.matcher(cell.getKey());
            if (!matcher.matches() || !recovery.valueFields().contains(matcher.group(1))) continue;
            Object value = CellValues.convert(cell.getValue(), recovery.typeOf(matcher.group(1)));
            if (value == null) continue;
            int lineNo = Integer.parseInt(matcher.group(2));
            Map<String, Object> row = lines.computeIfAbsent(lineNo, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("yearNo", year);
                created.put("monthNo", month);
                created.put("lineNo", key);
                return created;
            });
            row.put(matcher.group(1), value);
            saved += 1;
        }
        recovery.saveBulk(year, month, new ArrayList<>(lines.values()), true);
        return saved;
    }

    /** 기준연도의 계획 표 셀 (옛 키: combo-plan-행-칸, jesco-plan-행-칸, lngBoiler-월, lngCombo-월) */
    @Transactional(readOnly = true)
    public Map<String, String> loadEtc(int year) {
        Map<String, String> cells = new LinkedHashMap<>();
        YearMonth january = YearMonth.of(year, 1);
        for (Map<String, Object> row : plan.load(year, null)) {
            YearMonth target;
            try {
                target = YearMonth.parse(String.valueOf(row.get("targetYm")));
            } catch (RuntimeException e) {
                continue;
            }
            int offset = (int) ChronoUnit.MONTHS.between(january, target);
            for (Map.Entry<String, PlanRow> entry : PLAN_ROWS.entrySet()) {
                String text = CellValues.text(row.get(entry.getValue().field()));
                if (text == null) continue;
                cells.put(entry.getKey() + planIndex(entry.getValue(), offset), text);
            }
        }
        return cells;
    }

    /** 기준연도의 계획 표를 주어진 셀로 바꾼다. 저장한 칸 수를 돌려준다. */
    @Transactional
    public int replaceEtc(int year, Map<String, String> cells) {
        Map<String, Map<String, Object>> rows = new TreeMap<>();
        YearMonth january = YearMonth.of(year, 1);
        int saved = 0;
        for (Map.Entry<String, String> cell : cells.entrySet()) {
            for (Map.Entry<String, PlanRow> entry : PLAN_ROWS.entrySet()) {
                if (!cell.getKey().startsWith(entry.getKey())) continue;
                String indexText = cell.getKey().substring(entry.getKey().length());
                if (!indexText.matches("\\d+")) continue;
                PlanRow planRow = entry.getValue();
                Object value = CellValues.convert(cell.getValue(), plan.typeOf(planRow.field()));
                if (value == null) continue;
                String targetYm = january.plusMonths(planOffset(planRow, Integer.parseInt(indexText))).toString();
                Map<String, Object> row = rows.computeIfAbsent(targetYm, key -> {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("yearNo", year);
                    created.put("targetYm", key);
                    return created;
                });
                row.put(planRow.field(), value);
                saved += 1;
            }
        }
        plan.saveBulk(year, null, new ArrayList<>(rows.values()), true);
        return saved;
    }

    /** 칸 번호 → 기준연도 1월에서 떨어진 달 수 */
    static int planOffset(PlanRow row, int index) {
        return index - row.firstIndex() + row.firstOffset();
    }

    /** 기준연도 1월에서 떨어진 달 수 → 칸 번호 */
    static int planIndex(PlanRow row, int offset) {
        return offset - row.firstOffset() + row.firstIndex();
    }

    private static Map<String, String> dayCells(SteamTableAdapter adapter, int year, int month) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (Map<String, Object> row : adapter.load(year, month)) {
            int day = ((LocalDate) row.get("usageDate")).getDayOfMonth();
            for (String field : adapter.valueFields()) {
                String text = CellValues.text(row.get(field));
                if (text != null) cells.put(field + "-" + day, text);
            }
        }
        return cells;
    }

    private static int saveDayCells(SteamTableAdapter adapter, int year, int month, Map<String, String> cells) {
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        Map<Integer, Map<String, Object>> days = new TreeMap<>();
        int saved = 0;
        for (Map.Entry<String, String> cell : cells.entrySet()) {
            Matcher matcher = INDEXED.matcher(cell.getKey());
            if (!matcher.matches() || !adapter.valueFields().contains(matcher.group(1))) continue;
            int day = Integer.parseInt(matcher.group(2));
            if (day < 1 || day > lastDay) continue;
            Object value = CellValues.convert(cell.getValue(), adapter.typeOf(matcher.group(1)));
            if (value == null) continue;
            Map<String, Object> row = days.computeIfAbsent(day, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("usageDate", LocalDate.of(year, month, key));
                return created;
            });
            row.put(matcher.group(1), value);
            saved += 1;
        }
        adapter.saveBulk(year, month, new ArrayList<>(days.values()), true);
        return saved;
    }

    private static String upperFirst(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
