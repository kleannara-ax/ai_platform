package com.company.module.steamenergy.service.energy;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 에너지 회계비용 엑셀 서비스용 셀 브리지 — 옛 셀 좌표 (table_name, row_key, col_index) 를 규격 표의 (alias, field, 연, 월) 로 바꿔 읽고 쓴다.
 *
 * <p>엑셀 서비스(라벨 → 행, 머리글 → 열)는 그대로 두고, 저장소만 {@code steam_cell_value} 에서 실제 컬럼 표로 바꾸기 위한 것.
 * 매핑은 {@link EnergyCellMapData} (생성 코드). 매핑에 없는 셀(화면 계산 전용 행, 단위 글자 등)은 건너뛴다.
 *
 * <p>열 규약(colRule):
 * <ul>
 *   <li>eap / efr : 1 = 전년 12월 → (연-1, 12), 2..13 = 1..12월</li>
 *   <li>efp / efs : 1..12 = 1..12월</li>
 *   <li>emc       : 0 = 전년 12월 → (연-1, 12), 1..12, 13 = 다음해 1월 → (연+1, 1)</li>
 *   <li>emcNote   : 연도 비고 (월 없음)</li>
 * </ul>
 */
@Service
public class EnergyCellBridge {

    /** 옛 셀 한 칸 */
    public record Cell(String rowKey, int colIndex, String value) {}

    /** import 결과 */
    public record UpsertResult(int saved, int skipped) {}

    record Target(int yearOffset, Integer month) {}   // 패키지 내 테스트용

    private final Map<String, EnergyTableAdapter> adapters = new HashMap<>();
    /** legacyTable → legacyKey → entry */
    private final Map<String, Map<String, EnergyCellMapData.Entry>> index = new HashMap<>();

    public EnergyCellBridge(List<EnergyTableAdapter> adapterList) {
        adapterList.forEach(a -> adapters.put(a.alias(), a));
        for (EnergyCellMapData.Entry e : EnergyCellMapData.ENTRIES) {
            index.computeIfAbsent(e.legacyTable(), k -> new LinkedHashMap<>()).put(e.legacyKey(), e);
        }
    }

    /** 이 옛 논리 표가 매핑돼 있는지 */
    public boolean supports(String legacyTable) {
        return index.containsKey(legacyTable);
    }

    /**
     * export 용 — 그 해의 값을 옛 셀 모양 {@code {table_name, row_key, col_index, cell_value}} 목록으로.
     * 전년 12월·다음해 1월 열은 이웃 연도 행에서 가져온다.
     */
    public List<Map<String, Object>> cellsOf(String legacyTable, int year) {
        Map<String, EnergyCellMapData.Entry> entries = index.getOrDefault(legacyTable, Map.of());
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Map<String, EnergyRow>> rowsByAlias = new HashMap<>();   // alias → "year|month" → row
        for (EnergyCellMapData.Entry e : entries.values()) {
            Map<String, EnergyRow> rows = rowsByAlias.computeIfAbsent(e.alias(), alias -> loadAround(alias, year));
            for (EnergyRow row : rows.values()) {
                Integer col = colIndexOf(e.colRule(), year, row.yearNo(), row.monthNo());
                if (col == null) continue;
                Object value = row.values().get(e.field());
                if (value == null) continue;
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("table_name", legacyTable);
                cell.put("row_key", e.legacyKey());
                cell.put("col_index", col);
                cell.put("cell_value", value instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(value));
                out.add(cell);
            }
        }
        return out;
    }

    /**
     * import 전 — 그 해의 월 행 값을 비운다 (엑셀 파일이 그 해의 기준). 이웃 연도 행(전년 12월 등)과 연도 비고는 건드리지 않는다.
     */
    @Transactional
    public void clearYear(String legacyTable, int year) {
        for (String alias : aliasesOf(legacyTable)) {
            EnergyTableAdapter adapter = adapter(alias);
            if (!adapter.monthly()) continue;
            for (EnergyRow row : adapter.load(year)) {
                Map<String, Object> values = new LinkedHashMap<>();
                adapter.fields().forEach(f -> values.put(f, null));
                adapter.save(row.yearNo(), row.monthNo(), values);
            }
        }
    }

    /**
     * import — 셀 묶음을 (표, 연, 월) 행 단위로 모아 저장한다. 매핑에 없는 셀·숫자가 아닌 값은 건너뛴다.
     */
    @Transactional
    public UpsertResult upsertCells(String legacyTable, int year, List<Cell> cells) {
        Map<String, EnergyCellMapData.Entry> entries = index.getOrDefault(legacyTable, Map.of());
        // (alias|year|month) → 필드 값
        Map<String, Map<String, Object>> pending = new LinkedHashMap<>();
        Map<String, int[]> keys = new HashMap<>();
        int skipped = 0, saved = 0;
        for (Cell cell : cells) {
            EnergyCellMapData.Entry e = entries.get(cell.rowKey());
            if (e == null) { skipped += 1; continue; }
            Target target = targetOf(e.colRule(), cell.colIndex());
            if (target == null) { skipped += 1; continue; }
            Object value = "text".equals(e.kind()) ? cell.value() : parseNumber(cell.value());
            if (value == null) { skipped += 1; continue; }
            int rowYear = year + target.yearOffset();
            String key = e.alias() + "|" + rowYear + "|" + target.month();
            pending.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(e.field(), value);
            keys.put(key, new int[]{rowYear, target.month() == null ? -1 : target.month()});
            saved += 1;
        }
        Map<String, Map<String, EnergyRow>> loaded = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> group : pending.entrySet()) {
            String alias = group.getKey().substring(0, group.getKey().indexOf('|'));
            int[] ym = keys.get(group.getKey());
            Integer month = ym[1] < 0 ? null : ym[1];
            EnergyTableAdapter adapter = adapter(alias);
            Map<String, EnergyRow> rows = loaded.computeIfAbsent(alias + "|" + ym[0], k -> rowsByMonth(adapter.load(ym[0])));
            EnergyRow existing = rows.get(String.valueOf(month));
            Map<String, Object> values = new LinkedHashMap<>();
            adapter.fields().forEach(f -> values.put(f, existing == null ? null : existing.values().get(f)));
            values.putAll(group.getValue());
            adapter.save(ym[0], month, values);
        }
        return new UpsertResult(saved, skipped);
    }

    // ── 내부 ──────────────────────────────────────────────────────

    private EnergyTableAdapter adapter(String alias) {
        EnergyTableAdapter adapter = adapters.get(alias);
        if (adapter == null) throw new BusinessException(ErrorCode.INVALID_INPUT, "에너지 표 어댑터가 없습니다: " + alias);
        return adapter;
    }

    private List<String> aliasesOf(String legacyTable) {
        return index.getOrDefault(legacyTable, Map.of()).values().stream().map(EnergyCellMapData.Entry::alias).distinct().toList();
    }

    /** 그 해와 이웃 연도(전년 12월·다음해 1월용) 행 — "year|month" → 행 */
    private Map<String, EnergyRow> loadAround(String alias, int year) {
        EnergyTableAdapter adapter = adapter(alias);
        Map<String, EnergyRow> rows = new LinkedHashMap<>();
        for (int y = year - 1; y <= year + 1; y += 1) {
            for (EnergyRow row : adapter.load(y)) rows.put(row.yearNo() + "|" + row.monthNo(), row);
        }
        return rows;
    }

    private static Map<String, EnergyRow> rowsByMonth(List<EnergyRow> rows) {
        Map<String, EnergyRow> out = new HashMap<>();
        rows.forEach(r -> out.put(String.valueOf(r.monthNo()), r));
        return out;
    }

    /** 옛 col_index → (연도 오프셋, 월). 매핑되지 않는 열(합계·평균)은 null */
    static Target targetOf(String colRule, int col) {
        switch (colRule) {
            case "eap", "efr" -> {
                if (col == 1) return new Target(-1, 12);
                if (col >= 2 && col <= 13) return new Target(0, col - 1);
                return null;
            }
            case "efp", "efs" -> {
                if (col >= 1 && col <= 12) return new Target(0, col);
                return null;
            }
            case "emc" -> {
                if (col == 0) return new Target(-1, 12);
                if (col >= 1 && col <= 12) return new Target(0, col);
                if (col == 13) return new Target(1, 1);
                return null;
            }
            case "emcNote" -> {
                return col == 0 ? new Target(0, null) : null;
            }
            default -> throw new IllegalStateException("unknown colRule " + colRule);
        }
    }

    /** 행 (연, 월) → 기준 연도 year 의 옛 col_index. 그 해 시트에 자리가 없으면 null */
    static Integer colIndexOf(String colRule, int year, int rowYear, Integer month) {
        switch (colRule) {
            case "eap", "efr" -> {
                if (rowYear == year && month != null) return month + 1;
                if (rowYear == year - 1 && Objects.equals(month, 12)) return 1;
                return null;
            }
            case "efp", "efs" -> {
                return rowYear == year && month != null ? month : null;
            }
            case "emc" -> {
                if (rowYear == year && month != null) return month;
                if (rowYear == year - 1 && Objects.equals(month, 12)) return 0;
                if (rowYear == year + 1 && Objects.equals(month, 1)) return 13;
                return null;
            }
            case "emcNote" -> {
                return rowYear == year ? 0 : null;
            }
            default -> throw new IllegalStateException("unknown colRule " + colRule);
        }
    }

    static BigDecimal parseNumber(String text) {
        if (text == null) return null;
        String clean = text.replace(",", "").trim();
        if (clean.isEmpty() || clean.equals("-")) return null;
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
