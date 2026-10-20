package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SteamUnitExcelImportService {
    private static final Pattern MONTHLY_SHEET_PATTERN = Pattern.compile("^(\\d{1,2})월일지$");

    private final TableService tableService;
    private final DataFormatter dataFormatter = new DataFormatter();

    public SteamUnitExcelImportService(TableService tableService) {
        this.tableService = tableService;
    }

    public Map<String, Object> importWorkbook(MultipartFile file, int year) throws IOException {
        List<String> importedSheets = new ArrayList<>();
        int unitRowCount = 0;
        int rawRowCount = 0;
        int cellRowCount = 0;

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (int index = 0; index < workbook.getNumberOfSheets(); index += 1) {
                Sheet sheet = workbook.getSheetAt(index);
                Matcher matcher = MONTHLY_SHEET_PATTERN.matcher(sheet.getSheetName());
                if (!matcher.matches()) {
                    continue;
                }

                int monthNo = Integer.parseInt(matcher.group(1));
                String monthKey = String.format("%04d-%02d", year, monthNo);
                clearImportedMonth(monthKey);

                // 열은 머리글(설비 + 항목) 로, 머리글 줄과 일자 줄은 "일자"·"누계" 칸으로 찾는다 —
                // 번호를 박아 두면 원본에 줄·열이 하나만 추가돼도 값이 전부 다른 항목으로 저장된다.
                HeaderColumnIndex headerIndex = SteamUnitSectionLayout.unitHeaderIndex(sheet);

                if (headerIndex != null) {
                    Map<String, Integer> pm2Cols = SteamUnitLogColumnMap.columns(headerIndex, "PM-2");
                    Map<String, Integer> pm3Cols = SteamUnitLogColumnMap.columns(headerIndex, "PM-3");
                    Map<String, Integer> tm3Cols = SteamUnitLogColumnMap.columns(headerIndex, "TM-3");
                    Map<String, Integer> tm4Cols = SteamUnitLogColumnMap.columns(headerIndex, "TM-4");
                    Map<String, Integer> tm5Cols = SteamUnitLogColumnMap.columns(headerIndex, "TM-5");
                    Map<String, Integer> rawCols = SteamUnitLogColumnMap.rawColumns(headerIndex);

                    for (int day = 1; day <= 31; day += 1) {
                        Integer rowIndex = headerIndex.rowOfDay(day);
                        if (rowIndex == null) continue;
                        Row row = sheet.getRow(rowIndex);
                        if (row == null) continue;

                        if (upsertUnitUsage(monthKey, year, monthNo, day, "PM-2", row, pm2Cols)) unitRowCount += 1;
                        if (upsertUnitUsage(monthKey, year, monthNo, day, "PM-3", row, pm3Cols)) unitRowCount += 1;
                        if (upsertUnitUsage(monthKey, year, monthNo, day, "TM-3", row, tm3Cols)) unitRowCount += 1;
                        if (upsertUnitUsage(monthKey, year, monthNo, day, "TM-4", row, tm4Cols)) unitRowCount += 1;
                        if (upsertUnitUsage(monthKey, year, monthNo, day, "TM-5", row, tm5Cols)) unitRowCount += 1;
                        if (upsertUnitRaw(monthKey, year, monthNo, day, row, rawCols)) rawRowCount += 1;
                    }
                }

                cellRowCount += importUsageSection(sheet, monthKey, year, monthNo);
                cellRowCount += importLngSection(sheet, monthKey, year, monthNo);
                cellRowCount += importFlowSection(sheet, monthKey, year, monthNo);

                importedSheets.add(sheet.getSheetName());
            }
        }

        return Map.of(
                "year", year,
                "sheets", importedSheets,
                "unit_rows", unitRowCount,
                "raw_rows", rawRowCount,
                "cell_rows", cellRowCount
        );
    }

    private void clearImportedMonth(String monthKey) {
        tableService.deleteRowsByMonth("unit_usage", monthKey);
        tableService.deleteRowsByMonth("unit_usage_raw", monthKey);
        tableService.deleteTableCellValuesByTables(monthKey, List.of(
                "usage_m_pm2",
                "usage_m_pm3a",
                "usage_m_pm3b",
                "usage_m_tm",
                "usage_m_tissue",
                "lng_m_boiler",
                "lng_m_burner",
                "lng_m_mix",
                "flow_m_fluid_incinerator",
                "flow_m_combined",
                "flow_m_boiler"
        ));
    }

    /**
     * 2번 호기별 가동시간당 스팀 구역 — 좌표가 아니라 설비·항목 제목으로 칸을 찾는다({@link SteamUnitSectionLayout}).
     */
    private int importUsageSection(Sheet sheet, String monthKey, int year, int monthNo) {
        return importSection(SteamUnitSectionLayout.usage(sheet), sheet, monthKey, year, monthNo);
    }

    /**
     * LNG 구역 — 좌표가 아니라 79행 제목으로 칸을 찾는다({@link SteamUnitLngLayout}).
     * 복합보일러·신설소각로 열이 있는 새 양식이면 lng_m_boiler 7·8번에 넣고, 없는 옛 양식이면 건너뛴다.
     */
    private int importLngSection(Sheet sheet, String monthKey, int year, int monthNo) {
        int imported = 0;
        SteamUnitLngLayout layout = SteamUnitLngLayout.of(sheet);
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            Row row = sheet.getRow(day.getValue());
            String rowKey = String.format("%02d", day.getKey());
            for (String tableName : LNG_TABLES) {
                for (Map.Entry<Integer, Integer> column : layout.columns(tableName).entrySet()) {
                    imported += upsertCell(monthKey, year, monthNo, tableName, rowKey, column.getKey(), getCell(row, column.getValue()));
                }
            }
        }
        return imported;
    }

    private static final List<String> LNG_TABLES = List.of("lng_m_boiler", "lng_m_burner", "lng_m_mix");

    /**
     * 4번 유동상·소각로·외부·복합 구역 — 좌표가 아니라 설비·항목 제목으로 칸을 찾는다({@link SteamUnitSectionLayout}).
     */
    private int importFlowSection(Sheet sheet, String monthKey, int year, int monthNo) {
        return importSection(SteamUnitSectionLayout.flow(sheet), sheet, monthKey, year, monthNo);
    }

    /** 구역 하나를 일자 × 항목으로 읽는다. 제목을 못 찾은 칸·일자는 건너뛴다. */
    private int importSection(SteamUnitSectionLayout layout, Sheet sheet, String monthKey, int year, int monthNo) {
        int imported = 0;
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            Row row = sheet.getRow(day.getValue());
            String rowKey = String.format("%02d", day.getKey());
            for (String tableName : layout.tables()) {
                for (Map.Entry<Integer, Integer> column : layout.columns(tableName).entrySet()) {
                    imported += upsertCell(monthKey, year, monthNo, tableName, rowKey, column.getKey(), getCell(row, column.getValue()));
                }
            }
        }
        return imported;
    }

    private boolean upsertUnitUsage(
            String monthKey,
            int year,
            int monthNo,
            int day,
            String machineNo,
            Row row,
            Map<String, Integer> columnMap
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", monthKey);
        payload.put("year_no", year);
        payload.put("month_no", monthNo);
        payload.put("day", Integer.toString(day));
        payload.put("machine_no", machineNo);

        boolean hasImportField = false;
        for (Map.Entry<String, Integer> entry : columnMap.entrySet()) {
            String field = entry.getKey();
            Cell cell = getCell(row, entry.getValue());
            if (isFormulaCell(cell)) {
                continue;
            }
            Object value = field.equals("production_type") ? readString(cell) : readNumber(cell);
            if (value == null) {
                continue;
            }
            hasImportField = true;
            payload.put(field, value);
        }

        if (!hasImportField) {
            return false;
        }

        tableService.upsert("unit_usage", payload);
        return true;
    }

    private boolean upsertUnitRaw(
            String monthKey,
            int year,
            int monthNo,
            int day,
            Row row,
            Map<String, Integer> columnMap
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", monthKey);
        payload.put("year_no", year);
        payload.put("month_no", monthNo);
        payload.put("day", Integer.toString(day));

        boolean hasImportField = false;
        for (Map.Entry<String, Integer> entry : columnMap.entrySet()) {
            Cell cell = getCell(row, entry.getValue());
            // disperser_total(col 9) 등은 원본 일지에서 수식 셀이며 캐시된 결과값이 유일한 데이터원이다.
            // (에너지 페이지처럼 수기입력 대안이 없으므로) 수식 셀의 캐시 numeric 값을 그대로 읽는다.
            BigDecimal value = readNumberAllowFormula(cell);
            if (value == null) {
                continue;
            }
            hasImportField = true;
            payload.put(entry.getKey(), value);
        }

        if (!hasImportField) {
            return false;
        }

        tableService.upsert("unit_usage_raw", payload);
        return true;
    }

    private int upsertCell(
            String monthKey,
            int year,
            int monthNo,
            String tableName,
            String rowKey,
            int colIndex,
            Cell cell
    ) {
        if (isFormulaCell(cell)) {
            return 0;
        }

        String value = readCellValue(cell);
        if (value == null) {
            return 0;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", monthKey);
        payload.put("year_no", year);
        payload.put("month_no", monthNo);
        payload.put("table_name", tableName);
        payload.put("row_key", rowKey);
        payload.put("col_index", colIndex);
        payload.put("cell_value", value);
        tableService.upsert("table_cell_value", payload);
        return 1;
    }


    private Cell getCell(Row row, int columnIndex) {
        return row == null ? null : row.getCell(columnIndex);
    }

    private boolean isFormulaCell(Cell cell) {
        return cell != null && cell.getCellType() == CellType.FORMULA;
    }

    /**
     * readNumber 와 동일하되, 수식(FORMULA) 셀이면 캐시된 결과값을 읽는다.
     * 캐시 결과가 NUMERIC 이면 전체 정밀도의 숫자를, 그 외(문자/오류)면 무시(null).
     */
    private BigDecimal readNumberAllowFormula(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
            }
            return null;
        }
        return readNumber(cell);
    }

    private BigDecimal readNumber(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }

        String text = dataFormatter.formatCellValue(cell).replace(",", "").trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("-".equals(text)) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String readString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        String text = dataFormatter.formatCellValue(cell).trim();
        return text.isEmpty() ? null : text;
    }

    private String readCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        String text = dataFormatter.formatCellValue(cell).trim();
        if ("-".equals(text)) {
            return "0";
        }
        return text.isEmpty() ? null : text;
    }
}
