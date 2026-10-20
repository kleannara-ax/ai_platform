package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "3. 세부 운영내역" 시트에서 <b>항목 이름으로</b> 줄을, <b>날짜 머리글로</b> 칸을 찾는 색인.
 *
 * <p>이 시트만 저장 방식이 특별하다 — DB 의 {@code row_key} 가 {@code "AK16"} 처럼
 * <b>셀 주소 그 자체</b>다. 화면도 계산식도 그 주소로 짜여 있어서 주소를 없앨 수는 없다.
 * 그래서 주소는 <b>서식 원본(canonical) 기준</b>으로 그대로 두고, 업로드된 파일의 실제
 * 자리만 이름으로 찾아 이어 준다.
 *
 * <pre>
 *   서식 16행 "KNE지급비용 / 추가비용"  ←→  올라온 파일에서 그 이름이 있는 줄
 *   서식 AK열 "비용"                   ←→  올라온 파일에서 "비용" 머리글이 있는 칸
 * </pre>
 *
 * <p>서식 기준 배치는 {@code sheet-metadata/fluidized-detail.json} 에서 읽는다.
 * 화면을 그릴 때 쓰는 그 파일이 곧 기준이라 따로 표를 두 벌 관리하지 않아도 된다.
 *
 * <p><b>왜 필요한가</b> — 예전에는 올라온 파일의 N행을 곧 서식의 N행으로 봤다.
 * 그래서 사용자가 줄을 하나 지우거나, 내려받기가 추가 항목을 끼워 넣은 파일을 다시 올리면
 * 그 아래가 전부 한 칸씩 밀린 채 <b>다른 항목으로 저장</b>됐다
 * ({@code mapDetailImportRow} 가 "추가비용" 한 줄만 임시로 보정하고 있었다).
 *
 * <p>줄 이름은 <b>대분류(A열) + 소분류(B열)</b> 로 만든다. A열은 병합 셀이라 위쪽 값을 이어받는다.
 * "활성탄 (KG) / 반입" 처럼 <b>같은 이름이 두 번</b> 나오는 줄이 있어서, 서식 줄 번호가
 * 커지는 순서대로 <b>직전에 찾은 줄 다음부터</b> 훑는다.
 *
 * <p>못 찾으면 그 줄·그 칸은 <b>건너뛴다.</b> 번호로 폴백하면 값이 엉뚱한 항목에 붙는다.
 */
final class FluidizedDetailSheetIndex {


    /** 서식 기준 배치를 담은 classpath JSON 이름. */
    static final String METADATA_NAME = "fluidized-detail";

    /** 항목 이름이 적힌 열(0-based) — A 가 대분류, B 가 소분류. */
    private static final int GROUP_COL = 0;
    private static final int ITEM_COL = 1;

    /** 표 머리글에서 찾을 글자 — 이 줄에 1~31 과 합계·평균·단가·비용이 늘어선다. */
    private static final String HEADER_LABEL = "구 분";

    private static final int MAX_ROW = 120;
    private static final int MAX_COL = 48;

    private final Map<Integer, Integer> rowByCanonicalRow = new LinkedHashMap<>();
    private final Map<String, Integer> columnByCanonicalColumn = new LinkedHashMap<>();
    private final List<String> notFound = new ArrayList<>();

    private FluidizedDetailSheetIndex() {
    }

    /**
     * @param sheet    올라온(또는 내려받을) 시트
     * @param metadata {@code sheet-metadata/fluidized-detail.json} 내용
     */
    static FluidizedDetailSheetIndex of(Sheet sheet, Map<String, Object> metadata) {
        FluidizedDetailSheetIndex index = new FluidizedDetailSheetIndex();
        if (sheet == null || metadata == null) return index;

        index.mapRows(sheet, metadata);
        index.mapColumns(sheet, metadata);
        return index;
    }

    // ── 줄 잇기 ──────────────────────────────────────────────────────────────

    private void mapRows(Sheet sheet, Map<String, Object> metadata) {
        Map<Integer, String> canonical = canonicalRowLabels(metadata);
        Map<String, List<Integer>> actual = actualRowLabels(sheet);

        int cursor = 0;
        for (Map.Entry<Integer, String> entry : canonical.entrySet()) {
            List<Integer> candidates = actual.get(entry.getValue());
            Integer found = null;
            if (candidates != null) {
                for (int row : candidates) {
                    if (row >= cursor) {
                        found = row;
                        break;
                    }
                }
            }
            if (found == null) {
                notFound.add(entry.getKey() + "행 '" + entry.getValue() + "'");
                continue;
            }
            rowByCanonicalRow.put(entry.getKey(), found);
            cursor = found + 1;
        }
    }

    /** 서식 JSON 의 A·B 글자로 {서식 줄 번호(1-based) → 이름}. */
    private static Map<Integer, String> canonicalRowLabels(Map<String, Object> metadata) {
        Map<Integer, String> labels = new LinkedHashMap<>();
        int maxRow = intValue(metadata.get("maxRow"), MAX_ROW);
        String group = "";
        for (int row = 1; row <= Math.min(maxRow, MAX_ROW); row += 1) {
            String groupText = metadataText(metadata, "A" + row);
            String itemText = metadataText(metadata, "B" + row);
            if (!groupText.isBlank()) group = groupText;      // 병합 셀 이어받기
            if (group.isBlank() && itemText.isBlank()) continue;
            labels.put(row, key(group, itemText));
        }
        return labels;
    }

    /** 올라온 시트의 A·B 글자로 {이름 → 0-based 줄 목록}. 같은 이름이 여러 줄일 수 있다. */
    private static Map<String, List<Integer>> actualRowLabels(Sheet sheet) {
        Map<String, List<Integer>> labels = new LinkedHashMap<>();
        String group = "";
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROW);
        for (int row = 0; row <= lastRow; row += 1) {
            String groupText = text(sheet, row, GROUP_COL);
            String itemText = text(sheet, row, ITEM_COL);
            if (!groupText.isBlank()) group = groupText;
            if (group.isBlank() && itemText.isBlank()) continue;
            labels.computeIfAbsent(key(group, itemText), k -> new ArrayList<>()).add(row);
        }
        return labels;
    }

    // ── 칸 잇기 ──────────────────────────────────────────────────────────────

    private void mapColumns(Sheet sheet, Map<String, Object> metadata) {
        int canonicalHeaderRow = canonicalHeaderRow(metadata);
        int actualHeaderRow = actualHeaderRow(sheet);
        if (canonicalHeaderRow < 1 || actualHeaderRow < 0) {
            notFound.add("'" + HEADER_LABEL + "' 머리글 줄");
            return;
        }

        Map<String, Integer> actual = new LinkedHashMap<>();
        for (int col = 0; col < MAX_COL; col += 1) {
            String header = normalize(text(sheet, actualHeaderRow, col));
            if (!header.isEmpty()) actual.putIfAbsent(header, col);
        }
        for (int col = 1; col <= MAX_COL; col += 1) {
            String label = columnLabel(col);
            String header = normalize(metadataText(metadata, label + canonicalHeaderRow));
            if (header.isEmpty()) continue;
            Integer found = actual.get(header);
            if (found == null) {
                notFound.add(label + "열 '" + header + "'");
                continue;
            }
            columnByCanonicalColumn.put(label, found);
        }
    }

    private static int canonicalHeaderRow(Map<String, Object> metadata) {
        int maxRow = intValue(metadata.get("maxRow"), MAX_ROW);
        for (int row = 1; row <= Math.min(maxRow, MAX_ROW); row += 1) {
            if (normalize(metadataText(metadata, "A" + row)).equals(normalize(HEADER_LABEL))) return row;
        }
        return -1;
    }

    private static int actualHeaderRow(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROW);
        for (int row = 0; row <= lastRow; row += 1) {
            if (normalize(text(sheet, row, GROUP_COL)).equals(normalize(HEADER_LABEL))) return row;
        }
        return -1;
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    /** 서식 줄 번호(1-based) → 올라온 시트의 0-based 줄. 없으면 null. */
    Integer rowFor(int canonicalRow) {
        return rowByCanonicalRow.get(canonicalRow);
    }

    /** 서식 열 이름("C", "AK") → 올라온 시트의 0-based 칸. 없으면 null. */
    Integer columnFor(String canonicalColumn) {
        return columnByCanonicalColumn.get(canonicalColumn);
    }

    boolean usable() {
        return !rowByCanonicalRow.isEmpty() && !columnByCanonicalColumn.isEmpty();
    }

    /** 이름을 못 찾아 건너뛴 줄·칸. */
    List<String> notFound() {
        return notFound;
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String metadataText(Map<String, Object> metadata, String cellRef) {
        Object cells = metadata.get("cells");
        if (!(cells instanceof Map)) return "";
        Object cell = ((Map<String, Object>) cells).get(cellRef);
        if (!(cell instanceof Map)) return "";
        Object value = ((Map<String, Object>) cell).get("value");
        if (value == null) return "";
        // 날짜 머리글(1~31)은 JSON 에 숫자로 들어 있다 — 시트에서 읽은 "1" 과 맞추려면
        // 소수점을 붙이지 않고 찍어야 한다.
        if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value).trim();
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static String text(Sheet sheet, int rowIndex, int colIndex) {
        if (rowIndex < 0 || colIndex < 0) return "";
        Row row = sheet.getRow(rowIndex);
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        return SheetLabels.text(cell);   // 수식 칸이면 저장된 계산 결과
    }

    private static String key(String group, String item) {
        return normalize(group) + "|" + normalize(item);
    }

    /** 1-based 열 번호 → "A", "AK" 같은 이름. */
    private static String columnLabel(int col) {
        StringBuilder label = new StringBuilder();
        int value = col;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            label.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return label.toString();
    }

    /** 표기 차이를 흡수한다 — {@link HeaderColumnIndex} 와 같은 규칙이다. */
    private static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC);
        text = text.replaceAll("\\([^)]*\\)", "");
        text = text.replaceAll("[\\s\\u00A0]+", "");
        text = text.replaceAll("[#·・\\-_\\[\\]]", "");
        return text.toLowerCase(Locale.ROOT);
    }
}
