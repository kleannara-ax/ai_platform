package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
import com.company.module.steamenergy.legacy.db.TableService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.*;
import java.util.*;

/**
 * 소각로 위탁운영비 왕복 — 원본 가져오기 → 템플릿 내려받기 → 다시 가져오기 가 같은 값을 돌려주는지 본다.
 *
 * <p>가져오기와 내려받기가 같은 제목 기반 배치를 쓰므로 한 칸도 어긋나면 안 된다. 예전에는 내려받기가 좌표로 써서
 * 총 운영비용이 한 달씩 밀리고, 사고 기록이 모두 빠지고, 비고에 템플릿의 옛 글자가 붙었다.
 */
class IncineratorRoundTripTest {

    /** upsert 를 모아 두고 listAllByMonth 로 돌려주는 저장소. */
    static class Store extends TableService {
        final List<Map<String, Object>> rows = new ArrayList<>();
        Store() { super(null); }
        @Override public Map<String, Object> upsert(String t, Map<String, Object> p) { rows.add(new LinkedHashMap<>(p)); return p; }
        @Override public List<Map<String, Object>> listAllByMonth(String t, String month) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> r : rows) if (month.equals(String.valueOf(r.get("month")))) out.add(r);
            return out;
        }
        @Override public int deleteTableCellValuesByTables(String m, List<String> t) { return 0; }
        @Override public int deleteTableCellRowsByMonthAndPrefixes(String m, List<String> p) { return 0; }
        Map<String, String> snapshot() {
            Map<String, String> m = new TreeMap<>();
            for (Map<String, Object> r : rows) m.put(r.get("month") + " # " + r.get("table_name") + " # " + r.get("row_key") + " # " + r.get("col_index"), norm(String.valueOf(r.get("cell_value"))));
            return m;
        }
        static String norm(String v) {
            try { return new java.math.BigDecimal(v.replace(",", "")).stripTrailingZeros().toPlainString(); } catch (Exception e) { return v.trim(); }
        }
    }

    @Test
    @DisplayName("원본 → 내려받기 → 다시 가져오기 가 모든 칸에서 같다")
    void roundTrip() throws Exception {
        SteamPoiConfig.applyLimits();
        Path path = Path.of("..", "원본", "5. 소각로 위탁운영비 첨부자료(25년10월).xls");
        Assumptions.assumeTrue(Files.exists(path), "소각로 위탁운영비 원본이 없어 건너뜀");
        byte[] original = Files.readAllBytes(path);
        Store first = new Store();
        new IncineratorWorkbookService(first).importWorkbook(new MockMultipartFile("f", "f.xls", null, original), 2025, 10);
        byte[] exported = new IncineratorWorkbookService(first).exportWorkbook(2025, 10);
        Store second = new Store();
        new IncineratorWorkbookService(second).importWorkbook(new MockMultipartFile("f", "f.xlsx", null, exported), 2025, 10);

        Map<String, String> a = first.snapshot(), b = second.snapshot();
        List<String> out = new ArrayList<>();
        Map<String, Integer> groups = new TreeMap<>();
        Set<String> keys = new TreeSet<>(a.keySet()); keys.addAll(b.keySet());
        for (String k : keys) {
            if (Objects.equals(a.get(k), b.get(k))) continue;
            String[] p = k.split(" # ");
            String g = p[1] + " " + p[2].replaceAll("[|]\\d+[|]", "|*|").replaceAll("-m\\d+$", "-m*").replaceAll(":\\d+$", ":*") + " #" + p[3];
            groups.merge(g + (a.containsKey(k) ? (b.containsKey(k) ? " (값 다름)" : " (다시 올리면 빠짐)") : " (새로 생김)"), 1, Integer::sum);
            if (out.size() < 60) out.add("  " + k + " : 처음=" + a.get(k) + " / 왕복=" + b.get(k));
        }
        org.junit.jupiter.api.Assertions.assertEquals(a.size(), b.size(), "왕복 칸 수가 다르다");
        org.junit.jupiter.api.Assertions.assertTrue(groups.isEmpty(), "왕복에서 달라진 칸: " + groups + System.lineSeparator() + String.join(System.lineSeparator(), out));
    }
}
