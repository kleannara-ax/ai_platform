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
 * 표 머리글 글자로 <b>열</b>을, 일자 칸으로 <b>행</b>을 찾는 색인.
 *
 * <p>원단위 일지처럼 "설비(위) + 항목(아래)" 두 줄짜리 머리글을 쓰는 시트에 두루 쓴다.
 *
 * <p>이 시트는 하루가 한 줄이고 설비별 항목이 열로 늘어선다. 예전에는 열 번호를 코드에 박아
 * ({@code main_steam=1, coater_steam=2 …}) 읽고 썼기 때문에, 원본에 열이 하나 추가되면
 * 그 오른쪽 값이 전부 <b>다른 항목으로 저장</b>됐다.
 *
 * <p>머리글은 두 줄이다 — 위가 설비(PM-2, PM-3, TM-3 …), 아래가 항목(Main 스팀량, 생산량(kg) …).
 * 병합 셀은 좌상단에만 글자가 있으므로 <b>직전 값을 이어서</b> 설비명으로 쓴다.
 * 디스퍼져 총량·TOC 스팀량처럼 설비 없이 맨 윗줄에만 이름이 있는 열은 그 이름으로 찾는다.
 *
 * <p>못 찾으면 <b>그 항목은 건너뛴다.</b> 열 번호로 폴백하면 값이 엉뚱한 항목에 붙기 때문이다.
 */
public final class HeaderColumnIndex {


    private final Map<String, Integer> byGroupItem = new LinkedHashMap<>();
    /** 같은 (설비, 항목) 이 여러 열에 나올 수 있다 — 왼쪽부터 순서대로 담는다. */
    private final Map<String, List<Integer>> allByGroupItem = new LinkedHashMap<>();
    private final Map<String, Integer> byTopLabel = new LinkedHashMap<>();
    private final Map<Integer, Integer> dayRows = new LinkedHashMap<>();
    private final List<String> notFound = new ArrayList<>();

    private HeaderColumnIndex() {
    }

    /**
     * @param sheet     월별 일지 시트
     * @param topRow    설비 위에 있는 큰 구분 줄(0-based). 없으면 -1
     * @param groupRow  설비 줄(0-based)
     * @param itemRow   항목 줄(0-based)
     * @param firstRow  데이터 첫 줄(0-based)
     * @param lastRow   데이터 마지막 줄(0-based)
     * @param dayCol    일자가 적힌 열(0-based)
     */
    public static HeaderColumnIndex build(Sheet sheet, int topRow, int groupRow, int itemRow,
                                               int firstRow, int lastRow, int dayCol) {
        return build(sheet, topRow, groupRow, itemRow, firstRow, lastRow, dayCol,
                0, maxColumn(sheet, groupRow, itemRow));
    }

    /**
     * 열 범위를 제한해서 만든다.
     *
     * <p>도급내역처럼 <b>같은 머리글을 쓰는 표가 좌우로 두 벌</b> 놓인 시트에서,
     * 한쪽 블록만 보도록 범위를 좁히는 데 쓴다.
     */
    public static HeaderColumnIndex build(Sheet sheet, int topRow, int groupRow, int itemRow,
                                               int firstRow, int lastRow, int dayCol,
                                               int firstCol, int lastCol) {
        HeaderColumnIndex index = new HeaderColumnIndex();

        String group = "";
        for (int col = firstCol; col <= lastCol; col += 1) {
            String groupText = text(sheet, groupRow, col);
            if (!groupText.isBlank()) group = groupText;      // 병합 셀 이어받기
            String item = text(sheet, itemRow, col);

            if (!item.isBlank() && !group.isBlank()) {
                index.byGroupItem.putIfAbsent(key(group, item), col);
                index.allByGroupItem.computeIfAbsent(key(group, item), k -> new ArrayList<>()).add(col);
            }
            if (topRow >= 0 && item.isBlank()) {
                String top = text(sheet, topRow, col);
                if (!top.isBlank()) index.byTopLabel.putIfAbsent(normalize(top), col);
            }
        }

        for (int row = firstRow; row <= lastRow; row += 1) {
            Integer day = asDay(text(sheet, row, dayCol));
            if (day != null) index.dayRows.putIfAbsent(day, row);
        }
        return index;
    }

    /** 설비 + 항목으로 열을 찾는다. 없으면 null — 호출부가 그 항목을 건너뛴다. */
    public Integer column(String group, String item) {
        Integer col = byGroupItem.get(key(group, item));
        if (col == null) notFound.add(group + " " + item);
        return col;
    }

    /**
     * {@code minCol} 이상에서 설비 + 항목으로 열을 찾는다.
     *
     * <p>한 줄에 같은 항목 이름이 여러 번 나오는 시트(도급내역의 "운영비용(원)" 처럼 가동분·운휴분이
     * 같은 이름)를 위해, <b>직전에 찾은 열 다음부터</b> 훑는다. 매핑을 왼쪽에서 오른쪽 순서로
     * 해석하면 항목이 섞이지 않는다.
     */
    public Integer columnAfter(int minCol, String group, String item) {
        List<Integer> cols = allByGroupItem.get(key(group, item));
        if (cols != null) {
            for (int col : cols) {
                if (col >= minCol) return col;
            }
        }
        notFound.add(group + " " + item);
        return null;
    }

    /** 설비 없이 맨 윗줄에만 이름이 있는 열(디스퍼져 총량·TOC 스팀량 등). */
    public Integer columnByTopLabel(String label) {
        Integer col = byTopLabel.get(normalize(label));
        if (col == null) notFound.add(label);
        return col;
    }

    /** 일자가 적힌 줄. 없으면 null. */
    public Integer rowOfDay(int day) {
        return dayRows.get(day);
    }

    public boolean hasDays() {
        return !dayRows.isEmpty();
    }

    /** 머리글을 못 찾아 건너뛴 항목들. */
    public List<String> notFound() {
        return notFound;
    }

    private static int maxColumn(Sheet sheet, int groupRow, int itemRow) {
        int max = 0;
        for (int rowNumber : new int[]{groupRow, itemRow}) {
            Row row = sheet.getRow(rowNumber);
            if (row != null) max = Math.max(max, row.getLastCellNum());
        }
        return Math.max(max, 0);
    }

    private static String text(Sheet sheet, int rowIndex, int colIndex) {
        if (rowIndex < 0 || colIndex < 0) return "";
        Row row = sheet.getRow(rowIndex);
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        return SheetLabels.text(cell);   // 수식 칸이면 저장된 계산 결과
    }

    private static Integer asDay(String text) {
        if (text == null || text.isBlank()) return null;
        String digits = text.trim().replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) return null;
        try {
            int value = Integer.parseInt(digits);
            return (value >= 1 && value <= 31) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String key(String group, String item) {
        return normalize(group) + "" + normalize(item);
    }

    /** 표기 차이를 흡수한다 — 공백·줄바꿈·괄호 설명·{@code # · - _} 제거 후 소문자. */
    private static String normalize(String text) {
        if (text == null) return "";
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = value.replaceAll("\\([^)]*\\)", "");
        value = value.replaceAll("[\\s\\u00A0]+", "");
        value = value.replaceAll("[#·・\\-_\\[\\]]", "");
        return value.toLowerCase(Locale.ROOT);
    }
}
