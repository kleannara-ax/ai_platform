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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class ComboBoilerWorkbookService {
    private static final String ORIGINAL_DIR = "\uC6D0\uBCF8";
    private static final String CELL_TABLE = "table_cell_value";
    private static final String PRODUCTION_TABLE = "combo_boiler_production";
    private static final String DOWNTIME_TABLE = "combo_boiler_downtime";
    private static final String RECOVERY_TABLE = "combo_boiler_recovery";
    private static final String ETC_TABLE = "combo_boiler_etc";
    private static final int FIRST_DAY_ROW = 7;
    private static final int MAX_RECOVERY_ROWS = 26;

    private final TableService tableService;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);
    private volatile Path cachedWorkbookPath;

    public ComboBoilerWorkbookService(TableService tableService) {
        this.tableService = tableService;
    }

    public Map<String, Object> importWorkbook(MultipartFile file, int year) throws IOException {
        int imported = 0;
        int validSheets = 0;
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
                Sheet sheet = workbook.getSheetAt(i);
                int month = sheetMonth(sheet.getSheetName());
                if (month < 1 || month > 12) continue;
                validateMonthlySheet(sheet);
                validSheets += 1;
                clearImportedMonth(year, month);
                imported += importProductionSheet(sheet, year, month);
                imported += importDowntimeSheet(sheet, year, month);
                imported += importRecoverySheet(sheet, year, month);
                imported += importEtcSheet(sheet, year, month);
            }
        }
        if (validSheets == 0) {
            throw new IllegalArgumentException("\uBCF5\uD569\uBCF4\uC77C\uB7EC \uC2A4\uD300\uAD6C\uB9E4 \uD604\uD669 \uC5D1\uC140 \uD30C\uC77C\uC774 \uC544\uB2D9\uB2C8\uB2E4.");
        }
        return Map.of("year", year, "imported", imported);
    }

    private void clearImportedMonth(int year, int month) {
        tableService.deleteTableCellValuesByTables(monthKey(year, month), List.of(
                PRODUCTION_TABLE,
                DOWNTIME_TABLE,
                RECOVERY_TABLE
        ));
        tableService.deleteTableCellValuesByTables(monthKey(year, 1), List.of(ETC_TABLE));
    }

    public byte[] exportWorkbook(int year) throws IOException {
        Path workbookPath = resolveWorkbookPath();
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.setForceFormulaRecalculation(true);
            for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
                Sheet sheet = workbook.getSheetAt(i);
                int month = sheetMonth(sheet.getSheetName());
                if (month < 1 || month > 12) continue;
                String monthKey = monthKey(year, month);
                Map<String, Map<String, String>> cells = loadMonthCells(monthKey);
                Map<String, Map<String, String>> annualCells = loadMonthCells(monthKey(year, 1));
                setCellValue(sheet, 2, 0, year + "\uB144  " + month + "\uC6D4");
                fillProductionSheet(sheet, cells.getOrDefault(PRODUCTION_TABLE, Map.of()));
                fillDowntimeSheet(sheet, cells.getOrDefault(DOWNTIME_TABLE, Map.of()));
                fillRecoverySheet(sheet, cells.getOrDefault(RECOVERY_TABLE, Map.of()));
                fillEtcSheet(sheet, annualCells.getOrDefault(ETC_TABLE, Map.of()));
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    // validateWorkbook(int year) 제거됨 — Excel 을 다시 파싱하던 데드 메서드.
    // Excel 파일은 업로드/다운로드 외에서 열지 않는다는 정책에 위배.

    private int importProductionSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        String[][] fields = {
                {"comboSteam", "B"}, {"comboCondensate", "D"}, {"comboMakeup", "E"},
                {"comboDeaerator", "F"}, {"comboProcess", "G"}, {"comboDilution", "H"},
                {"externalSteam", "I"}, {"externalCondensate", "K"}, {"externalMakeup", "L"},
                {"wasteSteam1", "M"}, {"wasteSteam2", "O"}, {"kn20", "Q"}, {"kn50", "R"},
                {"fluidizedSteam", "S"}, {"fluidizedCondensate", "T"},
                {"runtime20", "W"}, {"runtime50", "X"}, {"runtimeFluidized", "Y"}
        };
        for (int day = 1; day <= 31; day += 1) {
            int row = FIRST_DAY_ROW + day - 1;
            for (String[] field : fields) {
                imported += upsertCell(PRODUCTION_TABLE, monthKey, year, month, field[0] + "-" + day, cellText(sheet, row, col(field[1])));
            }
        }
        imported += upsertCell(PRODUCTION_TABLE, monthKey, year, month, "fuelPrice-lngBoiler", cellText(sheet, 42, col("F")));
        imported += upsertCell(PRODUCTION_TABLE, monthKey, year, month, "fuelPrice-fluidized", cellText(sheet, 43, col("F")));
        imported += upsertCell(PRODUCTION_TABLE, monthKey, year, month, "fuelPrice-combo", cellText(sheet, 44, col("F")));
        imported += upsertCell(PRODUCTION_TABLE, monthKey, year, month, "fuelPrice-waste", cellText(sheet, 45, col("F")));
        imported += upsertCell(PRODUCTION_TABLE, monthKey, year, month, "fuelPrice-external", cellText(sheet, 46, col("F")));
        return imported;
    }

    private int importDowntimeSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        String[][] dayFields = {
                {"changeCombo", "AA"},
                {"changeJesco", "AE"},
                {"down1", "AI"},
                {"down2", "AJ"},
                {"disperserSteam", "AK"},
                {"runtimeMin", "AM"}
        };
        for (int day = 1; day <= 31; day += 1) {
            int row = FIRST_DAY_ROW + day - 1;
            for (String[] field : dayFields) {
                imported += upsertCell(DOWNTIME_TABLE, monthKey, year, month, field[0] + "-" + day, cellText(sheet, row, col(field[1])));
            }
        }
        imported += upsertCell(DOWNTIME_TABLE, monthKey, year, month, "ventLossUnitPrice", cellText(sheet, 54, col("D")));
        String[][] rows = {
                {"ventProduction", "50"},
                {"ventInternal", "51"},
                {"ventUsage", "52"}
        };
        for (String[] rowDef : rows) {
            int row = Integer.parseInt(rowDef[1]);
            for (int day = 1; day <= 31; day += 1) {
                imported += upsertCell(DOWNTIME_TABLE, monthKey, year, month, rowDef[0] + "-" + day, cellText(sheet, row, col("E") + day - 1));
            }
        }
        return imported;
    }

    private int importRecoverySheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        for (int index = 1; index <= MAX_RECOVERY_ROWS; index += 1) {
            int row = 58 + index - 1;
            imported += upsertCell(RECOVERY_TABLE, monthKey, year, month, "lossDate-" + index, cellText(sheet, row, col("A")));
            imported += upsertCell(RECOVERY_TABLE, monthKey, year, month, "lossTime-" + index, cellText(sheet, row, col("B")));
            imported += upsertCell(RECOVERY_TABLE, monthKey, year, month, "lossNote-" + index, cellText(sheet, row, col("D")));
            imported += upsertCell(RECOVERY_TABLE, monthKey, year, month, "lossSteamRate-" + index, cellText(sheet, row, col("J")));
            imported += upsertCell(RECOVERY_TABLE, monthKey, year, month, "lossBaseRate-" + index, cellText(sheet, row, col("K")));
            imported += upsertCell(RECOVERY_TABLE, monthKey, year, month, "lossMinutes-" + index, cellText(sheet, row, col("L")));
        }
        return imported;
    }

    private int importEtcSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, 1);
        imported += importHorizontal(sheet, year, 1, monthKey, "combo-plan-1-", 58, "S", "AG");
        imported += importHorizontal(sheet, year, 1, monthKey, "combo-plan-2-", 59, "S", "AG");
        imported += importHorizontal(sheet, year, 1, monthKey, "combo-plan-3-", 60, "S", "AG");
        imported += importHorizontal(sheet, year, 1, monthKey, "combo-plan-4-", 61, "S", "AG");
        imported += importHorizontal(sheet, year, 1, monthKey, "combo-plan-5-", 62, "S", "AG");
        imported += importHorizontal(sheet, year, 1, monthKey, "jesco-plan-0-", 67, "R", "AD");
        imported += importHorizontal(sheet, year, 1, monthKey, "jesco-plan-1-", 68, "R", "AD");
        imported += importHorizontal(sheet, year, 1, monthKey, "jesco-plan-2-", 69, "R", "AD");
        imported += importHorizontal(sheet, year, 1, monthKey, "lngBoiler-", 73, "R", "AC", 1);
        imported += importHorizontal(sheet, year, 1, monthKey, "lngCombo-", 74, "R", "AC", 1);
        return imported;
    }

    private int importHorizontal(Sheet sheet, int year, int month, String monthKey, String prefix, int row, String startCol, String endCol) {
        return importHorizontal(sheet, year, month, monthKey, prefix, row, startCol, endCol, 0);
    }

    private int importHorizontal(Sheet sheet, int year, int month, String monthKey, String prefix, int row, String startCol, String endCol, int keyOffset) {
        int imported = 0;
        int start = col(startCol);
        int end = col(endCol);
        for (int column = start; column <= end; column += 1) {
            imported += upsertCell(ETC_TABLE, monthKey, year, month, prefix + (column - start + keyOffset), cellText(sheet, row, column));
        }
        return imported;
    }

    private void fillProductionSheet(Sheet sheet, Map<String, String> cells) {
        String[][] fields = {
                {"comboSteam", "B"}, {"comboCondensate", "D"}, {"comboMakeup", "E"},
                {"comboDeaerator", "F"}, {"comboProcess", "G"}, {"comboDilution", "H"},
                {"externalSteam", "I"}, {"externalCondensate", "K"}, {"externalMakeup", "L"},
                {"wasteSteam1", "M"}, {"wasteSteam2", "O"}, {"kn20", "Q"}, {"kn50", "R"},
                {"fluidizedSteam", "S"}, {"fluidizedCondensate", "T"},
                {"runtime20", "W"}, {"runtime50", "X"}, {"runtimeFluidized", "Y"}
        };
        for (int day = 1; day <= 31; day += 1) {
            int row = FIRST_DAY_ROW + day - 1;
            for (String[] field : fields) setCellValue(sheet, row, col(field[1]), cells.get(field[0] + "-" + day));
        }
        setCellValue(sheet, 42, col("F"), cells.get("fuelPrice-lngBoiler"));
        setCellValue(sheet, 43, col("F"), cells.get("fuelPrice-fluidized"));
        setCellValue(sheet, 44, col("F"), cells.get("fuelPrice-combo"));
        setCellValue(sheet, 45, col("F"), cells.get("fuelPrice-waste"));
        setCellValue(sheet, 46, col("F"), cells.get("fuelPrice-external"));
    }

    private void fillDowntimeSheet(Sheet sheet, Map<String, String> cells) {
        String[][] dayFields = {
                {"changeCombo", "AA"},
                {"changeJesco", "AE"},
                {"down1", "AI"},
                {"down2", "AJ"},
                {"disperserSteam", "AK"},
                {"runtimeMin", "AM"}
        };
        for (int day = 1; day <= 31; day += 1) {
            int row = FIRST_DAY_ROW + day - 1;
            for (String[] field : dayFields) {
                setCellValue(sheet, row, col(field[1]), cells.get(field[0] + "-" + day));
            }
        }
        setCellValue(sheet, 54, col("D"), cells.get("ventLossUnitPrice"));
        String[][] rows = {
                {"ventProduction", "50"},
                {"ventInternal", "51"},
                {"ventUsage", "52"}
        };
        for (String[] rowDef : rows) {
            int row = Integer.parseInt(rowDef[1]);
            for (int day = 1; day <= 31; day += 1) {
                setCellValue(sheet, row, col("E") + day - 1, cells.get(rowDef[0] + "-" + day));
            }
        }
    }

    private void fillRecoverySheet(Sheet sheet, Map<String, String> cells) {
        for (int index = 1; index <= MAX_RECOVERY_ROWS; index += 1) {
            int row = 58 + index - 1;
            setCellValue(sheet, row, col("A"), cells.get("lossDate-" + index));
            setCellValue(sheet, row, col("B"), cells.get("lossTime-" + index));
            setCellValue(sheet, row, col("D"), cells.get("lossNote-" + index));
            setCellValue(sheet, row, col("J"), cells.get("lossSteamRate-" + index));
            setCellValue(sheet, row, col("K"), cells.get("lossBaseRate-" + index));
            setCellValue(sheet, row, col("L"), cells.get("lossMinutes-" + index));
        }
    }

    private void fillEtcSheet(Sheet sheet, Map<String, String> cells) {
        fillHorizontal(sheet, cells, "combo-plan-1-", 58, "S", "AG");
        fillHorizontal(sheet, cells, "combo-plan-2-", 59, "S", "AG");
        fillHorizontal(sheet, cells, "combo-plan-3-", 60, "S", "AG", 0, true);
        fillHorizontal(sheet, cells, "combo-plan-4-", 61, "S", "AG");
        fillHorizontal(sheet, cells, "combo-plan-5-", 62, "S", "AG", 0, true);
        fillHorizontal(sheet, cells, "jesco-plan-0-", 67, "R", "AD");
        fillHorizontal(sheet, cells, "jesco-plan-1-", 68, "R", "AD");
        fillHorizontal(sheet, cells, "jesco-plan-2-", 69, "R", "AD", 0, true);
        fillHorizontal(sheet, cells, "lngBoiler-", 73, "R", "AC", 1);
        fillHorizontal(sheet, cells, "lngCombo-", 74, "R", "AC", 1);
    }

    private void fillHorizontal(Sheet sheet, Map<String, String> cells, String prefix, int row, String startCol, String endCol) {
        fillHorizontal(sheet, cells, prefix, row, startCol, endCol, 0);
    }

    private void fillHorizontal(Sheet sheet, Map<String, String> cells, String prefix, int row, String startCol, String endCol, int keyOffset) {
        fillHorizontal(sheet, cells, prefix, row, startCol, endCol, keyOffset, false);
    }

    private void fillHorizontal(Sheet sheet, Map<String, String> cells, String prefix, int row, String startCol, String endCol, int keyOffset, boolean overwriteFormula) {
        int start = col(startCol);
        int end = col(endCol);
        for (int column = start; column <= end; column += 1) {
            setCellValue(sheet, row, column, cells.get(prefix + (column - start + keyOffset)), overwriteFormula);
        }
    }

    private Map<String, Map<String, String>> loadMonthCells(String monthKey) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : tableService.listAllByMonth(CELL_TABLE, monthKey)) {
            if (toInt(row.get("col_index")) != 0) continue;
            String tableName = String.valueOf(row.get("table_name"));
            String rowKey = String.valueOf(row.get("row_key"));
            String value = String.valueOf(row.get("cell_value"));
            result.computeIfAbsent(tableName, key -> new LinkedHashMap<>()).put(rowKey, value);
        }
        return result;
    }

    private int upsertCell(String tableName, String monthKey, int year, int month, String rowKey, String value) {
        if (value == null || value.isBlank()) return 0;
        tableService.upsert(CELL_TABLE, Map.of(
                "month", monthKey,
                "year_no", year,
                "month_no", month,
                "table_name", tableName,
                "row_key", rowKey,
                "col_index", 0,
                "cell_value", value
        ));
        return 1;
    }

    private String cellText(Sheet sheet, int oneBasedRow, int zeroBasedColumn) {
        Row row = sheet.getRow(oneBasedRow - 1);
        if (row == null) return null;
        Cell cell = row.getCell(zeroBasedColumn);
        if (cell == null || cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.BLANK) return null;
        String value = formatter.formatCellValue(cell).replace(",", "").trim();
        if (value.isBlank()) return null;
        if ("-".equals(value)) return "0";
        return value;
    }

    private void validateMonthlySheet(Sheet sheet) {
        String b41 = cellTextAllowFormula(sheet, 41, col("B"));
        String d41 = cellTextAllowFormula(sheet, 41, col("D"));
        String f41 = cellTextAllowFormula(sheet, 41, col("F"));
        if (!contains(b41, "\uAD6C") || !contains(d41, "\uC2A4\uD300") || !contains(f41, "\uC5F0\uB8CC\uB2E8\uAC00")) {
            throw new IllegalArgumentException("\uBCF5\uD569\uBCF4\uC77C\uB7EC \uC2A4\uD300\uAD6C\uB9E4 \uD604\uD669 \uC5D1\uC140 \uD615\uC2DD\uC774 \uC544\uB2D9\uB2C8\uB2E4.");
        }
    }

    private String cellTextAllowFormula(Sheet sheet, int oneBasedRow, int zeroBasedColumn) {
        Row row = sheet.getRow(oneBasedRow - 1);
        if (row == null) return "";
        Cell cell = row.getCell(zeroBasedColumn);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.replace(" ", "").contains(keyword);
    }

    private void setCellValue(Sheet sheet, int oneBasedRow, int zeroBasedColumn, Object value) {
        setCellValue(sheet, oneBasedRow, zeroBasedColumn, value, false);
    }

    private void setCellValue(Sheet sheet, int oneBasedRow, int zeroBasedColumn, Object value, boolean overwriteFormula) {
        Row row = sheet.getRow(oneBasedRow - 1);
        if (row == null) row = sheet.createRow(oneBasedRow - 1);
        Cell cell = row.getCell(zeroBasedColumn);
        if (cell == null) cell = row.createCell(zeroBasedColumn);
        if (cell.getCellType() == CellType.FORMULA && (!overwriteFormula || cell.isPartOfArrayFormulaGroup())) {
            return;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }
        String text = String.valueOf(value).replace(",", "").trim();
        try {
            cell.setCellValue(new BigDecimal(text).doubleValue());
        } catch (NumberFormatException exception) {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private Path resolveWorkbookPath() throws IOException {
        Path current = cachedWorkbookPath;
        if (current != null && Files.exists(current)) return current;
        try (Stream<Path> paths = Files.walk(Path.of(ORIGINAL_DIR), 1)) {
            Path resolved = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("3."))
                    .filter(path -> path.getFileName().toString().endsWith(".xlsx"))
                    .filter(path -> !path.getFileName().toString().startsWith("~$"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Combo boiler workbook not found"));
            cachedWorkbookPath = resolved;
            return resolved;
        }
    }

    private int sheetMonth(String sheetName) {
        if (sheetName == null || sheetName.contains("\uC591\uC2DD")) return -1;
        Matcher matcher = Pattern.compile("\\((\\d{1,2})\\s*\uC6D4\\)").matcher(sheetName);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private String monthKey(int year, int month) {
        return year + "-" + String.format("%02d", month);
    }

    private int col(String label) {
        int result = 0;
        for (int i = 0; i < label.length(); i += 1) {
            result = result * 26 + (label.charAt(i) - 'A' + 1);
        }
        return result - 1;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception exception) {
            return 0;
        }
    }
}
