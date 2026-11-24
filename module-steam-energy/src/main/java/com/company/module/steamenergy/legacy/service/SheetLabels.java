package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시트에서 <b>글자로</b> 줄·칸·월을 찾는 작은 도우미 모음.
 *
 * <p>표마다 배치 클래스를 따로 두기엔 작은 표(월별 12칸, 머리글 한 줄)가 많아서, 자주 쓰는 찾기를 모았다.
 * 행·열은 모두 0-based 다. 글자 비교는 공백만 지우고 한다(괄호 안이 구분인 표가 있어서 괄호는 남긴다).
 */
final class SheetLabels {

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.KOREA);
    private static final Pattern MONTH = Pattern.compile("(\\d{1,2})\\s*월");
    static final int MAX_ROW = 300;
    static final int MAX_COL = 60;

    private SheetLabels() {
    }

    /** 칸의 보이는 글자. */
    static String text(Sheet sheet, int rowIndex, int colIndex) {
        if (sheet == null || rowIndex < 0 || colIndex < 0) return "";
        Row row = sheet.getRow(rowIndex);
        if (row == null) return "";
        return text(row.getCell(colIndex));
    }

    /**
     * 칸의 보이는 글자 — 수식 칸이면 <b>저장된 계산 결과</b>를 쓴다.
     *
     * <p>DataFormatter 는 수식 칸에서 수식 글자("B5")를 돌려준다. 원본에는 제목이 수식인 칸이 있어서
     * (사업계획대비의 "2025계획/2025실적" 은 둘째 표부터 {@code =B5}, {@code =B6}) 그대로 두면 제목을 못 찾는다.
     */
    static String text(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) return FORMATTER.formatCellValue(cell).trim();
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> FORMATTER.formatRawCellContents(cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString()).trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** 공백만 지우고 소문자로 — "구 분" 과 "구분" 을 같게 본다. */
    static String key(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[\\s\\u00A0]+", "").toLowerCase(Locale.ROOT);
    }

    static boolean is(Sheet sheet, int row, int col, String label) {
        return key(text(sheet, row, col)).equals(key(label));
    }

    /** {@code fromRow} 부터 아래로, 한 줄의 어느 칸이든 조건에 맞는 첫 줄. 없으면 -1. */
    static int findRow(Sheet sheet, int fromRow, Predicate<String> match) {
        for (int row = Math.max(fromRow, 0); row <= MAX_ROW; row += 1) {
            for (int col = 0; col < MAX_COL; col += 1) {
                if (match.test(key(text(sheet, row, col)))) return row;
            }
        }
        return -1;
    }

    /** {@code fromRow} 부터 아래로, 정해진 열이 조건에 맞는 첫 줄. 없으면 -1. */
    static int findRowInColumn(Sheet sheet, int col, int fromRow, int toRow, Predicate<String> match) {
        for (int row = Math.max(fromRow, 0); row <= Math.min(toRow, MAX_ROW); row += 1) {
            if (match.test(key(text(sheet, row, col)))) return row;
        }
        return -1;
    }

    /** 한 줄에서 {@code fromCol} 이상, 글자가 정확히 맞는 첫 칸. 없으면 null. */
    static Integer column(Sheet sheet, int row, String label, int fromCol) {
        String wanted = key(label);
        for (int col = Math.max(fromCol, 0); col < MAX_COL; col += 1) {
            if (key(text(sheet, row, col)).equals(wanted)) return col;
        }
        return null;
    }

    /** 한 줄에서 {@code fromCol} 이상, 글자가 {@code prefix} 로 시작하는 첫 칸. 없으면 null. */
    static Integer columnStartingWith(Sheet sheet, int row, String prefix, int fromCol) {
        String wanted = key(prefix);
        for (int col = Math.max(fromCol, 0); col < MAX_COL; col += 1) {
            if (key(text(sheet, row, col)).startsWith(wanted)) return col;
        }
        return null;
    }

    /** 주어진 글자가 모두 들어 있는 첫 줄. 없으면 -1. */
    static int findRowWithAll(Sheet sheet, int fromRow, String... labels) {
        for (int row = Math.max(fromRow, 0); row <= MAX_ROW; row += 1) {
            boolean all = true;
            for (String label : labels) {
                if (column(sheet, row, label, 0) == null) { all = false; break; }
            }
            if (all) return row;
        }
        return -1;
    }

    /** "1월"~"12월" 이 세로로 6개 이상 적힌 열 — 월별 표의 월 칸. 없으면 -1. */
    static int monthLabelColumn(Sheet sheet, int fromRow, int toRow) {
        for (int col = 0; col < MAX_COL; col += 1) {
            if (monthRows(sheet, col, fromRow, toRow, null).size() >= 6) return col;
        }
        return -1;
    }

    /** 한 줄의 "1월"…"12월" 칸 — 월 → 열. */
    static Map<Integer, Integer> monthColumns(Sheet sheet, int row) {
        Map<Integer, Integer> columns = new LinkedHashMap<>();
        for (int col = 0; col < MAX_COL; col += 1) {
            Integer month = monthOf(text(sheet, row, col));
            if (month != null) columns.putIfAbsent(month, col);
        }
        return columns;
    }

    /** 한 열의 "1월"…"12월" 줄 — 월 → 줄. {@code stop} 에 맞는 글자를 만나면 멈춘다. */
    static Map<Integer, Integer> monthRows(Sheet sheet, int col, int fromRow, int toRow, Predicate<String> stop) {
        Map<Integer, Integer> rows = new LinkedHashMap<>();
        for (int row = Math.max(fromRow, 0); row <= Math.min(toRow, MAX_ROW); row += 1) {
            String value = text(sheet, row, col);
            if (stop != null && stop.test(key(value))) break;
            Integer month = monthOf(value);
            if (month != null) rows.putIfAbsent(month, row);
        }
        return rows;
    }

    /** "1월", "`25년 10월" 처럼 끝이 "N월" 인 글자에서 월을 읽는다. */
    static Integer monthOf(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = MONTH.matcher(value);
        Integer month = null;
        while (matcher.find()) month = Integer.parseInt(matcher.group(1));
        return (month != null && month >= 1 && month <= 12) ? month : null;
    }

    /** 월 머리글이 6개 이상 늘어선 줄 — "`25년 10월" 같은 한두 칸짜리 줄은 거른다. */
    static boolean isMonthHeader(Sheet sheet, int row) {
        return monthColumns(sheet, row).size() >= 6;
    }
}
