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
import java.util.Set;
import java.util.TreeMap;

/**
 * 유동상 운영 셀 저장소 — 엑셀 서비스가 쓰는 옛 키를 규격 표 4개의 행·컬럼으로 옮긴다.
 *
 * <ul>
 *   <li>{@code fluidized_plan_actual} (YYYY-MM): {@code operating|power|waste|srf_income} col 0 → steam_fluidized_month 의 계획 4칸</li>
 *   <li>{@code fluidized_plan_summary} (YYYY-MM): {@code monthly-summary} col 0 → SUMMARY_REMARK</li>
 *   <li>{@code fluidized_year_note} (YYYY-01): {@code note-월} col 0 → 그 월의 MONTH_NOTE</li>
 *   <li>{@code fluidized_contract_cost} (YYYY-MM): {@code cost} col 0·1·2 → 인건비·운영비·설비개선비용</li>
 *   <li>{@code fluidized_contract_daily} (YYYY-MM): {@code 일} col 0·2·4·5 → steam_fluidized_contract_daily</li>
 *   <li>{@code fluidized_srf_inbound} (YYYY-MM): {@code entry:일} col 1~10 → steam_fluidized_srf_inbound_daily,
 *       {@code rate} col 1~10 → steam_fluidized_srf_rate</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FluidizedCellStore {

    public static final String PLAN = "fluidized_plan_actual";
    public static final String SUMMARY = "fluidized_plan_summary";
    public static final String NOTE = "fluidized_year_note";
    public static final String CONTRACT = "fluidized_contract_daily";
    public static final String COST = "fluidized_contract_cost";
    public static final String SRF_INBOUND = "fluidized_srf_inbound";

    /** 이 저장소가 맡는 옛 논리 표 — 옛 셀 저장소에 남은 같은 이름의 행은 더 이상 읽지 않는다 */
    public static final Set<String> TABLES = Set.of(PLAN, SUMMARY, NOTE, CONTRACT, COST, SRF_INBOUND);

    static final Map<String, String> PLAN_FIELDS = Map.of(
            "operating", "planOperating", "power", "planPower", "waste", "planWaste", "srf_income", "planSrfIncome");
    static final Map<Integer, String> COST_FIELDS = Map.of(0, "laborCost", 1, "operatingPayCost", 2, "improvementPayCost");
    static final Map<Integer, String> CONTRACT_FIELDS = Map.of(0, "steamTon", 2, "steamUnitPrice", 4, "stopHours", 5, "fixedCostPerHour");
    static final int VENDOR_SLOTS = 10;

    /** 옛 셀 하나 */
    public record Cell(String table, String month, String rowKey, int col, String value) {}

    private final SteamFluidizedMonthAdapter monthTable;
    private final SteamFluidizedContractDailyAdapter contract;
    private final SteamFluidizedSrfInboundDailyAdapter srfInbound;
    private final SteamFluidizedSrfRateAdapter srfRate;

    static String kgField(int slot) { return String.format("vendor%02dKg", slot); }

    static String rateField(int slot) { return String.format("vendor%02dRate", slot); }

    /** 월 키(YYYY-MM)의 옛 형식 셀 목록: table_name, row_key, col_index, cell_value, month. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByMonthKey(String monthKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (monthKey == null || !monthKey.matches("\\d{4}-\\d{2}")) return out;
        int year = Integer.parseInt(monthKey.substring(0, 4));
        int month = Integer.parseInt(monthKey.substring(5, 7));
        if (month < 1 || month > 12) return out;
        for (Map<String, Object> row : monthTable.load(year, null)) {
            int n = (Integer) row.get("monthNo");
            // 월별 특이사항은 그 해 1월 키에 모아 준다
            if (month == 1) add(out, NOTE, monthKey, String.format("note-%02d", n), 0, row.get("monthNote"));
            if (n != month) continue;
            PLAN_FIELDS.forEach((key, field) -> add(out, PLAN, monthKey, key, 0, row.get(field)));
            add(out, SUMMARY, monthKey, "monthly-summary", 0, row.get("summaryRemark"));
            COST_FIELDS.forEach((col, field) -> add(out, COST, monthKey, "cost", col, row.get(field)));
        }
        for (Map<String, Object> row : contract.load(year, month)) {
            String day = String.format("%02d", ((LocalDate) row.get("usageDate")).getDayOfMonth());
            CONTRACT_FIELDS.forEach((col, field) -> add(out, CONTRACT, monthKey, day, col, row.get(field)));
        }
        out.addAll(listSrfInbound(year, month));
        return out;
    }

    /**
     * 옛 셀 저장소에서 읽은 그 달의 행(세부 운영내역 등 아직 전환되지 않은 표)에 규격 표의 셀을 합친다.
     * 옛 저장소에 남아 있는 이 저장소 담당 표의 행은 버린다 (규격 표가 기준).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mergeWithLegacy(List<Map<String, Object>> legacyRows, String monthKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : legacyRows) {
            if (!TABLES.contains(String.valueOf(row.get("table_name")))) out.add(row);
        }
        out.addAll(listByMonthKey(monthKey));
        return out;
    }

    /** SRF 입고내역의 옛 형식 셀 — 한 달 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSrfInbound(int year, int month) {
        List<Map<String, Object>> out = new ArrayList<>();
        String monthKey = String.format("%d-%02d", year, month);
        for (Map<String, Object> row : srfInbound.load(year, month)) {
            String rowKey = String.format("entry:%02d", ((LocalDate) row.get("usageDate")).getDayOfMonth());
            for (int slot = 1; slot <= VENDOR_SLOTS; slot += 1) add(out, SRF_INBOUND, monthKey, rowKey, slot, row.get(kgField(slot)));
        }
        for (Map<String, Object> row : srfRate.load(year, month)) {
            for (int slot = 1; slot <= VENDOR_SLOTS; slot += 1) add(out, SRF_INBOUND, monthKey, "rate", slot, row.get(rateField(slot)));
        }
        return out;
    }

    /** SRF 입고내역의 옛 형식 셀 — fromMonth ~ toMonth (YYYY-MM) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSrfInboundBetween(String fromMonth, String toMonth) {
        List<Map<String, Object>> out = new ArrayList<>();
        YearMonth from = YearMonth.parse(fromMonth), to = YearMonth.parse(toMonth);
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) out.addAll(listSrfInbound(ym.getYear(), ym.getMonthValue()));
        return out;
    }

    /**
     * 엑셀에서 읽은 셀을 반영한다.
     *
     * @param replace true = 전체 워크북 가져오기 — 그 해의 사업계획·월별 특이사항, 고른 달의 요약 비고·지급현황·일별 도급비용·SRF 입고를
     *                파일 기준으로 바꾼다 (파일에 없는 칸은 비운다). false = 탭별 가져오기 — 읽은 칸만 고친다.
     * @return 저장한 칸 수
     */
    @Transactional
    public int apply(int year, int month, boolean replace, List<Cell> cells) {
        int saved = 0;
        // ── 월 행 (steam_fluidized_month) ──
        Map<Integer, Map<String, Object>> months = new TreeMap<>();
        for (Map<String, Object> row : monthTable.load(year, null)) months.put((Integer) row.get("monthNo"), new LinkedHashMap<>(row));
        if (replace) {
            for (int n = 1; n <= 12; n += 1) {
                Map<String, Object> row = months.get(n);
                if (row == null) continue;
                PLAN_FIELDS.values().forEach(field -> row.put(field, null));
                row.put("monthNote", null);
                if (n == month) {
                    row.put("summaryRemark", null);
                    COST_FIELDS.values().forEach(field -> row.put(field, null));
                }
            }
        }
        // ── 일 행 ──
        Map<Integer, Map<String, Object>> contractDays = dayRows(contract, year, month, replace);
        Map<Integer, Map<String, Object>> srfDays = dayRows(srfInbound, year, month, replace);
        Map<String, Object> rateRow = new LinkedHashMap<>();
        if (!replace) for (Map<String, Object> row : srfRate.load(year, month)) rateRow.putAll(row);
        rateRow.put("yearNo", year);
        rateRow.put("monthNo", month);
        boolean touchedContract = replace, touchedSrf = replace;
        int lastDay = YearMonth.of(year, month).lengthOfMonth();

        for (Cell cell : cells) {
            String field;
            Map<String, Object> target;
            SteamTableAdapter adapter;
            switch (cell.table()) {
                case PLAN -> {
                    field = cell.col() == 0 ? PLAN_FIELDS.get(cell.rowKey()) : null;
                    target = monthRow(months, year, monthOf(cell.month(), year));
                    adapter = monthTable;
                }
                case SUMMARY -> {
                    field = cell.col() == 0 && "monthly-summary".equals(cell.rowKey()) ? "summaryRemark" : null;
                    target = monthRow(months, year, monthOf(cell.month(), year));
                    adapter = monthTable;
                }
                case NOTE -> {
                    Integer n = cell.rowKey().matches("note-\\d{2}") ? Integer.parseInt(cell.rowKey().substring(5)) : null;
                    field = cell.col() == 0 && n != null ? "monthNote" : null;
                    target = n == null ? null : monthRow(months, year, n);
                    adapter = monthTable;
                }
                case COST -> {
                    field = "cost".equals(cell.rowKey()) ? COST_FIELDS.get(cell.col()) : null;
                    target = monthRow(months, year, monthOf(cell.month(), year));
                    adapter = monthTable;
                }
                case CONTRACT -> {
                    Integer day = cell.rowKey().matches("\\d{1,2}") ? Integer.parseInt(cell.rowKey()) : null;
                    field = CONTRACT_FIELDS.get(cell.col());
                    target = day == null || day < 1 || day > lastDay ? null : dayRow(contractDays, year, month, day);
                    adapter = contract;
                    touchedContract = true;
                }
                case SRF_INBOUND -> {
                    boolean slotOk = cell.col() >= 1 && cell.col() <= VENDOR_SLOTS;
                    touchedSrf = true;
                    if ("rate".equals(cell.rowKey())) {
                        field = slotOk ? rateField(cell.col()) : null;
                        target = rateRow;
                        adapter = srfRate;
                    } else {
                        Integer day = cell.rowKey().matches("entry:\\d{1,2}") ? Integer.parseInt(cell.rowKey().substring(6)) : null;
                        field = slotOk ? kgField(cell.col()) : null;
                        target = day == null || day < 1 || day > lastDay ? null : dayRow(srfDays, year, month, day);
                        adapter = srfInbound;
                    }
                }
                default -> {
                    continue;
                }
            }
            if (field == null || target == null) continue;
            Object value = CellValues.convert(cell.value(), adapter.typeOf(field));
            if (value == null) continue;
            target.put(field, value);
            saved += 1;
        }

        months.values().removeIf(row -> row.get("monthNo") == null);
        monthTable.saveBulk(year, null, new ArrayList<>(months.values()), false);
        if (touchedContract) contract.saveBulk(year, month, new ArrayList<>(contractDays.values()), replace);
        if (touchedSrf) {
            srfInbound.saveBulk(year, month, new ArrayList<>(srfDays.values()), replace);
            srfRate.saveBulk(year, month, List.of(rateRow), false);
        }
        return saved;
    }

    /** 옛 셀의 월 키(YYYY-MM)에서 월을 꺼낸다. 다른 해면 0 (그 행은 버려진다). */
    private static int monthOf(String monthKey, int year) {
        if (monthKey == null || !monthKey.matches("\\d{4}-\\d{2}") || Integer.parseInt(monthKey.substring(0, 4)) != year) return 0;
        return Integer.parseInt(monthKey.substring(5, 7));
    }

    private static Map<String, Object> monthRow(Map<Integer, Map<String, Object>> months, int year, int month) {
        if (month < 1 || month > 12) return null;
        return months.computeIfAbsent(month, key -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("yearNo", year);
            created.put("monthNo", key);
            return created;
        });
    }

    /** replace 면 빈 맵에서 시작 (파일이 그 달의 기준), 아니면 기존 행을 읽어 온다. */
    private static Map<Integer, Map<String, Object>> dayRows(SteamTableAdapter adapter, int year, int month, boolean replace) {
        Map<Integer, Map<String, Object>> days = new TreeMap<>();
        if (!replace) {
            for (Map<String, Object> row : adapter.load(year, month)) {
                days.put(((LocalDate) row.get("usageDate")).getDayOfMonth(), new LinkedHashMap<>(row));
            }
        }
        return days;
    }

    private static Map<String, Object> dayRow(Map<Integer, Map<String, Object>> days, int year, int month, int day) {
        return days.computeIfAbsent(day, key -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("usageDate", LocalDate.of(year, month, key));
            return created;
        });
    }

    private static void add(List<Map<String, Object>> out, String table, String monthKey, String rowKey, int col, Object value) {
        String text = CellValues.text(value);
        if (text == null) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("month", monthKey);
        row.put("table_name", table);
        row.put("row_key", rowKey);
        row.put("col_index", col);
        row.put("cell_value", text);
        out.add(row);
    }
}
