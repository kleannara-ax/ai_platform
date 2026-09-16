package com.company.module.steamenergy.legacy.service.sheet;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Excel 시트를 메모리에서 시뮬레이션하기 위한 단순한 모델.
 * Apache POI 의 Workbook/Sheet 를 대체.
 *
 * 핵심 동작:
 * - 셀 주소("A1", "AH103") → 값/수식/스타일 매핑
 * - 수식 셀은 평가 후 캐시 (입력 변경시 invalidateFormulaCache 로 무효화)
 * - 정적 텍스트, 스타일, 머지, 폭, 행높이는 classpath JSON 에서 한 번 로드
 *
 * 사용 시퀀스:
 * 1. SheetData.load("fluidized-detail") — JSON 에서 시트 템플릿 로드 (immutable template)
 * 2. SheetData copy = template.snapshot() — mutable 복사본
 * 3. copy.set("AK16", 12345)  // 입력값 적용
 * 4. Object v = copy.evaluate("AH17")  // 수식 평가
 */
public class SheetData {
    private static final Pattern CELL_REF = Pattern.compile("^([A-Z]+)([0-9]+)$");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 원본 cell 데이터: key=주소, value=Map{value|formula, style, format}. */
    private final Map<String, Map<String, Object>> cells;
    /** 동적 입력으로 덮어쓰인 값 (수식 셀의 캐시도 여기로). */
    private final Map<String, Object> overrides = new LinkedHashMap<>();
    /** "blanked" 처리된 셀 (정적 값 무시). */
    private final java.util.Set<String> blanked = new java.util.HashSet<>();
    private final Map<String, Integer> columnPx;
    private final Map<Integer, Double> rowHeights;
    private final List<Map<String, Object>> merges;
    private final int maxRow;
    private final int maxCol;

    @SuppressWarnings("unchecked")
    private SheetData(Map<String, Object> json) {
        this.cells = (Map<String, Map<String, Object>>) json.get("cells");
        this.columnPx = (Map<String, Integer>) json.getOrDefault("columns", Map.of());
        this.rowHeights = (Map<Integer, Double>) json.getOrDefault("rowHeights", Map.of());
        this.merges = (List<Map<String, Object>>) json.getOrDefault("merges", List.of());
        this.maxRow = ((Number) json.getOrDefault("maxRow", 113)).intValue();
        this.maxCol = ((Number) json.getOrDefault("maxCol", 39)).intValue();
    }

    @SuppressWarnings("unchecked")
    public static SheetData load(String name) {
        String path = "/sheet-metadata/" + name + ".json";
        try (InputStream in = SheetData.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Sheet template not found: " + path);
            return new SheetData(JSON_MAPPER.readValue(in, Map.class));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load sheet template: " + path, ex);
        }
    }

    /** 변경 가능한 새 인스턴스 생성 (template 은 immutable). 각 요청마다 호출. */
    public SheetData snapshot() {
        return new SheetData(buildSnapshotJson());
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private Map<String, Object> buildSnapshotJson() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("cells", new LinkedHashMap<>(cells));  // 같은 참조 공유 OK (override 로 분리됨)
        j.put("columns", columnPx);
        j.put("rowHeights", rowHeights);
        j.put("merges", merges);
        j.put("maxRow", maxRow);
        j.put("maxCol", maxCol);
        return j;
    }

    public int maxRow() { return maxRow; }
    public int maxCol() { return maxCol; }
    public Map<String, Integer> columnPx() { return columnPx; }
    public Map<Integer, Double> rowHeights() { return rowHeights; }
    public List<Map<String, Object>> merges() { return merges; }
    public Map<String, Map<String, Object>> rawCells() { return cells; }

    /** 입력값 설정 (수식 캐시 무효화). */
    public void set(String addr, Object value) {
        overrides.put(addr, value);
        blanked.remove(addr);
        // 입력이 바뀌면 수식 결과가 stale 가능 → 모든 캐시 비움 (단순 정책)
        clearFormulaCache();
    }

    /** 셀 빈 값으로 (정적 값도 무시). */
    public void setBlank(String addr) {
        blanked.add(addr);
        overrides.remove(addr);
        clearFormulaCache();
    }

    /** raw 값 조회 (수식 평가 안 함). 정적 값 또는 override. */
    public Object getRaw(String addr) {
        if (blanked.contains(addr)) return null;
        if (overrides.containsKey(addr)) return overrides.get(addr);
        Map<String, Object> entry = cells.get(addr);
        if (entry == null) return null;
        return entry.get("value");
    }

    /** 수식 셀이면 평가. 아니면 raw 반환. */
    public Object evaluate(String addr) {
        if (blanked.contains(addr)) return null;
        if (overrides.containsKey(addr)) return overrides.get(addr);
        Map<String, Object> entry = cells.get(addr);
        if (entry == null) return null;
        if (entry.containsKey("formula")) {
            String formula = String.valueOf(entry.get("formula"));
            String cacheKey = "__eval__" + addr;
            if (overrides.containsKey(cacheKey)) return overrides.get(cacheKey);
            Object result = FormulaEvaluator.eval(formula, this);
            overrides.put(cacheKey, result);  // 캐시
            return result;
        }
        return entry.get("value");
    }

    /** 수치값으로 평가 (null 또는 비숫자면 null). */
    public Double evaluateNumeric(String addr) {
        Object v = evaluate(addr);
        return toDouble(v);
    }

    public static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        s = s.replace(",", "");
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    public boolean hasValue(String addr) {
        if (blanked.contains(addr)) return false;
        if (overrides.containsKey(addr)) {
            Object v = overrides.get(addr);
            return v != null && !String.valueOf(v).isEmpty();
        }
        Map<String, Object> entry = cells.get(addr);
        if (entry == null) return false;
        return entry.containsKey("value") || entry.containsKey("formula");
    }

    /** 셀의 스타일 (정적). */
    public Map<String, Object> styleOf(String addr) {
        Map<String, Object> entry = cells.get(addr);
        if (entry == null) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> style = (Map<String, Object>) entry.get("style");
        return style == null ? Map.of() : style;
    }

    public String formatOf(String addr) {
        Map<String, Object> entry = cells.get(addr);
        if (entry == null) return "";
        Object f = entry.get("format");
        return f == null ? "" : String.valueOf(f);
    }

    public boolean isFormula(String addr) {
        Map<String, Object> entry = cells.get(addr);
        return entry != null && entry.containsKey("formula");
    }

    private void clearFormulaCache() {
        overrides.keySet().removeIf(k -> k.startsWith("__eval__"));
    }

    /** "A1" → {col:1, row:1}. */
    public static int[] parseRef(String addr) {
        Matcher m = CELL_REF.matcher(addr);
        if (!m.matches()) throw new IllegalArgumentException("Bad cell ref: " + addr);
        int col = 0;
        for (char c : m.group(1).toCharArray()) col = col * 26 + (c - 'A' + 1);
        int row = Integer.parseInt(m.group(2));
        return new int[]{col, row};
    }

    public static String columnLetter(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char)('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }
}
