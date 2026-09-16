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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 에너지 회계비용 마감비교 (energy-month-compare) Excel ↔ DB 매핑.
 *
 * Excel sheet pattern : "회계비용\s*마감과\s*실마감\s*비교" (예: '회계비용 마감과 실마감 비교(2025)')
 * DB table            : energy_month_compare
 * Row key 형식         : {section}|{rowKey}  (section ∈ {fuel,power,total})
 * Col index 0..13     ↔ Excel col C..P  (excel_col_zero_based = col_index + 2)
 *                       0=전년 12월, 1=1월, ..., 12=12월, 13=다음해 1월
 *
 * 정책: Excel formula 셀 skip / 빈 셀 skip / 일반 숫자 셀 upsert
 *      → 외부 xlsx 참조 / 내부 계산식 / 다른 페이지에서 자동 계산된 imported 값은 모두 보존
 *      → HTML 사용자 입력 셀만 왕복
 */
@Service
public class EnergyMonthCompareWorkbookService {
    private static final String ORIGINAL_DIR = "원본";
    private static final String FILE_PREFIX = "6.";
    private static final Pattern SHEET_NAME_PATTERN = Pattern.compile("회계비용\\s*마감과\\s*실마감\\s*비교");
    private static final String CELL_TABLE = "table_cell_value";
    private static final String PAGE_TABLE = "energy_month_compare";

    private record RowMap(String section, String rowKey, int excelRowOneBased) {}

    private static final List<RowMap> ROW_MAPS = List.of(
            // ── fuel 섹션 ────────────────────────────────────────────
            new RowMap("fuel", "compositeSteam",   3),
            new RowMap("fuel", "zescoSteam",       4),
            new RowMap("fuel", "lngBuy",           5),
            new RowMap("fuel", "pyhsynSteam",      6),
            new RowMap("fuel", "fluidSteam",       7),
            // r8 fuelMonthly = formula
            new RowMap("fuel", "zescoOffset",      9),
            new RowMap("fuel", "zescoPenalty",    10),
            new RowMap("fuel", "compositeLngEtc", 11),
            new RowMap("fuel", "compositeInvest", 12),
            new RowMap("fuel", "compositeIncome", 13),
            new RowMap("fuel", "landLease",       14),
            // r15 deductSubtotal = formula
            // r16 fuelActual = formula
            new RowMap("fuel", "paperFuel",       17),
            new RowMap("fuel", "livingFuel",      18),
            new RowMap("fuel", "fuelPlan",        19),
            new RowMap("fuel", "diff",            20),
            new RowMap("fuel", "monthDiff",       21),
            // r22 prodTotal = formula
            new RowMap("fuel", "paperProd",       23),
            new RowMap("fuel", "tissueProd",      24),
            // r25~r27 unit = formula
            new RowMap("fuel", "profitDelta",     28),
            new RowMap("fuel", "paperProfit",     29),
            new RowMap("fuel", "livingProfit",    30),
            // r31 profitSum = formula
            new RowMap("fuel", "steamProd",       32),
            new RowMap("fuel", "lngBurner",       33),
            // r34~r36 = formula

            // ── power 섹션 ────────────────────────────────────────────
            new RowMap("power", "powerMonthly",     40),
            new RowMap("power", "energyKSettle",    41),
            new RowMap("power", "essSettle",        42),
            new RowMap("power", "kepcoBill",        43),
            new RowMap("power", "dongjinWh",        44),
            new RowMap("power", "eumseongFct",      45),
            new RowMap("power", "logisticsWh",      46),
            new RowMap("power", "drRefund",         47),
            new RowMap("power", "compositePower",   48),
            new RowMap("power", "powerSettle",      49),
            // r50 powerDeductSum / r51 powerActualCj / r52 powerActualAll = computed
            new RowMap("power", "paperPower",       53),
            new RowMap("power", "tissuePower",      54),
            new RowMap("power", "padPower",         55),
            new RowMap("power", "powerPlan",        56),
            // r57 powerVsPlan / r58 powerProdTotal = formula
            new RowMap("power", "powerPaperProd",   59),
            new RowMap("power", "powerTissueProd",  60),
            new RowMap("power", "padProd",          61),
            // r62~r66 unit/delta = formula
            new RowMap("power", "paperPowerProfit", 67),
            new RowMap("power", "tissuePowerProfit",68),
            new RowMap("power", "padPowerProfit",   69),
            // r70 powerProfitSum / r72 powerRateWon = formula/sum
            new RowMap("power", "powerUsageMw",     71)

            // total 섹션: 전부 formula/sum 이므로 매핑 없음 (Excel 자동 계산)
    );

    private final TableService tableService;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);
    private volatile Path cachedWorkbookPath;

    public EnergyMonthCompareWorkbookService(TableService tableService) {
        this.tableService = tableService;
    }

    public Map<String, Object> importWorkbook(MultipartFile file, int year) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            int imported = applyImport(workbook, year);
            return Map.of("year", year, "imported", imported);
        }
    }

    public byte[] exportWorkbook(int year) throws IOException {
        Path workbookPath = resolveWorkbookPath();
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setForceFormulaRecalculation(true);
            applyExport(workbook, year);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public int applyImport(Workbook workbook, int year) {
        Sheet sheet = findSheetByPattern(workbook);
        if (sheet == null) return 0;
        tableService.deleteTableCellValuesByTables(String.valueOf(year), List.of(PAGE_TABLE));
        int imported = 0;
        for (RowMap map : ROW_MAPS) {
            Row row = sheet.getRow(map.excelRowOneBased() - 1);
            if (row == null) continue;
            for (int colIndex = 0; colIndex <= 13; colIndex += 1) {
                int excelColZeroBased = colIndex + 2;
                Cell cell = row.getCell(excelColZeroBased);
                String text = readInputCellText(cell);
                if (text == null) continue;
                upsertCell(year, dbRowKey(map), colIndex, text);
                imported += 1;
            }
        }
        return imported;
    }

    public void applyExport(Workbook workbook, int year) {
        Sheet sheet = findSheetByPattern(workbook);
        if (sheet == null) return;
        Map<String, String> cells = loadCells(year);
        for (RowMap map : ROW_MAPS) {
            for (int colIndex = 0; colIndex <= 13; colIndex += 1) {
                String key = dbRowKey(map) + "|" + colIndex;
                String value = cells.get(key);
                if (value == null) continue;
                int excelColZeroBased = colIndex + 2;
                setNonFormulaCellNumber(sheet, map.excelRowOneBased(), excelColZeroBased, value);
            }
        }
    }

    /* ── helpers ─────────────────────────────────────────────── */

    private String dbRowKey(RowMap m) { return m.section() + "|" + m.rowKey(); }

    private Sheet findSheetByPattern(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName();
            if (name != null && SHEET_NAME_PATTERN.matcher(name).find()) {
                return sheet;
            }
        }
        return null;
    }

    private Map<String, String> loadCells(int year) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, Object> row : tableService.listAllByMonth(CELL_TABLE, String.valueOf(year))) {
            if (!PAGE_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            String rk = String.valueOf(row.get("row_key"));
            int ci = toInt(row.get("col_index"));
            Object v = row.get("cell_value");
            if (v == null) continue;
            out.put(rk + "|" + ci, String.valueOf(v));
        }
        return out;
    }

    private void upsertCell(int year, String rowKey, int colIndex, String value) {
        if (value == null || value.isBlank()) return;
        tableService.upsert(CELL_TABLE, Map.of(
                "month", String.valueOf(year),
                "year_no", year,
                "month_no", 0,
                "table_name", PAGE_TABLE,
                "row_key", rowKey,
                "col_index", colIndex,
                "cell_value", value
        ));
    }

    private String readInputCellText(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.BLANK) return null;
        // 수식 셀: 마지막 계산(캐시)값을 import — 캐시 데이터가 있으면 무조건 가져온다.
        if (type == CellType.FORMULA) {
            CellType rt = cell.getCachedFormulaResultType();
            if (rt == CellType.NUMERIC) {
                double v = cell.getNumericCellValue();
                return new BigDecimal(String.valueOf(v)).toPlainString();
            }
            if (rt == CellType.STRING) {
                String s = cell.getStringCellValue();
                return (s == null || s.isBlank()) ? null : s.trim();
            }
            return null;
        }
        if (type == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            return new BigDecimal(String.valueOf(v)).toPlainString();
        }
        String text = formatter.formatCellValue(cell).replace(",", "").trim();
        return text.isBlank() ? null : text;
    }

    private void setNonFormulaCellNumber(Sheet sheet, int oneBasedRow, int zeroBasedColumn, String value) {
        Row row = sheet.getRow(oneBasedRow - 1);
        if (row == null) row = sheet.createRow(oneBasedRow - 1);
        Cell cell = row.getCell(zeroBasedColumn);
        if (cell == null) cell = row.createCell(zeroBasedColumn);
        if (cell.getCellType() == CellType.FORMULA) return;
        String text = value.replace(",", "").trim();
        if (text.isBlank()) { cell.setBlank(); return; }
        try {
            cell.setCellValue(new BigDecimal(text).doubleValue());
        } catch (NumberFormatException exception) {
            cell.setCellValue(text);
        }
    }

    private Path resolveWorkbookPath() throws IOException {
        Path current = cachedWorkbookPath;
        if (current != null && Files.exists(current)) return current;
        try (Stream<Path> paths = Files.walk(Path.of(ORIGINAL_DIR), 1)) {
            Path resolved = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(FILE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("에너지 회계비용 원본 엑셀을 찾을 수 없습니다."));
            cachedWorkbookPath = resolved;
            return resolved;
        }
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
