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
 * 에너지 연료비 배분비율 (energy-fuel-ratio) Excel ↔ DB 매핑.
 *
 * Excel sheet pattern : "\d{4}\s*연료비\s*배분비율" (예: '2025 연료비 배분비율')
 * DB table            : energy_fuel_ratio
 * Row key 형식         : tNN|rMM  (t01..t17)
 * Col index 1..13     ↔ Excel col B..N  (excel_col_zero_based = col_index)
 *                       1=전년 12월, 2=1월, ..., 13=12월
 *
 * 테이블 매핑 (tableId, htmlRowCount, firstExcelRow):
 *  t01  9 rows  excel  4..12   (스팀생산량 — LNG/복합/폐합성/유동상/외부/합계/유동상밴트/페널티량/보증량)
 *  t02  8 rows  excel 16..23   (보일러 연료비)
 *  t03  6 rows  excel 27..32   (스팀단가)
 *  t04 12 rows  excel 39..50   (호기별 연료비배분)
 *  t05 12 rows  excel 53..64   (호기별 스팀사용량)
 *  t06  8 rows  excel 68..75   (제품생산량)
 *  t07  8 rows  excel 79..86   (스팀 연료비)
 *  t08  8 rows  excel 90..97   (연료 원단위)
 *  t09  8 rows  excel 101..108 (스팀 톤당 연료비)
 *  t10  6 rows  excel 112..117 (LNG 사용량)
 *  t11  9 rows  excel 120..129 (LNG 비용 — r123 header skip)
 *  t12  5 rows  excel 133..137 (LNG 사용량 원단위)
 *  t13  5 rows  excel 141..145 (LNG 사용비용 원단위)
 *  t14  8 rows  excel 149..156 (총 연료단가)
 *  t15  7 rows  excel 160..166 (스팀사용량 원단위)
 *  t16  8 rows  excel 170..177 (호기별 스팀사용비율)
 *  t17  7 rows  excel 181..187 (스팀사용량 원단위 with 기타)
 *
 * 정책: Excel formula 셀 skip / 빈 셀 skip / 일반 숫자 셀 upsert
 */
@Service
public class EnergyFuelRatioWorkbookService {
    private static final String ORIGINAL_DIR = "원본";
    private static final String FILE_PREFIX = "6.";
    private static final Pattern SHEET_NAME_PATTERN = Pattern.compile("\\d{4}\\s*연료비\\s*배분비율");
    private static final String CELL_TABLE = "table_cell_value";
    private static final String PAGE_TABLE = "energy_fuel_ratio";

    private record TableMap(String tableId, int htmlRowCount, int firstExcelRow, int rowGap) {
        TableMap(String t, int n, int s) { this(t, n, s, 0); }
    }

    /**
     * t11 만 r123 (header) 을 건너뛰어야 함 — htmlRow >= 4 부터 excelRow 가 +1 이동.
     * 단순화를 위해 t11 전체에 대해 dynamic offset 처리.
     */
    private static final List<TableMap> TABLE_MAPS = List.of(
            new TableMap("t01",  9,  4),
            new TableMap("t02",  8, 16),
            new TableMap("t03",  6, 27),
            new TableMap("t04", 12, 39),
            new TableMap("t05", 12, 53),
            new TableMap("t06",  8, 68),
            new TableMap("t07",  8, 79),
            new TableMap("t08",  8, 90),
            new TableMap("t09",  8, 101),
            new TableMap("t10",  6, 112),
            new TableMap("t11",  9, 120), // r120,121,122,124,125,126,127,128,129 (skip r123 header)
            new TableMap("t12",  5, 133),
            new TableMap("t13",  5, 141),
            new TableMap("t14",  8, 149),
            new TableMap("t15",  7, 160),
            new TableMap("t16",  8, 170),
            new TableMap("t17",  7, 181)
    );

    private final TableService tableService;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);
    private volatile Path cachedWorkbookPath;

    public EnergyFuelRatioWorkbookService(TableService tableService) {
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
        for (TableMap map : TABLE_MAPS) {
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                int excelRowOneBased = excelRowFor(map, htmlRow);
                Row row = sheet.getRow(excelRowOneBased - 1);
                if (row == null) continue;
                for (int colIndex = 1; colIndex <= 13; colIndex += 1) {
                    int excelColZeroBased = colIndex;
                    Cell cell = row.getCell(excelColZeroBased);
                    String text = readInputCellText(cell);
                    if (text == null) continue;
                    upsertCell(year, rowKey(map.tableId(), htmlRow), colIndex, text);
                    imported += 1;
                }
            }
        }
        return imported;
    }

    public void applyExport(Workbook workbook, int year) {
        Sheet sheet = findSheetByPattern(workbook);
        if (sheet == null) return;
        Map<String, String> cells = loadCells(year);
        for (TableMap map : TABLE_MAPS) {
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                int excelRowOneBased = excelRowFor(map, htmlRow);
                for (int colIndex = 1; colIndex <= 13; colIndex += 1) {
                    String key = rowKey(map.tableId(), htmlRow) + "|" + colIndex;
                    String value = cells.get(key);
                    if (value == null) continue;
                    int excelColZeroBased = colIndex;
                    setNonFormulaCellNumber(sheet, excelRowOneBased, excelColZeroBased, value);
                }
            }
        }
    }

    /* ── helpers ─────────────────────────────────────────────── */

    /** t11 만 htmlRow 4 부터 excel row +1 (r123 header 건너뜀). 다른 테이블은 contiguous. */
    private int excelRowFor(TableMap map, int htmlRow) {
        if ("t11".equals(map.tableId()) && htmlRow >= 4) {
            return map.firstExcelRow() + (htmlRow - 1) + 1;
        }
        return map.firstExcelRow() + (htmlRow - 1);
    }

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

    /**
     * 연료비 배분비율 셀 읽기 정책:
     *  - 모든 수식 셀(외부 파일 참조 / 내부 계산) → 마지막 계산(캐시)값을 import.
     *    캐시 데이터가 있으면 무조건 가져온다. (페이지의 계산 행은 JS 가 다시 계산하므로 무방)
     *  - 일반 숫자 → 그대로
     */
    private String readInputCellText(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.BLANK) return null;
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
            return null; // ERROR / BOOLEAN 등은 skip
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

    private String rowKey(String tableId, int htmlRow) {
        return tableId + "|r" + String.format("%02d", htmlRow);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
