package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
import com.company.module.steamenergy.legacy.db.TableService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 소각로 위탁운영비 원본 가져오기 — 좌표 대신 제목으로 찾아도 같은 값이 들어가는지 본다.
 *
 * <p>DB 대신 기록만 하는 저장소를 끼워 원본 파일을 실제로 가져오게 하고, 들어간 값을 원본에 보이는 값과 맞춘다.
 * 위탁운영비용(op-cost)은 예전 위치 읽기가 원본의 "고정비·스팀구매비·합계" 구조와 어긋나 있었다 —
 * 제목으로 찾은 뒤의 올바른 자리를 확인한다.
 */
class IncineratorTitleImportTest {

    /** upsert 만 기록하고 나머지는 아무것도 하지 않는 저장소. */
    static class Recording extends TableService {
        final Map<String, String> cells = new TreeMap<>();

        Recording() {
            super(null);
        }

        @Override
        public Map<String, Object> upsert(String tableName, Map<String, Object> p) {
            cells.put(p.get("table_name") + " " + p.get("row_key") + " #" + p.get("col_index"), String.valueOf(p.get("cell_value")));
            return p;
        }

        @Override
        public List<Map<String, Object>> listAllByMonth(String t, String m) {
            return List.of();
        }

        @Override
        public int deleteTableCellValuesByTables(String m, List<String> t) {
            return 0;
        }

        @Override
        public int deleteTableCellRowsByMonthAndPrefixes(String m, List<String> p) {
            return 0;
        }

        /** 칸 번호를 안 적으면 #1 (한 칸짜리 항목). */
        double number(String key) {
            if (!key.contains(" #")) key = key + " #1";
            String value = cells.get(key);
            assertTrue(value != null, key + " 가 들어가지 않았다");
            return Double.parseDouble(value.replace(",", ""));
        }
    }

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    private static Recording importOriginal() throws Exception {
        Path path = findWorkbook();
        Assumptions.assumeTrue(path != null, "소각로 위탁운영비 원본이 없어 건너뜀");
        Recording store = new Recording();
        byte[] bytes = Files.readAllBytes(path);
        new IncineratorWorkbookService(store).importWorkbook(new MockMultipartFile("f", "f.xls", null, bytes), 2025, 10);
        return store;
    }

    @Test
    @DisplayName("사업계획대비 — 월별 표·이달 요약·비고 (둘째 표의 '2025계획' 이 수식이어도 읽는다)")
    void planVs() throws Exception {
        Recording store = importOriginal();
        assertEquals(96400, store.number("incinerator_plan_vs labor|plan-m1"), 1e-6);
        assertEquals(94700, store.number("incinerator_plan_vs labor|actual-m1"), 1e-6);
        assertEquals(37204.93, store.number("incinerator_plan_vs operation|plan-m1"), 1e-6);   // B7 = "=B5"
        assertEquals(46670.8, store.number("incinerator_plan_vs operation|actual-m10"), 1e-6);
        assertEquals(143050.8, store.number("incinerator_plan_vs total|actual-m10"), 1e-6);
        assertTrue(store.cells.containsKey("incinerator_plan_vs break|m-plan #1"), "이달 계획");
        assertTrue(store.cells.containsKey("incinerator_plan_vs break|prev-actual #1"), "전월 실적");
        assertTrue(store.cells.getOrDefault("incinerator_plan_vs notes|m1-u2 #1", "").startsWith("1/1~6"), "1월 #2 비고");
    }

    @Test
    @DisplayName("가동실적 — 설비·항목 제목으로 찾는다 (전년대비 시트는 가져오기에서 일부러 무시한다)")
    void operation() throws Exception {
        Recording store = importOriginal();
        assertFalse(store.cells.keySet().stream().anyMatch(k -> k.startsWith("incinerator_yoy")), "전년대비는 DB 실적으로 계산하므로 올린 값을 쓰지 않는다");
        assertEquals(600, store.number("incinerator_operation hours|1|u1-plan"), 1e-6);
        assertEquals(391, store.number("incinerator_operation hours|1|u2-actual"), 1e-6);
        assertEquals(145391, store.number("incinerator_operation metric|1|cost-actual"), 1e-6);
    }

    @Test
    @DisplayName("총 운영비용 — 위탁운영비용은 고정비·스팀구매비·합계·차감비 제자리로 들어간다")
    void totalCost() throws Exception {
        Recording store = importOriginal();
        String t = "incinerator_total_cost ";
        assertEquals(268.16, store.number(t + "op-burn|1|burn-1"), 1e-6);
        assertEquals(11500, store.number(t + "op-cost|1|unit"), 1e-6);
        assertEquals(39195, store.number(t + "op-cost|1|u1-stop"), 1e-6);    // 1호기 고정비
        assertEquals(14007, store.number(t + "op-cost|1|u1-op"), 1e-6);      // 1호기 스팀구매비
        assertEquals(92189, store.number(t + "op-cost|1|u2-sub"), 1e-6);     // 2호기 합계
        assertEquals(-33.54, store.number(t + "op-cost|4|deduct"), 1e-6);    // 차감비(V)
        assertEquals(145391, store.number(t + "op-cost|1|total"), 1e-6);     // 비용합계(W)
        assertFalse(store.cells.containsKey(t + "op-cost|1|u1-steam #1"), "이 표에 없는 호기별 스팀량은 건너뛴다");
        assertEquals(642.04, store.number(t + "self-srf|1|in-qty"), 1e-6);
        assertEquals(194.06, store.number(t + "self-util|1|prate"), 1e-6);
        assertEquals(1300, store.number(t + "mat-oil|1|u1"), 1e-6);
        assertEquals(9217600, store.number(t + "mat-misc|1|total"), 1e-6);
    }

    @Test
    @DisplayName("사고이력·도급내역 인건비 — 머리글과 집계 줄로 범위를 잡는다")
    void accidentAndContract() throws Exception {
        Recording store = importOriginal();
        assertEquals(10.8, store.number("steam_cell_value incinerator-accident-record:1 #4"), 1e-6);   // 1번 사고 운휴시간
        assertEquals(63.0, store.number("steam_cell_value incinerator-accident-record:9 #4"), 1e-6);
        assertEquals(96380000, store.number("incinerator_contract summary|0|labor"), 1e-6);
    }

    private static Path findWorkbook() throws Exception {
        for (Path dir : List.of(Path.of("원본"), Path.of("..", "module-steam-energy", "원본"))) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> paths = Files.list(dir)) {
                Optional<Path> found = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().contains("위탁운영비"))
                        .filter(p -> !p.getFileName().toString().startsWith("~$"))
                        .findFirst();
                if (found.isPresent()) return found.get();
            }
        }
        return null;
    }
}
