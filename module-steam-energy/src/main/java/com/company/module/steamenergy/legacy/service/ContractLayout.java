package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 소각로 "도급내역" 시트의 배치 — <b>머리글 글자로</b> 열을, <b>일별 칸으로</b> 행을 찾는다.
 *
 * <p>이 시트는 같은 모양의 표가 <b>좌우 두 벌</b> 놓여 있다.
 * 왼쪽이 도급(계약) 기준, 오른쪽이 실 스팀량 기준이고, 각각 앞에 "일별" 칸이 있다.
 *
 * <pre>
 *  r3                 16:스팀량 합계   17:총 운영비용        26:스팀량 합계   27:총 운영비용
 *  r4   2:1호기        9:2호기                    20:1호기        23:2호기
 *  r5   2:스팀량 3:운영단가 4:운영비용 5:운휴시간 6:고정비 7:운영비용 8:1호기 소계 …
 *  r6+  1:1일 …                                  19:1일 …
 * </pre>
 *
 * <p>같은 호기 안에 <b>"운영비용(원)" 이 두 번</b> 나온다(가동분·운휴분). 그래서 항목을
 * 왼쪽에서 오른쪽 순서대로, 직전에 찾은 열 다음부터 훑는다({@link HeaderColumnIndex#columnAfter}).
 *
 * <p>머리글이나 일별 칸을 못 찾으면 그 항목·그 날은 <b>건너뛴다</b>. 열 번호로 폴백하면
 * 값이 다른 항목에 붙기 때문이다.
 */
final class ContractLayout {


    /** 머리글 줄(0-based) — 3행 합계류 / 4행 호기 / 5행 항목 */
    private static final int TOP_ROW = 3;
    private static final int GROUP_ROW = 4;
    private static final int ITEM_ROW = 5;
    private static final int FIRST_DATA_ROW = 6;
    private static final int LAST_DATA_ROW = 40;
    private static final String DAY_HEADER = "일별";

    /** 1·2호기 항목 순서 — UNIT_FIELDS 와 같은 순서다. */
    private static final String[][] UNIT_HEADERS = {
            {"steam", "스팀량"},          // 1호기 "스팀량(톤)" / 2호기 "스팀량(톤/일)"
            {"unit", "운영단가"},
            {"oper", "운영비용"},          // 가동분
            {"runtime", "운휴시간"},
            {"fixed", "운휴시간당 고정비"},
            {"stop", "운영비용"},          // 운휴분 — 같은 이름이라 순서대로 찾는다
            {"sub", "소계"}                // "1호기 소계(원)" / "2호기 소계(원)"
    };

    /** 실 스팀량 표의 항목 순서 — REAL_FIELDS 와 같은 순서다. */
    private static final String[][] REAL_HEADERS = {
            {"steam-1", "스팀량"},
            {"unit-1", "운영단가"},
            {"oper-1", "운영비용"},
            {"steam-2", "스팀량"},
            {"unit-2", "운영단가"},
            {"oper-2", "운영비용"}
    };

    private final Map<String, Map<String, Integer>> columnsBySection = new LinkedHashMap<>();
    private final Map<String, Map<Integer, Integer>> dayRowsBySection = new LinkedHashMap<>();

    private ContractLayout() {
    }

    static ContractLayout of(Sheet sheet) {
        ContractLayout layout = new ContractLayout();

        // "일별" 칸이 표 하나에 하나씩 있다 — 왼쪽이 도급, 오른쪽이 실 스팀량.
        int leftDayCol = findDayColumn(sheet, 0);
        int rightDayCol = leftDayCol < 0 ? -1 : findDayColumn(sheet, leftDayCol + 1);

        if (leftDayCol >= 0) {
            int lastCol = rightDayCol > 0 ? rightDayCol - 1 : lastColumn(sheet);
            HeaderColumnIndex index = HeaderColumnIndex.build(sheet, TOP_ROW, GROUP_ROW, ITEM_ROW,
                    FIRST_DATA_ROW, LAST_DATA_ROW, leftDayCol, leftDayCol + 1, lastCol);
            layout.columnsBySection.put("unit1", ordered(index, "1호기", UNIT_HEADERS, leftDayCol + 1));
            layout.columnsBySection.put("unit2", ordered(index, "2호기", UNIT_HEADERS, leftDayCol + 1));
            Map<Integer, Integer> rows = dayRows(sheet, leftDayCol);
            layout.dayRowsBySection.put("unit1", rows);
            layout.dayRowsBySection.put("unit2", rows);
        }

        if (rightDayCol > 0) {
            HeaderColumnIndex index = HeaderColumnIndex.build(sheet, TOP_ROW, GROUP_ROW, ITEM_ROW,
                    FIRST_DATA_ROW, LAST_DATA_ROW, rightDayCol, rightDayCol + 1, lastColumn(sheet));

            Map<String, Integer> columns = new LinkedHashMap<>();
            int cursor = rightDayCol + 1;
            for (String[] header : REAL_HEADERS) {
                String group = header[0].endsWith("-2") ? "2호기" : "1호기";
                Integer col = index.columnAfter(cursor, group, header[1]);
                if (col == null) continue;
                columns.put(header[0], col);
                cursor = col + 1;
            }
            // 합계 두 칸은 호기 밑이 아니라 윗줄에만 이름이 있다
            Integer steamSum = index.columnByTopLabel("스팀량 합계");
            if (steamSum != null) columns.put("steam-sum", steamSum);
            Integer totalCost = index.columnByTopLabel("총 운영비용");
            if (totalCost != null) columns.put("total-cost", totalCost);

            layout.columnsBySection.put("real", columns);
            layout.dayRowsBySection.put("real", dayRows(sheet, rightDayCol));
        }
        return layout;
    }

    /** 항목을 왼쪽에서 오른쪽 순서대로 찾는다 — 같은 이름이 두 번 나오는 경우 대비. */
    private static Map<String, Integer> ordered(HeaderColumnIndex index, String group,
                                                String[][] headers, int firstCol) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        int cursor = firstCol;
        for (String[] header : headers) {
            String item = "sub".equals(header[0]) ? group + " " + header[1] : header[1];
            Integer col = index.columnAfter(cursor, group, item);
            if (col == null) continue;
            columns.put(header[0], col);
            cursor = col + 1;
        }
        return columns;
    }

    Map<String, Integer> columns(String section) {
        return columnsBySection.getOrDefault(section, Map.of());
    }

    Map<Integer, Integer> dayRows(String section) {
        return dayRowsBySection.getOrDefault(section, Map.of());
    }

    private static int findDayColumn(Sheet sheet, int fromCol) {
        for (int row = 0; row <= GROUP_ROW; row += 1) {
            for (int col = fromCol; col <= lastColumn(sheet); col += 1) {
                if (DAY_HEADER.equals(text(sheet, row, col).replaceAll("\\s+", ""))) return col;
            }
        }
        return -1;
    }

    /** 일별 칸("1일" ~ "31일")에서 날짜 → 행. */
    private static Map<Integer, Integer> dayRows(Sheet sheet, int dayCol) {
        Map<Integer, Integer> rows = new LinkedHashMap<>();
        for (int row = FIRST_DATA_ROW; row <= LAST_DATA_ROW; row += 1) {
            String value = text(sheet, row, dayCol).replaceAll("[^0-9].*$", "");
            if (value.isEmpty()) continue;
            try {
                int day = Integer.parseInt(value);
                if (day >= 1 && day <= 31) rows.putIfAbsent(day, row);
            } catch (NumberFormatException ignored) {
                // 날짜가 아닌 줄
            }
        }
        return rows;
    }

    private static int lastColumn(Sheet sheet) {
        int max = 0;
        for (int row = TOP_ROW; row <= ITEM_ROW; row += 1) {
            Row line = sheet.getRow(row);
            if (line != null) max = Math.max(max, line.getLastCellNum());
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
}
