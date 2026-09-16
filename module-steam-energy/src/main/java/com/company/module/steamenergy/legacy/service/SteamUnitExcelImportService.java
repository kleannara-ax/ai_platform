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
    private static final int DAILY_START_ROW = 5;
    private static final int DAILY_END_ROW = 35;

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

                for (int rowIndex = DAILY_START_ROW; rowIndex <= DAILY_END_ROW; rowIndex += 1) {
                    Row row = sheet.getRow(rowIndex);
                    Integer day = readDay(row);
                    if (day == null) {
                        continue;
                    }

                    if (upsertUnitUsage(monthKey, year, monthNo, day, "PM-2", row, Map.of(
                            "main_steam", 1,
                            "coater_steam", 2,
                            "production", 4,
                            "production_type", 6
                    ))) {
                        unitRowCount += 1;
                    }

                    if (upsertUnitUsage(monthKey, year, monthNo, day, "PM-3", row, Map.of(
                            "main_steam", 7,
                            "coater_steam", 8,
                            "ventilation_steam", 10,
                            "production", 12,
                            "production_type", 14
                    ))) {
                        unitRowCount += 1;
                    }

                    if (upsertUnitUsage(monthKey, year, monthNo, day, "TM-3", row, Map.of(
                            "steam", 18,
                            "production", 19
                    ))) {
                        unitRowCount += 1;
                    }

                    if (upsertUnitUsage(monthKey, year, monthNo, day, "TM-4", row, Map.of(
                            "steam", 21,
                            "production", 22
                    ))) {
                        unitRowCount += 1;
                    }

                    if (upsertUnitUsage(monthKey, year, monthNo, day, "TM-5", row, Map.of(
                            "steam", 24,
                            "production", 25
                    ))) {
                        unitRowCount += 1;
                    }

                    if (upsertUnitRaw(monthKey, year, monthNo, day, row, Map.of(
                            "disperser_total", 9,
                            "toc_steam", 34,
                            "pica121_vent", 35
                    ))) {
                        rawRowCount += 1;
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

    private int importUsageSection(Sheet sheet, String monthKey, int year, int monthNo) {
        int imported = 0;
        for (int rowIndex = 42; rowIndex <= 72; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            Integer day = readDay(row);
            if (day == null) {
                continue;
            }
            String rowKey = String.format("%02d", day);

            imported += upsertCell(monthKey, year, monthNo, "usage_m_pm2", rowKey, 1, getCell(row, 2));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_pm2", rowKey, 3, getCell(row, 4));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_pm3a", rowKey, 1, getCell(row, 6));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_pm3a", rowKey, 3, getCell(row, 8));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_pm3b", rowKey, 1, getCell(row, 10));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_pm3b", rowKey, 5, getCell(row, 13));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_tm", rowKey, 1, getCell(row, 19));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_tm", rowKey, 4, getCell(row, 22));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_tm", rowKey, 7, getCell(row, 25));
            imported += upsertCell(monthKey, year, monthNo, "usage_m_tissue", rowKey, 1, getCell(row, 28));
        }
        return imported;
    }

    private int importLngSection(Sheet sheet, String monthKey, int year, int monthNo) {
        int imported = 0;
        for (int rowIndex = 80; rowIndex <= 110; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            Integer day = readDay(row);
            if (day == null) {
                continue;
            }
            String rowKey = String.format("%02d", day);

            imported += upsertCell(monthKey, year, monthNo, "lng_m_boiler", rowKey, 0, getCell(row, 1));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_boiler", rowKey, 1, getCell(row, 2));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_boiler", rowKey, 2, getCell(row, 3));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_boiler", rowKey, 3, getCell(row, 4));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_boiler", rowKey, 5, getCell(row, 6));

            imported += upsertCell(monthKey, year, monthNo, "lng_m_burner", rowKey, 0, getCell(row, 8));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_burner", rowKey, 1, getCell(row, 9));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_burner", rowKey, 2, getCell(row, 10));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_burner", rowKey, 3, getCell(row, 11));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_burner", rowKey, 6, getCell(row, 14));

            imported += upsertCell(monthKey, year, monthNo, "lng_m_mix", rowKey, 0, getCell(row, 19));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_mix", rowKey, 1, getCell(row, 20));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_mix", rowKey, 3, getCell(row, 22));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_mix", rowKey, 4, getCell(row, 23));
            imported += upsertCell(monthKey, year, monthNo, "lng_m_mix", rowKey, 5, getCell(row, 24));
        }
        return imported;
    }

    private int importFlowSection(Sheet sheet, String monthKey, int year, int monthNo) {
        int imported = 0;
        for (int rowIndex = 119; rowIndex <= 149; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            Integer day = readDay(row);
            if (day == null) {
                continue;
            }
            String rowKey = String.format("%02d", day);

            imported += upsertCell(monthKey, year, monthNo, "flow_m_fluid_incinerator", rowKey, 0, getCell(row, 1));
            imported += upsertCell(monthKey, year, monthNo, "flow_m_fluid_incinerator", rowKey, 1, getCell(row, 2));
            imported += upsertCell(monthKey, year, monthNo, "flow_m_fluid_incinerator", rowKey, 3, getCell(row, 4));
            imported += upsertCell(monthKey, year, monthNo, "flow_m_fluid_incinerator", rowKey, 4, getCell(row, 5));

            imported += upsertCell(monthKey, year, monthNo, "flow_m_combined", rowKey, 0, getCell(row, 7));
            imported += upsertCell(monthKey, year, monthNo, "flow_m_combined", rowKey, 1, getCell(row, 8));
            imported += upsertCell(monthKey, year, monthNo, "flow_m_combined", rowKey, 3, getCell(row, 10));

            imported += upsertCell(monthKey, year, monthNo, "flow_m_boiler", rowKey, 0, getCell(row, 12));
            imported += upsertCell(monthKey, year, monthNo, "flow_m_boiler", rowKey, 1, getCell(row, 13));
            imported += upsertCell(monthKey, year, monthNo, "flow_m_boiler", rowKey, 3, getCell(row, 15));
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

    private Integer readDay(Row row) {
        Cell cell = getCell(row, 0);
        if (cell == null || cell.getCellType() == CellType.BLANK || isFormulaCell(cell)) {
            return null;
        }

        BigDecimal value = readNumber(cell);
        if (value == null) {
            return null;
        }

        int day = value.intValue();
        return day >= 1 && day <= 31 ? day : null;
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
