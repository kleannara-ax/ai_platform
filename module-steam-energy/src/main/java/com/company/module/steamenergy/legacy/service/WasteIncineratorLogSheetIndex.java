package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Header;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Layout;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.SheetMap;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 운전일지 시트에서 <b>헤더 글자로 항목 위치를 찾는</b> 색인.
 *
 * <p>원본 양식에 행이나 열이 추가·삭제되면 고정 좌표는 통째로 밀린다. 그 상태로 읽으면
 * 경고도 없이 엉뚱한 값이 저장되므로, 시트의 라벨 줄을 실제로 훑어
 * "헤더 글자 → 실제 줄" 색인을 만들고 그걸로 위치를 정한다.
 *
 * <ul>
 *   <li>일자가 열인 시트 — 라벨 열(A, 또는 A+B)을 훑어 항목 행을, 일자 헤더 행에서 일자 열을 찾는다</li>
 *   <li>일자가 행인 시트 — 라벨 행(2행, 또는 3+4행)을 훑어 항목 열을, 일자 열에서 일자 행을 찾는다</li>
 * </ul>
 *
 * <p>병합 셀은 좌상단에만 글자가 있으므로 <b>직전 값을 이어서</b> 상위 구분으로 쓴다
 * (소각량 A4 "폐1" 이 A9 까지 이어지는 구조).
 *
 * <p>헤더를 못 찾으면 {@link WasteIncineratorLogCellMap.Entry#line()} 의 좌표로 폴백하고
 * 경고를 남긴다. 찾았는데 좌표와 다르면 "양식이 바뀌었다"는 안내를 남긴다.
 * 어느 쪽이든 예전(좌표 전용)보다 나빠지지 않는다.
 */
public final class WasteIncineratorLogSheetIndex {

    /** 시트의 (행, 열) 글자를 읽어 주는 함수. POI 읽기 방식은 호출부가 정한다. */
    @FunctionalInterface
    public interface CellText {
        /** @param row 1-based 행, @param col 1-based 열 */
        String at(int row, int col);
    }

    /**
     * @param line     실제로 읽을 줄 (일자가 열인 시트면 행 번호, 행인 시트면 열 번호)
     * @param byHeader 헤더 글자로 찾았으면 true, 좌표 폴백이면 false
     * @param moved    헤더로 찾은 위치가 매핑표 좌표와 다르면 true
     */
    public record Resolved(int line, boolean byHeader, boolean moved) {}

    private static final int MAX_SCAN = 200;

    private final SheetMap sheetMap;
    private final Map<String, Integer> headerIndex;
    private final Map<Integer, Integer> dayIndex;
    private final List<String> notFound = new ArrayList<>();
    private final List<String> moved = new ArrayList<>();

    private WasteIncineratorLogSheetIndex(SheetMap sheetMap,
                                          Map<String, Integer> headerIndex,
                                          Map<Integer, Integer> dayIndex) {
        this.sheetMap = sheetMap;
        this.headerIndex = headerIndex;
        this.dayIndex = dayIndex;
    }

    public static WasteIncineratorLogSheetIndex build(Sheet sheet, SheetMap sheetMap, CellText text) {
        return sheetMap.layout() == Layout.DAYS_IN_COLUMNS
                ? buildForDaysInColumns(sheet, sheetMap, text)
                : buildForDaysInRows(sheet, sheetMap, text);
    }

    // ── 일자가 열인 시트: 항목 = 행 ────────────────────────────────
    private static WasteIncineratorLogSheetIndex buildForDaysInColumns(
            Sheet sheet, SheetMap sheetMap, CellText text) {

        Map<Integer, Integer> days = new LinkedHashMap<>();
        for (int col = 1; col <= MAX_SCAN; col += 1) {
            Integer day = asDay(text.at(sheetMap.dayHeaderLine(), col));
            if (day != null) days.putIfAbsent(day, col);
        }

        Map<String, Integer> headers = new LinkedHashMap<>();
        int[] labelCols = sheetMap.labelLines();
        int lastRow = sheet.getLastRowNum() + 1;
        String group = null;
        for (int row = sheetMap.dayHeaderLine() + 1; row <= lastRow; row += 1) {
            String item;
            if (labelCols.length >= 2) {
                String groupText = text.at(row, labelCols[0]);
                if (!groupText.isBlank()) group = groupText;   // 병합 셀은 좌상단에만 글자가 있다
                item = text.at(row, labelCols[1]);
            } else {
                group = null;
                item = text.at(row, labelCols[0]);
            }
            if (item == null || item.isBlank()) continue;
            headers.putIfAbsent(key(group, item), row);
        }
        return new WasteIncineratorLogSheetIndex(sheetMap, headers, days);
    }

    // ── 일자가 행인 시트: 항목 = 열 ────────────────────────────────
    private static WasteIncineratorLogSheetIndex buildForDaysInRows(
            Sheet sheet, SheetMap sheetMap, CellText text) {

        Map<Integer, Integer> days = new LinkedHashMap<>();
        int lastRow = sheet.getLastRowNum() + 1;
        for (int row = 1; row <= lastRow; row += 1) {
            Integer day = asDay(text.at(row, sheetMap.dayHeaderLine()));
            if (day != null) days.putIfAbsent(day, row);
        }

        Map<String, Integer> headers = new LinkedHashMap<>();
        int[] labelRows = sheetMap.labelLines();
        String group = null;
        for (int col = sheetMap.dayHeaderLine() + 1; col <= MAX_SCAN; col += 1) {
            String item;
            if (labelRows.length >= 2) {
                String groupText = text.at(labelRows[0], col);
                if (!groupText.isBlank()) group = groupText;
                item = text.at(labelRows[1], col);
            } else {
                group = null;
                item = text.at(labelRows[0], col);
            }
            if (item == null || item.isBlank()) continue;
            headers.putIfAbsent(key(group, item), col);
        }
        return new WasteIncineratorLogSheetIndex(sheetMap, headers, days);
    }

    /** 항목의 실제 줄을 정한다. 헤더로 못 찾으면 매핑표 좌표로 폴백한다. */
    public Resolved resolve(Entry entry) {
        int declared = sheetMap.layout() == Layout.DAYS_IN_COLUMNS
                ? WasteIncineratorLogCellMap.parseRow(entry.line())
                : WasteIncineratorLogCellMap.parseCol(entry.line());

        Header header = entry.header();
        if (header == null) return new Resolved(declared, false, false);

        Integer found = headerIndex.get(key(header.group(), header.item()));
        if (found == null) {
            notFound.add(entry.label());
            return new Resolved(declared, false, false);
        }
        if (found != declared) {
            moved.add(entry.label() + "(" + describe(declared) + "→" + describe(found) + ")");
        }
        return new Resolved(found, true, found != declared);
    }

    /** 일자가 놓인 줄 — 일자 헤더에서 못 찾으면 매핑표의 "시작 + (일-1)" 로 폴백한다. */
    public int dayLine(int day) {
        Integer found = dayIndex.get(day);
        if (found != null) return found;
        return sheetMap.layout() == Layout.DAYS_IN_COLUMNS
                ? sheetMap.firstDayCol() + day - 1
                : sheetMap.firstDayRow() + day - 1;
    }

    /** 일자 헤더를 하나도 못 읽었으면 양식이 많이 달라진 것이다. */
    public boolean hasDayHeader() {
        return !dayIndex.isEmpty();
    }

    /** 헤더 글자를 못 찾아 좌표로 읽은 항목 이름들. */
    public List<String> notFound() {
        return notFound;
    }

    /** 매핑표 좌표와 다른 자리에서 찾은 항목들 — 원본 양식이 바뀌었다는 뜻. */
    public List<String> moved() {
        return moved;
    }

    private String describe(int line) {
        return sheetMap.layout() == Layout.DAYS_IN_COLUMNS ? line + "행" : columnName(line) + "열";
    }

    private static String columnName(int col) {
        StringBuilder name = new StringBuilder();
        int remaining = col;
        while (remaining > 0) {
            int digit = (remaining - 1) % 26;
            name.insert(0, (char) ('A' + digit));
            remaining = (remaining - 1) / 26;
        }
        return name.toString();
    }

    /** 1~31 이면 일자로 본다. "1일" 처럼 글자가 붙어 있어도 읽는다. */
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

    /**
     * 표기 차이를 흡수한다 — 공백·전각/반각·{@code # · - _} ·괄호를 지우고 소문자로.
     * (예: "폐#1" = "폐1", "폐 2 - 1" = "폐2-1")
     */
    private static String normalize(String text) {
        if (text == null) return "";
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = value.replaceAll("[\\s\\u00A0]+", "");
        value = value.replaceAll("[#·・\\-_()\\[\\]（）]", "");
        return value.toLowerCase(Locale.ROOT);
    }
}
