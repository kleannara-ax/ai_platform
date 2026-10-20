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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ComboBoilerWorkbookService {
    private static final String CELL_TABLE = "steam_cell_value";
    private static final String PRODUCTION_TABLE = "combo_boiler_production";
    private static final String DOWNTIME_TABLE = "combo_boiler_downtime";
    private static final String RECOVERY_TABLE = "combo_boiler_recovery";
    private static final String ETC_TABLE = "combo_boiler_etc";
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
                // 좌표가 아니라 머리글 글자로 칸을 찾는다 — 원본에서 행·열을 지워도 밀리지 않는다.
                ComboBoilerLayout layout = ComboBoilerLayout.of(sheet);
                validateMonthlySheet(sheet, layout);
                validSheets += 1;
                clearImportedMonth(year, month);
                imported += importProductionSheet(sheet, layout, year, month);
                imported += importDowntimeSheet(sheet, layout, year, month);
                imported += importRecoverySheet(sheet, layout, year, month);
                imported += importEtcSheet(sheet, layout, year, month);
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
                // \uB0B4\uB824\uBC1B\uAE30\uB3C4 \uAC19\uC740 \uC0C9\uC778\uC744 \uC4F4\uB2E4 \u2014 \uC5C5\uB85C\uB4DC\uC640 \uC790\uB9AC\uAC00 \uC5B4\uAE0B\uB098\uC9C0 \uC54A\uAC8C.
                ComboBoilerLayout layout = ComboBoilerLayout.of(sheet);
                setCellValue(sheet, 2, 0, year + "\uB144  " + month + "\uC6D4");
                fillProductionSheet(sheet, layout, cells.getOrDefault(PRODUCTION_TABLE, Map.of()));
                fillDowntimeSheet(sheet, layout, cells.getOrDefault(DOWNTIME_TABLE, Map.of()));
                fillRecoverySheet(sheet, layout, cells.getOrDefault(RECOVERY_TABLE, Map.of()));
                fillEtcSheet(sheet, layout, annualCells.getOrDefault(ETC_TABLE, Map.of()));
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    // validateWorkbook(int year) 제거됨 — Excel 을 다시 파싱하던 데드 메서드.
    // Excel 파일은 업로드/다운로드 외에서 열지 않는다는 정책에 위배.

    private int importProductionSheet(Sheet sheet, ComboBoilerLayout layout, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        String[] fields = {
                "comboSteam", "comboCondensate", "comboMakeup",
                "comboDeaerator", "comboProcess", "comboDilution",
                "externalSteam", "externalCondensate", "externalMakeup",
                "wasteSteam1", "wasteSteam2", "kn20", "kn50",
                "fluidizedSteam", "fluidizedCondensate",
                "runtime20", "runtime50", "runtimeFluidized"
        };
        imported += importDayFields(sheet, layout, PRODUCTION_TABLE, monthKey, year, month, fields);

        // 연료단가: 설비 이름으로 줄을, "연료단가" 머리글로 칸을 찾는다.
        int priceColumn = layout.fuelPriceColumn();
        if (priceColumn >= 0) {
            for (Map.Entry<String, Integer> entry : layout.fuelPriceRows().entrySet()) {
                imported += upsertCell(PRODUCTION_TABLE, monthKey, year, month, entry.getKey(),
                        cellText(sheet, entry.getValue(), priceColumn));
            }
        }
        return imported;
    }

    private int importDowntimeSheet(Sheet sheet, ComboBoilerLayout layout, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        String[] dayFields = {"changeCombo", "changeJesco", "down1", "down2", "disperserSteam", "runtimeMin"};
        imported += importDayFields(sheet, layout, DOWNTIME_TABLE, monthKey, year, month, dayFields);

        // 벤트 손실: 항목 이름으로 줄을, 날짜 머리글로 칸을 찾는다.
        Integer lossRow = layout.ventRows().get("ventLossUnitPrice");
        if (lossRow != null && layout.ventUnitPriceColumn() >= 0) {
            imported += upsertCell(DOWNTIME_TABLE, monthKey, year, month, "ventLossUnitPrice",
                    cellText(sheet, lossRow, layout.ventUnitPriceColumn()));
        }
        for (String key : new String[]{"ventProduction", "ventInternal", "ventUsage"}) {
            Integer row = layout.ventRows().get(key);
            if (row == null) continue;
            for (Map.Entry<Integer, Integer> day : layout.ventDayColumns().entrySet()) {
                imported += upsertCell(DOWNTIME_TABLE, monthKey, year, month, key + "-" + day.getKey(),
                        cellText(sheet, row, day.getValue()));
            }
        }
        return imported;
    }

    /** 일별 표를 항목 키로 읽는다. 머리글을 못 찾은 항목·일자는 건너뛴다. */
    private int importDayFields(Sheet sheet, ComboBoilerLayout layout, String tableName,
                                String monthKey, int year, int month, String[] fields) {
        int imported = 0;
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            for (String field : fields) {
                Integer column = layout.dayColumns().get(field);
                if (column == null) continue;
                imported += upsertCell(tableName, monthKey, year, month, field + "-" + day.getKey(),
                        cellText(sheet, day.getValue(), column));
            }
        }
        return imported;
    }

    private int importRecoverySheet(Sheet sheet, ComboBoilerLayout layout, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int firstRow = layout.recoveryFirstRow();
        if (firstRow < 0) return imported;
        for (int index = 1; index <= MAX_RECOVERY_ROWS; index += 1) {
            int row = firstRow + index - 1;
            for (Map.Entry<String, Integer> column : layout.recoveryColumns().entrySet()) {
                imported += upsertCell(RECOVERY_TABLE, monthKey, year, month, column.getKey() + "-" + index,
                        cellText(sheet, row, column.getValue()));
            }
        }
        return imported;
    }

    private int importEtcSheet(Sheet sheet, ComboBoilerLayout layout, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, 1);
        for (String block : new String[]{"combo", "jesco", "lng"}) {
            imported += importPlanBlock(sheet, layout.planBlock(block), year, monthKey, "lng".equals(block) ? 1 : 0);
        }
        return imported;
    }

    /**
     * 연간 계획 표 한 벌을 읽는다.
     *
     * <p>월 칸은 머리글에서 찾은 범위({@code 첫 월 ~ "계" 바로 왼쪽})를 쓴다. 저장 키는 예전처럼
     * <b>왼쪽에서 몇 번째 칸인지</b>로 매긴다 — 화면이 그 순서로 월을 그리기 때문이다.
     */
    private int importPlanBlock(Sheet sheet, ComboBoilerLayout.PlanBlock block, int year, String monthKey, int keyOffset) {
        if (block == null || !block.usable()) return 0;
        int imported = 0;
        for (Map.Entry<String, Integer> row : block.rows().entrySet()) {
            for (int column = block.firstMonthCol(); column <= block.lastMonthCol(); column += 1) {
                imported += upsertCell(ETC_TABLE, monthKey, year, 1,
                        row.getKey() + (column - block.firstMonthCol() + keyOffset),
                        cellText(sheet, row.getValue(), column));
            }
        }
        return imported;
    }

    private void fillProductionSheet(Sheet sheet, ComboBoilerLayout layout, Map<String, String> cells) {
        String[] fields = {
                "comboSteam", "comboCondensate", "comboMakeup",
                "comboDeaerator", "comboProcess", "comboDilution",
                "externalSteam", "externalCondensate", "externalMakeup",
                "wasteSteam1", "wasteSteam2", "kn20", "kn50",
                "fluidizedSteam", "fluidizedCondensate",
                "runtime20", "runtime50", "runtimeFluidized"
        };
        fillDayFields(sheet, layout, cells, fields);

        int priceColumn = layout.fuelPriceColumn();
        if (priceColumn < 0) return;
        for (Map.Entry<String, Integer> entry : layout.fuelPriceRows().entrySet()) {
            setCellValue(sheet, entry.getValue(), priceColumn, cells.get(entry.getKey()));
        }
    }

    private void fillDowntimeSheet(Sheet sheet, ComboBoilerLayout layout, Map<String, String> cells) {
        fillDayFields(sheet, layout, cells,
                new String[]{"changeCombo", "changeJesco", "down1", "down2", "disperserSteam", "runtimeMin"});

        Integer lossRow = layout.ventRows().get("ventLossUnitPrice");
        if (lossRow != null && layout.ventUnitPriceColumn() >= 0) {
            setCellValue(sheet, lossRow, layout.ventUnitPriceColumn(), cells.get("ventLossUnitPrice"));
        }
        for (String key : new String[]{"ventProduction", "ventInternal", "ventUsage"}) {
            Integer row = layout.ventRows().get(key);
            if (row == null) continue;
            for (Map.Entry<Integer, Integer> day : layout.ventDayColumns().entrySet()) {
                setCellValue(sheet, row, day.getValue(), cells.get(key + "-" + day.getKey()));
            }
        }
    }

    private void fillDayFields(Sheet sheet, ComboBoilerLayout layout, Map<String, String> cells, String[] fields) {
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            for (String field : fields) {
                Integer column = layout.dayColumns().get(field);
                if (column == null) continue;
                setCellValue(sheet, day.getValue(), column, cells.get(field + "-" + day.getKey()));
            }
        }
    }

    private void fillRecoverySheet(Sheet sheet, ComboBoilerLayout layout, Map<String, String> cells) {
        int firstRow = layout.recoveryFirstRow();
        if (firstRow < 0) return;
        for (int index = 1; index <= MAX_RECOVERY_ROWS; index += 1) {
            int row = firstRow + index - 1;
            for (Map.Entry<String, Integer> column : layout.recoveryColumns().entrySet()) {
                setCellValue(sheet, row, column.getValue(), cells.get(column.getKey() + "-" + index));
            }
        }
    }

    private void fillEtcSheet(Sheet sheet, ComboBoilerLayout layout, Map<String, String> cells) {
        // 소계·잔량 줄은 원본이 수식이지만 화면에서 고칠 수 있는 값이라 덮어쓴다.
        fillPlanBlock(sheet, layout.planBlock("combo"), cells, 0,
                Set.of("combo-plan-3-", "combo-plan-5-"));
        fillPlanBlock(sheet, layout.planBlock("jesco"), cells, 0, Set.of("jesco-plan-2-"));
        fillPlanBlock(sheet, layout.planBlock("lng"), cells, 1, Set.of());
    }

    private void fillPlanBlock(Sheet sheet, ComboBoilerLayout.PlanBlock block, Map<String, String> cells,
                               int keyOffset, Set<String> overwriteFormulaRows) {
        if (block == null || !block.usable()) return;
        for (Map.Entry<String, Integer> row : block.rows().entrySet()) {
            boolean overwriteFormula = overwriteFormulaRows.contains(row.getKey());
            for (int column = block.firstMonthCol(); column <= block.lastMonthCol(); column += 1) {
                String value = cells.get(row.getKey() + (column - block.firstMonthCol() + keyOffset));
                setCellValue(sheet, row.getValue(), column, value, overwriteFormula);
            }
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

    /**
     * \uBCF5\uD569\uBCF4\uC77C\uB7EC \uC11C\uC2DD\uC774 \uB9DE\uB294\uC9C0 \uBCF8\uB2E4 \u2014 \uC608\uC804\uC5D0\uB294 41\uD589\uC758 \uAE00\uC790\uB97C \uBD24\uC9C0\uB9CC, \uC774\uC81C\uB294
     * <b>\uC77C\uBCC4 \uD45C\uC640 \uC5F0\uB8CC\uB2E8\uAC00 \uD45C\uB97C \uBA38\uB9AC\uAE00\uB85C \uCC3E\uC744 \uC218 \uC788\uB294\uC9C0</b>\uB85C \uD310\uB2E8\uD55C\uB2E4.
     * \uC6D0\uBCF8\uC5D0\uC11C \uD589\uC744 \uBA87 \uAC1C \uC9C0\uC6B4 \uD30C\uC77C\uB3C4 \uD1B5\uACFC\uD574\uC57C \uD558\uAE30 \uB54C\uBB38\uC774\uB2E4.
     */
    private void validateMonthlySheet(Sheet sheet, ComboBoilerLayout layout) {
        if (layout.dayRows().isEmpty() || layout.dayColumns().isEmpty() || layout.fuelPriceColumn() < 0) {
            throw new IllegalArgumentException("\uBCF5\uD569\uBCF4\uC77C\uB7EC \uC2A4\uD300\uAD6C\uB9E4 \uD604\uD669 \uC5D1\uC140 \uD615\uC2DD\uC774 \uC544\uB2D9\uB2C8\uB2E4.");
        }
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
        // 작업 디렉터리에 기대지 않고 '원본' 폴더를 찾아 올라간다 (OriginalWorkbookFiles 주석 참고)
        Path resolved = OriginalWorkbookFiles.find(
                name -> name.startsWith("3.") && name.endsWith(".xlsx"), "복합보일러");
        cachedWorkbookPath = resolved;
        return resolved;
    }

    private int sheetMonth(String sheetName) {
        if (sheetName == null || sheetName.contains("\uC591\uC2DD")) return -1;
        Matcher matcher = Pattern.compile("\\((\\d{1,2})\\s*\uC6D4\\)").matcher(sheetName);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private String monthKey(int year, int month) {
        return year + "-" + String.format("%02d", month);
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
