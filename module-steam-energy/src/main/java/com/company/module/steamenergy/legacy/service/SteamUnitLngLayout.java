package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 원단위 일지 "3. 일별 LNG 연료 사용 비용" 구역의 배치 — <b>항목 제목으로</b> 열을, <b>일자 칸으로</b> 행을 찾는다.
 *
 * <p>예전에는 81~111행, B·C·D·E·G / I~L·O / T·U·W·X·Y 열을 코드에 박아 뒀다. 2026년 9월에 보일러 쪽에
 * "복합보일러", "신설소각로" 두 열이 끼어들면서 그 오른쪽이 전부 두 칸씩 밀렸다.
 *
 * <pre>
 *  옛 배치 : 20톤-1 20톤-2 50톤 60톤 | 합계 단가 비용 | PM-3 TM-3 TM-4 TM-5 … 단가 비용 총비용 | PM-2 PM-3 …
 *  새 배치 : 20톤-1 20톤-2 50톤 60톤 복합보일러 신설소각로 | 합계 단가 비용 | PM-3 …          | PM-2 PM-3 …
 * </pre>
 *
 * <p>두 배치를 다 읽어야 한다 — 지난달 파일을 다시 올리는 경우가 있기 때문이다. 그래서 79행 제목을
 * <b>왼쪽부터 순서대로</b> 훑는다. "단가", "비용", "PM-3" 처럼 같은 이름이 보일러·버너·복합 표에 두 번
 * 이상 나오므로, 직전에 찾은 칸 다음부터 찾아야 표가 섞이지 않는다.
 *
 * <p>복합보일러·신설소각로는 <b>있으면 읽는다</b>(옛 파일에는 없다). 없을 때 다른 칸을 잘못 집지 않도록
 * 바로 뒤의 필수 제목("LNG 사용합계") 앞에서만 찾는다.
 *
 * <p>제목에 괄호가 들어 있어(LNG (20톤-1) …) 공용 색인처럼 괄호를 지우면 네 칸이 모두 "LNG" 가 된다.
 * 그래서 공백만 지우고 비교한다.
 *
 * <p>행·열은 모두 0-based 다.
 */
final class SteamUnitLngLayout {

    private static final int SEARCH_FIRST_ROW = 60;
    private static final int SEARCH_LAST_ROW = 160;
    private static final int MAX_COL = 60;
    private static final String HEADER_ANCHOR = "LNG (20톤-1)";

    /**
     * 표 한 벌의 항목 — {@code {저장 col_index, 제목}}. 저장 번호가 -1 이면 자리만 짚고 넘어간다.
     * 제목 앞에 "?" 가 붙으면 없어도 되는 칸이다.
     */
    private static final String[][] BOILER = {
            {"0", "LNG (20톤-1)"}, {"1", "LNG (20톤-2)"}, {"2", "LNG (50톤)"}, {"3", "LNG (60톤)"},
            {"7", "?복합보일러"}, {"8", "?신설소각로"},
            {"-1", "LNG 사용합계"}, {"5", "단가"}, {"-1", "비용"}
    };
    private static final String[][] BURNER = {
            {"0", "PM-3"}, {"1", "TM-3"}, {"2", "TM-4"}, {"3", "TM-5"},
            {"-1", "화장지 계"}, {"-1", "제지+화장지 합계"}, {"6", "단가"}, {"-1", "비용"}, {"-1", "총비용"}
    };
    private static final String[][] MIX = {
            {"0", "PM-2"}, {"1", "PM-3"}, {"-1", "PM합계"}, {"3", "TM-3"}, {"4", "TM-4"}, {"5", "TM-5"}
    };

    private final Map<String, Map<Integer, Integer>> columnsByTable = new LinkedHashMap<>();
    private final Map<Integer, Integer> dayRows = new LinkedHashMap<>();
    private final List<String> notFound = new ArrayList<>();
    private int headerRow = -1;

    private SteamUnitLngLayout() {
    }

    static SteamUnitLngLayout of(Sheet sheet) {
        SteamUnitLngLayout layout = new SteamUnitLngLayout();
        layout.headerRow = findHeaderRow(sheet);
        if (layout.headerRow < 0) {
            layout.notFound.add("LNG 구역 제목 줄('" + HEADER_ANCHOR + "')");
            return layout;
        }
        List<String> headers = new ArrayList<>();
        for (int col = 0; col < MAX_COL; col += 1) headers.add(normalize(text(sheet, layout.headerRow, col)));

        int cursor = 0;
        cursor = layout.readTable(headers, "lng_m_boiler", BOILER, cursor);
        cursor = layout.readTable(headers, "lng_m_burner", BURNER, cursor);
        layout.readTable(headers, "lng_m_mix", MIX, cursor);

        // 일자: 제목 줄 아래 A열의 1~31. "누계" 를 만나면 끝이다.
        for (int row = layout.headerRow + 1; row <= layout.headerRow + 40; row += 1) {
            if (normalize(text(sheet, row, 0)).equals(normalize("누계"))) break;
            Integer day = dayOf(sheet, row);
            if (day != null) layout.dayRows.putIfAbsent(day, row);
        }
        if (layout.dayRows.isEmpty()) layout.notFound.add("LNG 구역 일자 칸");
        return layout;
    }

    /** 표 한 벌을 왼쪽부터 훑는다. 다음 표는 돌려준 자리부터 찾는다. */
    private int readTable(List<String> headers, String tableName, String[][] fields, int cursor) {
        Map<Integer, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 1) {
            int colIndex = Integer.parseInt(fields[i][0]);
            boolean optional = fields[i][1].startsWith("?");
            String label = normalize(optional ? fields[i][1].substring(1) : fields[i][1]);

            // 없어도 되는 칸은 바로 뒤 필수 칸 앞에서만 찾는다 — 엉뚱한 칸을 집지 않게
            int limit = headers.size();
            if (optional) {
                for (int j = i + 1; j < fields.length; j += 1) {
                    if (fields[j][1].startsWith("?")) continue;
                    int next = indexOf(headers, normalize(fields[j][1]), cursor, headers.size());
                    if (next >= 0) limit = next;
                    break;
                }
            }
            int found = indexOf(headers, label, cursor, limit);
            if (found < 0) {
                if (!optional) notFound.add(tableName + " '" + fields[i][1] + "'");
                continue;
            }
            if (colIndex >= 0) columns.put(colIndex, found);
            cursor = found + 1;
        }
        columnsByTable.put(tableName, columns);
        return cursor;
    }

    /** 표 이름 → {저장 col_index → 시트 열}. 제목을 못 찾은 칸은 들어 있지 않다. */
    Map<Integer, Integer> columns(String tableName) {
        return columnsByTable.getOrDefault(tableName, Map.of());
    }

    /** 일(1~31) → 시트 행. */
    Map<Integer, Integer> dayRows() {
        return dayRows;
    }

    int headerRow() {
        return headerRow;
    }

    /** 제목을 못 찾아 건너뛴 칸. */
    List<String> notFound() {
        return notFound;
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    private static int findHeaderRow(Sheet sheet) {
        String anchor = normalize(HEADER_ANCHOR);
        for (int row = SEARCH_FIRST_ROW; row <= SEARCH_LAST_ROW; row += 1) {
            for (int col = 0; col < MAX_COL; col += 1) {
                if (normalize(text(sheet, row, col)).equals(anchor)) return row;
            }
        }
        return -1;
    }

    private static int indexOf(List<String> headers, String label, int from, int until) {
        for (int col = from; col < Math.min(until, headers.size()); col += 1) {
            if (headers.get(col).equals(label)) return col;
        }
        return -1;
    }

    /** A열 일자. 가져오기의 readDay 와 같게 — 숫자 칸만 보고 수식 칸은 건너뛴다. */
    private static Integer dayOf(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(0);
        if (cell == null || cell.getCellType() != CellType.NUMERIC) return null;
        double value = cell.getNumericCellValue();
        int day = (int) value;
        return (day >= 1 && day <= 31 && day == value) ? day : null;
    }

    private static String text(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        return SheetLabels.text(cell);   // 수식 칸이면 저장된 계산 결과
    }

    /** 공백만 지운다 — 괄호 안(20톤-1, 50톤 …)이 곧 구분이라 남겨야 한다. */
    private static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return text.replaceAll("[\\s\\u00A0]+", "").toLowerCase(Locale.ROOT);
    }
}
