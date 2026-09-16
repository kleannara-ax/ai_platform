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
import java.util.stream.Stream;

/**
 * 에너지 회계비용 계획 (energy-accounting-plan) Excel ↔ DB 매핑.
 *
 * Excel sheet  : '25년 회계비용 계획'
 * DB table     : energy_accounting_plan
 * Row mapping  : DB row r## (## = 1..32)  ↔  Excel row (## + 2), 즉 r01 → Excel 3행
 * Col mapping  : DB col_index 1..13       ↔  Excel column C..O   (col_index + 1 = 0-based col)
 *                col_index 1 = 전년 12월, 2 = 1월, ..., 13 = 12월
 *
 * 정책:
 *  - Excel 의 formula 셀은 import/export 모두 건드리지 않는다 (Excel 이 자동 재계산).
 *  - 따라서 사용자가 입력하는 raw cell 만 왕복한다.
 */
@Service
public class EnergyAccountingPlanWorkbookService {
    private static final String ORIGINAL_DIR = "원본";
    private static final String FILE_PREFIX = "6.";
    // 시트명 패턴: "25년 회계비용 계획", "26년 회계비용 계획", "2025년 회계비용 계획" 등 모두 매칭
    private static final java.util.regex.Pattern SHEET_NAME_PATTERN =
            java.util.regex.Pattern.compile("\\d+년\\s*회계비용\\s*계획");
    private static final String CELL_TABLE = "table_cell_value";
    private static final String PAGE_TABLE = "energy_accounting_plan";
    private static final int HTML_ROW_COUNT = 32;
    private static final int HTML_TO_EXCEL_ROW_OFFSET = 2; // HTML r01 → Excel row 3

    private final TableService tableService;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);
    private volatile Path cachedWorkbookPath;

    public EnergyAccountingPlanWorkbookService(TableService tableService) {
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

    /** orchestrator (efs all-in-one) 가 호출하는 import 적용 메서드. 시트 없으면 0 반환 (다른 시트만 있는 워크북 호환). */
    public int applyImport(Workbook workbook, int year) {
        Sheet sheet = findSheetByPattern(workbook);
        if (sheet == null) return 0;
        tableService.deleteTableCellValuesByTables(String.valueOf(year), List.of(PAGE_TABLE));
        int imported = 0;
        for (int htmlRow = 1; htmlRow <= HTML_ROW_COUNT; htmlRow += 1) {
            int excelRowOneBased = htmlRow + HTML_TO_EXCEL_ROW_OFFSET;
            Row row = sheet.getRow(excelRowOneBased - 1);
            if (row == null) continue;
            for (int colIndex = 1; colIndex <= 13; colIndex += 1) {
                int excelColZeroBased = colIndex + 1;
                Cell cell = row.getCell(excelColZeroBased);
                String text = readInputCellText(cell);
                if (text == null) continue;
                upsertCell(year, rowKey(htmlRow), colIndex, text);
                imported += 1;
            }
        }
        return imported;
    }

    /** orchestrator (efs all-in-one) 가 호출하는 export 적용 메서드. */
    public void applyExport(Workbook workbook, int year) {
        Sheet sheet = findSheetByPattern(workbook);
        if (sheet == null) return;
        Map<String, String> cells = loadCells(year);
        for (int htmlRow = 1; htmlRow <= HTML_ROW_COUNT; htmlRow += 1) {
            int excelRowOneBased = htmlRow + HTML_TO_EXCEL_ROW_OFFSET;
            for (int colIndex = 1; colIndex <= 13; colIndex += 1) {
                String key = rowKey(htmlRow) + "|" + colIndex;
                String value = cells.get(key);
                if (value == null) continue;
                int excelColZeroBased = colIndex + 1;
                setNonFormulaCellNumber(sheet, excelRowOneBased, excelColZeroBased, value);
            }
        }
    }

    /* ── helpers ─────────────────────────────────────────────── */

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
        // 수식 셀: 마지막 계산(캐시)값을 import — 사업계획대비 분석 등에서 계획 연료비를 가져올 수 있도록.
        //  (페이지의 계산 행은 JS 가 다시 계산하므로 캐시값 import 해도 표시는 JS 가 덮어씀)
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
        // NUMERIC: 셀 표시 format 무시하고 raw 값 읽기 (0 이나 음수가 format 으로 ""/-/공백 처리되는 케이스 방지)
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
        // formula 보존
        if (cell.getCellType() == CellType.FORMULA) return;
        String text = value.replace(",", "").trim();
        if (text.isBlank()) {
            cell.setBlank();
            return;
        }
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

    private String rowKey(int htmlRow) {
        return "r" + String.format("%02d", htmlRow);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
