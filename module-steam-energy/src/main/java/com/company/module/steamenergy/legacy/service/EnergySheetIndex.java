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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 에너지 회계비용 워크북에서 <b>항목 이름으로 행을, 월 머리글로 열을 찾는</b> 색인.
 *
 * <p>이 시트들은 원래 {@code excelRow = htmlRow + 고정 offset} 으로만 읽고 썼다. 그래서 원본에
 * 행이 하나 추가되면 그 아래 항목이 전부 한 칸씩 밀린 채 <b>말없이 잘못 저장</b>됐다
 * (업로드 후 값이 한 줄씩 밀리던 증상). 이제 왼쪽 라벨 칸의 글자로 행을 찾는다.
 *
 * <p>라벨은 <b>구분 열(B)에 있으면 그것을, 비어 있으면 대분류 열(A)</b>을 쓴다.
 * 원본이 "연료비 / 복합보일러 스팀구매" 처럼 두 칸을 나눠 쓰고, 소계 행은 A 에만 적혀 있기 때문이다.
 *
 * <p>월 열도 같은 이유로 머리글에서 찾는다. "1월"~"12월" 을 읽어 열 번호를 잡고,
 * 그 왼쪽 칸을 전년 12월로 본다.
 *
 * <p><b>못 찾으면 그 항목은 건너뛴다.</b> 원본에서 행을 지우면 그 자리에 다른 항목이 올라오므로,
 * 좌표로 폴백하면 <b>엉뚱한 제목 줄에 값이 붙는다</b>(지운 행 아래가 전부 한 칸씩 밀리는 증상).
 * 값을 잘못 넣느니 넣지 않고 경고로 알리는 편이 안전하다.
 */
public final class EnergySheetIndex {

    /** @param row 1-based 행, @param byLabel 라벨로 찾았으면 true, @param moved 좌표와 다르면 true */
    public record Resolved(int row, boolean byLabel, boolean moved) {}

    private static final Pattern MONTH = Pattern.compile("(\\d{1,2})\\s*월");

    /** 같은 라벨이 여러 줄에 나올 수 있다(연료비/전력비 섹션의 '제지손익' 등) — 나온 순서대로 모두 담는다. */
    private final Map<String, List<Integer>> labelRows;
    /** (행, 정규화한 라벨) 을 위에서 아래 순서로 — 표 제목을 앞부분만으로 찾을 때 쓴다. */
    private final List<int[]> rowOrder = new ArrayList<>();
    private final Map<Integer, String> rowLabel = new LinkedHashMap<>();
    private final Map<Integer, Integer> monthCols;
    private final List<String> notFound = new ArrayList<>();
    private final List<String> moved = new ArrayList<>();

    private EnergySheetIndex(Map<String, List<Integer>> labelRows, Map<Integer, Integer> monthCols) {
        this.labelRows = labelRows;
        this.monthCols = monthCols;
    }

    /**
     * @param sheet          대상 시트
     * @param labelCols      라벨이 있는 열 번호들(1-based). 앞쪽이 우선이 아니라 <b>뒤쪽(구분)이 우선</b>이다
     * @param firstRow       라벨 탐색 시작 행(1-based)
     * @param monthHeaderRow "1월"~"12월" 이 적힌 머리글 행(1-based). 0 이면 월 열은 찾지 않는다
     */
    public static EnergySheetIndex build(Sheet sheet, int[] labelCols, int firstRow, int monthHeaderRow) {
        Map<String, List<Integer>> labelRows = new LinkedHashMap<>();
        List<Integer> orderedRows = new ArrayList<>();
        Map<Integer, String> orderedLabels = new LinkedHashMap<>();
        int lastRow = sheet.getLastRowNum() + 1;
        for (int row = firstRow; row <= lastRow; row += 1) {
            String label = labelOf(sheet, row, labelCols);
            if (label.isEmpty()) continue;
            labelRows.computeIfAbsent(label, key -> new ArrayList<>()).add(row);
            orderedRows.add(row);
            orderedLabels.put(row, label);
        }

        Map<Integer, Integer> monthCols = new LinkedHashMap<>();
        if (monthHeaderRow > 0) {
            Row header = sheet.getRow(monthHeaderRow - 1);
            if (header != null) {
                Integer januaryCol = null;
                for (int col = 1; col <= header.getLastCellNum(); col += 1) {
                    Integer month = monthOf(text(sheet, monthHeaderRow, col));
                    if (month == null) continue;
                    if (month == 1 && januaryCol == null) januaryCol = col;
                    if (januaryCol != null) monthCols.putIfAbsent(month, col);
                }
                // 1월 왼쪽 칸이 전년 12월이다 (원본의 "20. 12월")
                if (januaryCol != null && januaryCol > 1) monthCols.put(0, januaryCol - 1);
            }
        }
        EnergySheetIndex index = new EnergySheetIndex(labelRows, monthCols);
        orderedRows.forEach(row -> index.rowOrder.add(new int[]{row}));
        index.rowLabel.putAll(orderedLabels);
        return index;
    }

    /** 구분 열이 비어 있으면 대분류 열을 쓴다. */
    private static String labelOf(Sheet sheet, int row, int[] labelCols) {
        for (int i = labelCols.length - 1; i >= 0; i -= 1) {
            String value = normalize(text(sheet, row, labelCols[i]));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    /**
     * 항목 이름으로 행을 찾는다. 못 찾으면 {@code declaredRow} 로 폴백한다.
     *
     * @param labels 후보 이름들 — 원본 표기가 바뀐 이력이 있으면 여러 개를 준다
     */
    public Resolved resolveRow(int declaredRow, String what, String... labels) {
        return resolveRowAfter(0, declaredRow, what, labels);
    }

    /**
     * {@code minRow} 이상에서 이름으로 행을 찾는다.
     *
     * <p>같은 이름이 여러 섹션에 나오는 시트(연료비/전력비의 "제지손익", "제지 제품생산량")를 위해
     * <b>직전에 찾은 행 다음부터</b> 훑는다. 매핑을 위에서 아래 순서로 해석하면 섹션이 섞이지 않는다.
     */
    public Resolved resolveRowAfter(int minRow, int declaredRow, String what, String... labels) {
        for (String label : labels) {
            List<Integer> rows = labelRows.get(normalize(label));
            if (rows == null) continue;
            for (int found : rows) {
                if (found < minRow) continue;
                if (found != declaredRow) moved.add(what + "(" + declaredRow + "행→" + found + "행)");
                return new Resolved(found, true, found != declaredRow);
            }
        }
        notFound.add(what);
        return new Resolved(declaredRow, false, false);
    }

    /**
     * 이름으로만 행을 찾는다. 못 찾으면 <b>null</b> 이라 호출부가 그 항목을 건너뛸 수 있다.
     * 좌표 폴백이 없으므로 값이 다른 제목 줄에 붙지 않는다.
     */
    public Integer rowOrNull(int minRow, int declaredRow, String what, String label) {
        Resolved resolved = resolveRowAfter(minRow, declaredRow, what, label);
        return resolved.byLabel() ? resolved.row() : null;
    }

    /**
     * 표 제목이 있는 행을 찾는다. 제목은 <b>앞부분만</b> 맞으면 되고, 맨 앞의 연도("2025년")는 무시한다.
     * 한 시트에 작은 표가 여러 개 쌓인 배분비율·사업계획 시트에서 표 블록을 잡는 데 쓴다.
     *
     * @return 못 찾으면 null
     */
    public Integer titleRowOrNull(int minRow, String title) {
        String key = stripYear(normalize(title));
        for (int[] entry : rowOrder) {
            int row = entry[0];
            if (row < minRow) continue;
            String label = stripYear(rowLabel.getOrDefault(row, ""));
            if (!label.isEmpty() && label.startsWith(key)) return row;
        }
        notFound.add("표 제목 '" + title + "'");
        return null;
    }

    /** 맨 앞의 연도 표기를 지운다 — "2025년 스팀생산량" 과 "2026년 스팀생산량" 을 같게 본다. */
    private static String stripYear(String normalized) {
        return normalized.replaceFirst("^(19|20)\\d{2}년?", "");
    }

    /**
     * 월 열을 찾는다. 못 찾으면 {@code declaredCol} 로 폴백한다.
     *
     * @param month 1~12, 또는 0(전년 12월)
     */
    public int monthColumn(int month, int declaredCol) {
        Integer found = monthCols.get(month);
        return found == null ? declaredCol : found;
    }

    public boolean hasMonthHeader() {
        return !monthCols.isEmpty();
    }

    /** 이름을 못 찾아 좌표로 읽은 항목들. */
    public List<String> notFound() {
        return notFound;
    }

    /** 좌표와 다른 자리에서 찾은 항목들 — 원본 양식이 바뀌었다는 뜻. */
    public List<String> moved() {
        return moved;
    }

    private static String text(Sheet sheet, int rowNumber, int colNumber) {
        if (rowNumber < 1 || colNumber < 1) return "";
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) return "";
        Cell cell = row.getCell(colNumber - 1);
        return SheetLabels.text(cell);   // 수식 칸이면 저장된 계산 결과
    }

    private static Integer monthOf(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = MONTH.matcher(text);
        if (!matcher.find()) return null;
        int value = Integer.parseInt(matcher.group(1));
        return (value >= 1 && value <= 12) ? value : null;
    }

    /**
     * 표기 차이를 흡수한다 — 공백·줄바꿈·괄호 안 설명·{@code # · - _} 를 지우고 소문자로.
     * 화면 라벨에는 "(소계)", "(× 0.7)" 같은 주석이 붙어 있어 괄호 부분은 통째로 지운다.
     */
    private static String normalize(String text) {
        if (text == null) return "";
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = value.replaceAll("\\([^)]*\\)", "");          // 괄호 설명 제거
        value = value.replaceAll("[\\s\\u00A0]+", "");
        value = value.replaceAll("[#·・\\-_\\[\\]]", "");
        return value.toLowerCase(Locale.ROOT);
    }
}
