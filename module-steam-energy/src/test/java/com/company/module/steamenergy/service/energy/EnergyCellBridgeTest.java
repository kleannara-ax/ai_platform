package com.company.module.steamenergy.service.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 셀 브리지 — 옛 col_index 규약 해석과 행 단위 묶음 저장. 표 어댑터는 메모리 가짜로 대신한다.
 */
class EnergyCellBridgeTest {

    /** 메모리 어댑터: (year|month) → 값 맵 */
    static class FakeAdapter implements EnergyTableAdapter {
        final String alias; final boolean monthly; final Set<String> fields;
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        int saves = 0;
        FakeAdapter(String alias, boolean monthly, String... fields) { this.alias = alias; this.monthly = monthly; this.fields = Set.of(fields); }
        public String alias() { return alias; }
        public boolean monthly() { return monthly; }
        public Set<String> fields() { return fields; }
        public List<EnergyRow> load(int year) {
            List<EnergyRow> out = new ArrayList<>();
            rows.forEach((k, v) -> {
                int y = Integer.parseInt(k.split("\\|")[0]);
                String m = k.split("\\|")[1];
                if (y == year) out.add(new EnergyRow(y, m.equals("null") ? null : Integer.parseInt(m), new LinkedHashMap<>(v)));
            });
            return out;
        }
        public void save(int yearNo, Integer monthNo, Map<String, Object> values) {
            saves += 1;
            rows.put(yearNo + "|" + monthNo, new LinkedHashMap<>(values));
        }
    }

    @Test
    @DisplayName("col 규약: eap/efr 1=전년12월, emc 0/13=이웃 연도, efp 합계 열은 무시")
    void colRules() {
        assertEquals(new EnergyCellBridge.Target(-1, 12), targetOf("eap", 1));
        assertEquals(new EnergyCellBridge.Target(0, 1), targetOf("eap", 2));
        assertEquals(new EnergyCellBridge.Target(0, 12), targetOf("efr", 13));
        assertNull(targetOf("efr", 14));
        assertEquals(new EnergyCellBridge.Target(0, 12), targetOf("efp", 12));
        assertNull(targetOf("efp", 13));
        assertEquals(new EnergyCellBridge.Target(-1, 12), targetOf("emc", 0));
        assertEquals(new EnergyCellBridge.Target(1, 1), targetOf("emc", 13));
        assertEquals(new EnergyCellBridge.Target(0, null), targetOf("emcNote", 0));

        assertEquals(1, EnergyCellBridge.colIndexOf("efr", 2025, 2024, 12));
        assertEquals(3, EnergyCellBridge.colIndexOf("efr", 2025, 2025, 2));
        assertNull(EnergyCellBridge.colIndexOf("efr", 2025, 2024, 11));
        assertEquals(13, EnergyCellBridge.colIndexOf("emc", 2025, 2026, 1));
        assertEquals(0, EnergyCellBridge.colIndexOf("emcNote", 2025, 2025, null));
    }

    private static EnergyCellBridge.Target targetOf(String rule, int col) {
        return EnergyCellBridge.targetOf(rule, col);
    }

    @Test
    @DisplayName("upsertCells: 같은 (표, 연, 월) 의 셀은 한 번에 저장되고, 전년 12월은 전년도 12월 행에 들어가며, 매핑 없는 셀은 건너뛴다")
    void upsertGroupsByRow() {
        // 실제 매핑(EnergyCellMapData)을 그대로 쓰고 어댑터만 가짜 — ratioBoiler 의 t01|r01(steamLngBoiler), t01|r02(steamComposite)
        FakeAdapter boiler = new FakeAdapter("ratioBoiler", true, "steamLngBoiler", "steamComposite", "costLngBoiler");
        List<EnergyTableAdapter> adapters = new ArrayList<>();
        adapters.add(boiler);
        EnergyCellMapData.ENTRIES.stream().map(EnergyCellMapData.Entry::alias).distinct()
                .filter(a -> !a.equals("ratioBoiler")).forEach(a -> adapters.add(new FakeAdapter(a, true)));
        EnergyCellBridge bridge = new EnergyCellBridge(adapters);

        EnergyCellBridge.UpsertResult result = bridge.upsertCells("energy_fuel_ratio", 2025, List.of(
                new EnergyCellBridge.Cell("t01|r01", 2, "100"),      // 2025-01 LNG 보일러
                new EnergyCellBridge.Cell("t01|r02", 2, "200"),      // 2025-01 복합
                new EnergyCellBridge.Cell("t01|r01", 1, "90"),       // 전년 12월 → 2024-12
                new EnergyCellBridge.Cell("t01|r01", 14, "999"),     // 합계 열 → 무시
                new EnergyCellBridge.Cell("t03|r06", 2, "1.2"),      // 합계 행(계산 전용, 매핑 없음) → 무시
                new EnergyCellBridge.Cell("t01|r02", 3, "백만원")      // 숫자 아님 → 무시
        ));

        assertEquals(3, result.saved());
        assertEquals(3, result.skipped());
        assertEquals(2, boiler.saves, "2025-01 한 번, 2024-12 한 번");
        Map<String, Object> jan = boiler.rows.get("2025|1");
        assertEquals(new BigDecimal("100"), jan.get("steamLngBoiler"));
        assertEquals(new BigDecimal("200"), jan.get("steamComposite"));
        assertNull(jan.get("costLngBoiler"), "보내지 않은 필드는 비어 있다");
        assertEquals(new BigDecimal("90"), boiler.rows.get("2024|12").get("steamLngBoiler"));

        // export 방향: 2025 기준으로 읽으면 전년 12월이 col 1 로 나온다
        List<Map<String, Object>> cells = bridge.cellsOf("energy_fuel_ratio", 2025);
        assertTrue(cells.stream().anyMatch(c -> c.get("row_key").equals("t01|r01") && c.get("col_index").equals(1) && c.get("cell_value").equals("90")));
        assertTrue(cells.stream().anyMatch(c -> c.get("row_key").equals("t01|r02") && c.get("col_index").equals(2) && c.get("cell_value").equals("200")));
    }

    @Test
    @DisplayName("clearYear 는 그 해 월 행만 비우고 전년 12월 행과 연도 비고는 그대로 둔다")
    void clearYearKeepsNeighbours() {
        FakeAdapter boiler = new FakeAdapter("ratioBoiler", true, "steamLngBoiler");
        boiler.rows.put("2025|1", new LinkedHashMap<>(Map.of("steamLngBoiler", new BigDecimal("1"))));
        boiler.rows.put("2024|12", new LinkedHashMap<>(Map.of("steamLngBoiler", new BigDecimal("9"))));
        FakeAdapter note = new FakeAdapter("monthCompareNote", false, "noteFuel");
        note.rows.put("2025|null", new LinkedHashMap<>(Map.of("noteFuel", "메모")));
        List<EnergyTableAdapter> adapters = new ArrayList<>(List.of(boiler, note));
        EnergyCellMapData.ENTRIES.stream().map(EnergyCellMapData.Entry::alias).distinct()
                .filter(a -> !a.equals("ratioBoiler") && !a.equals("monthCompareNote")).forEach(a -> adapters.add(new FakeAdapter(a, true)));
        EnergyCellBridge bridge = new EnergyCellBridge(adapters);

        bridge.clearYear("energy_fuel_ratio", 2025);
        bridge.clearYear("energy_month_compare", 2025);

        assertNull(boiler.rows.get("2025|1").get("steamLngBoiler"));
        assertEquals(new BigDecimal("9"), boiler.rows.get("2024|12").get("steamLngBoiler"));
        assertEquals("메모", note.rows.get("2025|null").get("noteFuel"));
    }
}
