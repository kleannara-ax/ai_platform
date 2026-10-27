package com.company.module.personalinfo.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엑셀 읽기 공통 유틸 (병합 셀 값, 날짜 문자열 파싱).
 */
final class PiExcelSupport {

    private static final DataFormatter FORMATTER = new DataFormatter();
    /** 20xx.MM.dd / yy.MM.dd / yyyy-MM-dd / yyyy/MM/dd (점 뒤 공백 허용) */
    private static final Pattern DATE_TOKEN =
            Pattern.compile("(\\d{4}|\\d{2})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})");

    private PiExcelSupport() {
    }

    /** 병합 영역이면 좌상단 셀 값을 돌려준다. 값이 없으면 빈 문자열 */
    static String text(Sheet sheet, int rowIdx, int colIdx) {
        Cell cell = mergedTopLeft(sheet, rowIdx, colIdx);
        return cellText(cell);
    }

    /** 병합 영역을 따르지 않고 해당 셀만 읽는다 */
    static String rawText(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        return row == null ? "" : cellText(row.getCell(colIdx));
    }

    static LocalDate date(Sheet sheet, int rowIdx, int colIdx) {
        Cell cell = mergedTopLeft(sheet, rowIdx, colIdx);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return parseFirstDate(cellText(cell));
    }

    static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                return switch (cell.getCachedFormulaResultType()) {
                    case NUMERIC -> FORMATTER.formatRawCellContents(cell.getNumericCellValue(), -1, "General").trim();
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    case STRING -> cell.getStringCellValue().trim();
                    default -> "";
                };
            }
            return FORMATTER.formatCellValue(cell).trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static Cell mergedTopLeft(Sheet sheet, int rowIdx, int colIdx) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIdx, colIdx)) {
                Row top = sheet.getRow(region.getFirstRow());
                return top == null ? null : top.getCell(region.getFirstColumn());
            }
        }
        Row row = sheet.getRow(rowIdx);
        return row == null ? null : row.getCell(colIdx);
    }

    /** 문자열에서 첫 번째 날짜(연.월.일)를 찾는다. 2자리 연도는 2000년대로 본다. */
    static LocalDate parseFirstDate(String text) {
        List<LocalDate> dates = parseDates(text);
        return dates.isEmpty() ? null : dates.get(0);
    }

    static List<LocalDate> parseDates(String text) {
        List<LocalDate> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        Matcher m = DATE_TOKEN.matcher(text);
        while (m.find()) {
            try {
                int year = Integer.parseInt(m.group(1));
                if (year < 100) {
                    year += 2000;
                }
                result.add(LocalDate.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
            } catch (RuntimeException ignore) {
                // 2026.13.40 같은 잘못된 날짜는 건너뛴다
            }
        }
        return result;
    }

    /** 공백/줄바꿈을 제거한 비교용 문자열 */
    static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }
}
