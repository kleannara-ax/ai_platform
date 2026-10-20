package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SteamUnitExcelExportService {

    private final TableService tableService;

    public SteamUnitExcelExportService(TableService tableService) {
        this.tableService = tableService;
    }

    public byte[] exportWorkbook(int year) throws IOException {
        Path templatePath = resolveTemplatePath();
        try (InputStream inputStream = Files.newInputStream(templatePath);
             HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            workbook.setForceFormulaRecalculation(true);

            for (int month = 1; month <= 12; month += 1) {
                String monthKey = String.format("%04d-%02d", year, month);
                Sheet sheet = workbook.getSheet(month + "월일지");
                if (sheet == null) {
                    continue;
                }
                fillMonthlySheet(sheet, year, month, monthKey);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void fillMonthlySheet(Sheet sheet, int year, int monthNo, String monthKey) {
        List<Map<String, Object>> unitRows = tableService.listAllByMonth("steam_unit_usage", monthKey);
        List<Map<String, Object>> rawRows = tableService.listAllByMonth("steam_unit_usage_raw", monthKey);
        List<Map<String, Object>> cellRows = tableService.listAllByMonth("steam_cell_value", monthKey);

        Map<String, Map<String, Object>> unitByKey = new HashMap<>();
        for (Map<String, Object> row : unitRows) {
            unitByKey.put(row.get("machine_no") + ":" + row.get("day"), row);
        }

        Map<String, Map<String, Object>> rawByDay = new HashMap<>();
        for (Map<String, Object> row : rawRows) {
            rawByDay.put(String.valueOf(row.get("day")), row);
        }

        Map<String, Object> cellByKey = new HashMap<>();
        for (Map<String, Object> row : cellRows) {
            cellByKey.put(cellKey(row.get("table_name"), row.get("row_key"), row.get("col_index")), row.get("cell_value"));
        }

        // 제목 칸도 번호가 아니라 글자로 찾는다 — 표 제목 / 월 누계 표 / 월 요약 표 머리글
        setLabel(sheet, TITLE_PATTERN, false, String.format("1. 호기별 생산량당 스팀사용 원단위 (%d.%d )", year, monthNo));
        setLabel(sheet, MONTH_TOTAL_PATTERN, true, monthNo + "월 누계");
        setLabel(sheet, MONTH_PATTERN, true, monthNo + "월");

        fillUnitSection(sheet, unitByKey, rawByDay);
        fillUsageSection(sheet, cellByKey);
        fillLngSection(sheet, cellByKey);
        fillFlowSection(sheet, cellByKey);
    }

    private static final java.util.regex.Pattern TITLE_PATTERN = java.util.regex.Pattern.compile("^1\\.\\s*호기별 생산량당.*");
    private static final java.util.regex.Pattern MONTH_TOTAL_PATTERN = java.util.regex.Pattern.compile("^\\d{1,2}\\s*월\\s*누계$");
    private static final java.util.regex.Pattern MONTH_PATTERN = java.util.regex.Pattern.compile("^\\d{1,2}\\s*월$");

    /** A열에서 규칙에 맞는 제목 칸을 찾아 글자를 바꾼다. 못 찾으면 그대로 둔다. */
    private void setLabel(Sheet sheet, java.util.regex.Pattern pattern, boolean unitInC, String value) {
        int row = SteamUnitSectionLayout.findLabelRow(sheet, pattern, unitInC);
        if (row >= 0) setString(sheet, row, 0, value);
    }

    private void fillUnitSection(Sheet sheet, Map<String, Map<String, Object>> unitByKey, Map<String, Map<String, Object>> rawByDay) {
        // 업로드와 같은 방식으로 머리글 줄·열·일자 줄을 제목에서 찾는다.
        HeaderColumnIndex index = SteamUnitSectionLayout.unitHeaderIndex(sheet);
        if (index == null) return;

        Map<String, Integer> pm2Cols = SteamUnitLogColumnMap.columns(index, "PM-2");
        Map<String, Integer> pm3Cols = SteamUnitLogColumnMap.columns(index, "PM-3");
        Map<String, Integer> tm3Cols = SteamUnitLogColumnMap.columns(index, "TM-3");
        Map<String, Integer> tm4Cols = SteamUnitLogColumnMap.columns(index, "TM-4");
        Map<String, Integer> tm5Cols = SteamUnitLogColumnMap.columns(index, "TM-5");
        Map<String, Integer> rawCols = SteamUnitLogColumnMap.rawColumns(index);

        for (int day = 1; day <= 31; day += 1) {
            Integer rowIndex = index.rowOfDay(day);
            if (rowIndex == null) continue;
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            clearCells(row, SteamUnitLogColumnMap.allColumns(
                    pm2Cols, pm3Cols, tm3Cols, tm4Cols, tm5Cols, rawCols));

            Map<String, Object> pm2 = unitByKey.get("PM-2:" + day);
            Map<String, Object> pm3 = unitByKey.get("PM-3:" + day);
            Map<String, Object> tm3 = unitByKey.get("TM-3:" + day);
            Map<String, Object> tm4 = unitByKey.get("TM-4:" + day);
            Map<String, Object> tm5 = unitByKey.get("TM-5:" + day);
            Map<String, Object> raw = rawByDay.get(String.valueOf(day));

            setNumberAt(row, pm2Cols, "main_steam", pm2);
            setNumberAt(row, pm2Cols, "coater_steam", pm2);
            setNumberAt(row, pm2Cols, "production", pm2);
            setStringAt(row, pm2Cols, "production_type", pm2);

            setNumberAt(row, pm3Cols, "main_steam", pm3);
            setNumberAt(row, pm3Cols, "coater_steam", pm3);
            setNumberAt(row, rawCols, "disperser_total", raw);
            setNumberAt(row, pm3Cols, "ventilation_steam", pm3);
            setNumberAt(row, pm3Cols, "production", pm3);
            setStringAt(row, pm3Cols, "production_type", pm3);

            setNumberAt(row, tm3Cols, "steam", tm3);
            setNumberAt(row, tm3Cols, "production", tm3);
            setNumberAt(row, tm4Cols, "steam", tm4);
            setNumberAt(row, tm4Cols, "production", tm4);
            setNumberAt(row, tm5Cols, "steam", tm5);
            setNumberAt(row, tm5Cols, "production", tm5);

            setNumberAt(row, rawCols, "toc_steam", raw);
            setNumberAt(row, rawCols, "pica121_vent", raw);
        }
    }

    /** 2번 호기별 가동시간당 스팀 구역 — 가져오기와 같은 제목 기반 배치로 쓴다({@link SteamUnitSectionLayout}). */
    private void fillUsageSection(Sheet sheet, Map<String, Object> cellByKey) {
        fillSection(SteamUnitSectionLayout.usage(sheet), sheet, cellByKey);
    }

    /** LNG 구역 — 가져오기와 같은 제목 기반 배치로 쓴다({@link SteamUnitLngLayout}). */
    private void fillLngSection(Sheet sheet, Map<String, Object> cellByKey) {
        SteamUnitLngLayout layout = SteamUnitLngLayout.of(sheet);
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            Row row = sheet.getRow(day.getValue());
            if (row == null) {
                continue;
            }
            String rowKey = String.format("%02d", day.getKey());
            for (String tableName : new String[]{"lng_m_boiler", "lng_m_burner", "lng_m_mix"}) {
                for (Map.Entry<Integer, Integer> column : layout.columns(tableName).entrySet()) {
                    clearCells(row, column.getValue());
                    setValue(row, column.getValue(), cellByKey.get(cellKey(tableName, rowKey, column.getKey())));
                }
            }
        }
    }

    /** 4번 유동상·소각로·외부·복합 구역 — 가져오기와 같은 제목 기반 배치로 쓴다({@link SteamUnitSectionLayout}). */
    private void fillFlowSection(Sheet sheet, Map<String, Object> cellByKey) {
        fillSection(SteamUnitSectionLayout.flow(sheet), sheet, cellByKey);
    }

    /** 구역 하나를 일자 × 항목으로 채운다. */
    private void fillSection(SteamUnitSectionLayout layout, Sheet sheet, Map<String, Object> cellByKey) {
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            Row row = sheet.getRow(day.getValue());
            if (row == null) {
                continue;
            }
            String rowKey = String.format("%02d", day.getKey());
            for (String tableName : layout.tables()) {
                for (Map.Entry<Integer, Integer> column : layout.columns(tableName).entrySet()) {
                    clearCells(row, column.getValue());
                    setValue(row, column.getValue(), cellByKey.get(cellKey(tableName, rowKey, column.getKey())));
                }
            }
        }
    }

    /** 머리글에서 찾은 열에만 쓴다 — 못 찾은 항목(null)은 건너뛴다. */
    private void setNumberAt(Row row, Map<String, Integer> columns, String key, Map<String, Object> data) {
        Integer columnIndex = columns.get(key);
        if (columnIndex == null) return;
        setNumber(row, columnIndex, data, key);
    }

    private void setStringAt(Row row, Map<String, Integer> columns, String key, Map<String, Object> data) {
        Integer columnIndex = columns.get(key);
        if (columnIndex == null) return;
        setString(row, columnIndex, data, key);
    }

    private void setNumber(Row row, int columnIndex, Map<String, Object> data, String key) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        Object value = data == null ? null : data.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(new BigDecimal(String.valueOf(value)).doubleValue());
    }

    private void setString(Row row, int columnIndex, Map<String, Object> data, String key) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        Object value = data == null ? null : data.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private void setString(Sheet sheet, int rowIndex, int columnIndex, String value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        cell.setCellValue(value);
    }

    private void setValue(Row row, int columnIndex, Object value) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }

        String text = String.valueOf(value).replace(",", "").trim();
        try {
            cell.setCellValue(new BigDecimal(text).doubleValue());
        } catch (NumberFormatException exception) {
            cell.setCellValue(text);
        }
    }

    private void clearCells(Row row, int... columnIndexes) {
        for (int columnIndex : columnIndexes) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null) {
                cell.setBlank();
            }
        }
    }

    private String cellKey(Object tableName, Object rowKey, Object colIndex) {
        return String.valueOf(tableName) + ":" + String.valueOf(rowKey) + ":" + String.valueOf(colIndex);
    }

    /**
     * 서식 파일을 찾는다.
     *
     * <p>예전에는 작업 디렉터리 바로 아래만 봤다. bootRun 은 작업 디렉터리가 {@code app/} 이라
     * 항상 못 찾고 500 이 났다. 이제 '원본' 폴더를 찾아 올라가고, 파일명의 연도는 보지 않는다.
     */
    private Path resolveTemplatePath() {
        try {
            return OriginalWorkbookFiles.find(
                    name -> name.replace(" ", "").contains("원단위일지")
                            && (name.endsWith(".xls") || name.endsWith(".xlsx")),
                    "원단위 일지");
        } catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }
}
