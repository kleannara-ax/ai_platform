package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 운전일지 셀 ↔ 하루 한 행 변환 — 주어진 칸만 덮어쓰고 나머지는 그대로 두는지.
 */
class DailyLogCellsTest {

    private static final Map<Integer, String> FIELDS = Map.of(100, "steamU1", 101, "steamU2First", 104, "steamPressure");

    /** 메모리에 행을 들고 있는 가짜 표 */
    private static class FakeTable implements SteamTableAdapter {
        final List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> lastSaved = List.of();
        boolean lastClearMissing = true;

        @Override public String alias() { return "fake"; }
        @Override public String time() { return "day"; }
        @Override public List<String> keyFields() { return List.of("usageDate"); }
        @Override public List<String> valueFields() { return List.of("steamU1", "steamU2First", "steamPressure"); }
        @Override public Class<?> typeOf(String field) { return "steamPressure".equals(field) ? String.class : BigDecimal.class; }
        @Override public List<Map<String, Object>> load(int year, Integer month) { return rows; }

        @Override
        public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> saved, boolean clearMissing) {
            lastSaved = saved;
            lastClearMissing = clearMissing;
            return null;
        }
    }

    private static Map<String, Object> row(int day, String steamU1, String steamU2First) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("usageDate", LocalDate.of(2026, 7, day));
        row.put("steamU1", steamU1 == null ? null : new BigDecimal(steamU1));
        row.put("steamU2First", steamU2First == null ? null : new BigDecimal(steamU2First));
        row.put("steamPressure", null);
        return row;
    }

    @Test
    @DisplayName("읽기: 값이 있는 칸만 일|항목번호 로 편다")
    void loadCells() {
        FakeTable table = new FakeTable();
        table.rows.add(row(3, "12.500000", null));
        Map<String, String> cells = DailyLogCells.load(table, FIELDS, 2026, 7);
        assertEquals(Map.of("3|100", "12.5"), cells);
    }

    @Test
    @DisplayName("쓰기: 주어진 칸만 바꾸고 그 날의 다른 칸은 그대로, 목록에 없는 날은 보내지 않는다")
    void mergeKeepsOtherColumns() {
        FakeTable table = new FakeTable();
        table.rows.add(row(3, "12.5", "7"));
        table.rows.add(row(4, "1", "2"));

        int saved = DailyLogCells.merge(table, FIELDS, 2026, 7, Map.of("3|100", "20", "5|104", "저"));

        assertEquals(2, saved);
        assertFalse(table.lastClearMissing);
        assertEquals(2, table.lastSaved.size());
        Map<String, Object> day3 = table.lastSaved.get(0);
        assertEquals(LocalDate.of(2026, 7, 3), day3.get("usageDate"));
        assertEquals(new BigDecimal("20"), day3.get("steamU1"));
        assertEquals(new BigDecimal("7"), day3.get("steamU2First"));
        Map<String, Object> day5 = table.lastSaved.get(1);
        assertEquals(LocalDate.of(2026, 7, 5), day5.get("usageDate"));
        assertEquals("저", day5.get("steamPressure"));
    }

    @Test
    @DisplayName("쓰기: 빈 값 · 숫자 칸의 글자 · 없는 날 · 모르는 항목 번호는 건너뛴다")
    void mergeSkipsInvalid() {
        FakeTable table = new FakeTable();
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("1|100", " ");
        cells.put("2|100", "점검");
        cells.put("32|100", "5");
        cells.put("3|999", "5");
        cells.put("x|100", "5");
        assertEquals(0, DailyLogCells.merge(table, FIELDS, 2026, 7, cells));
        assertTrue(table.lastSaved.isEmpty());
    }

    @Test
    @DisplayName("폐합성소각로 항목표: 87개, 필드 이름이 겹치지 않는다")
    void wasteFields() {
        assertEquals(87, WasteIncinLogFields.FIELDS.size());
        assertEquals(87, WasteIncinLogFields.FIELDS.values().stream().distinct().count());
        assertEquals("steamPressure", WasteIncinLogFields.FIELDS.get(104));
        assertEquals("inboundVendor14", WasteIncinLogFields.FIELDS.get(713));
    }
}
