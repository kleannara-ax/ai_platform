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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 에너지 연료비 사업계획 (energy-fuel-plan) Excel ↔ DB 매핑.
 *
 * Excel sheet pattern : "\d{4}\s*연료비\s*사업계획"  (예: '2025 연료비 사업계획(안)')
 * DB table            : energy_fuel_plan
 * Row key 형식         : tNN|rMM   (t01..t10 / r01..)
 * Col index 1..12     ↔ Excel col E..P  (excel_col_zero_based = col_index + 3)
 *
 * 테이블 매핑 (htmlRowCount, firstExcelRow):
 *  t01 14 rows  excel 5..18
 *  t02  6 rows  excel 19..24
 *  t03  3 rows  excel 25..27
 *  t04  6 rows  excel 28..33
 *  t05  6 rows  excel 34..39
 *  t06 10 rows  excel 40..49
 *  t07  5 rows  excel 50..54
 *  t08  6 rows  excel 55..60   (gap r61)
 *  t09  8 rows  excel 62..69
 *  t10  2 rows  excel 70..71
 *
 * 정책:
 *  - Excel formula 셀 (외부 xlsx 참조 / 내부 계산식) → skip (DB 보존, Excel 자동 재계산)
 *  - 빈 셀 → skip (기존 DB 값 유지)
 *  - 일반 숫자 셀 → upsert
 */
@Service
public class EnergyFuelPlanWorkbookService {
    private static final String ORIGINAL_DIR = "원본";
    private static final String FILE_PREFIX = "6.";
    private static final Pattern SHEET_NAME_PATTERN = Pattern.compile("\\d{4}\\s*연료비\\s*사업계획");
    private static final String CELL_TABLE = "steam_cell_value";
    private static final String PAGE_TABLE = "energy_fuel_plan";

    /**
     * @param firstExcelRow 원본 시트의 첫 행 — 라벨을 못 찾았을 때의 폴백 좌표
     * @param labels        행 라벨 — 구분(B)이 있으면 그것을, 비어 있으면 대분류(A)
     */
    record TableMap(String tableId, int htmlRowCount, int firstExcelRow, List<String> labels) {}

    // 이 시트는 표 제목이 따로 없고 항목이 한 줄로 이어진다. 그래서 위에서 아래 순서대로
    // 직전에 찾은 행 다음부터 라벨을 찾는다("스팀생산량 LNG보일러" 처럼 같은 이름이 두 번 나온다).
    static final List<TableMap> TABLE_MAPS = List.of(
            new TableMap("t01", 14, 5,
                    List.of("제지 생산량", "화장지 생산량", "생산량 총계", "제지 생산원단위", "화장지 생산원단위", "제지 공정 스팀량", "화장지 공정 스팀량", "공정 총 사용량", "기타 스팀사용량(제지)", "기타 스팀사용량(화장지)", "제지 총 사용량", "화장지 총 사융량", "제지 총 원단위", "화장지 총 원단위")),
            new TableMap("t02", 6, 19,
                    List.of("디스퍼져사용량", "환기시스템", "RO 열교환기", "TOC재생설비", "기타(난방, 취사용 등)", "총 기타 사용량")),
            new TableMap("t03", 3, 25,
                    List.of("스팀 총 사용량", "가동일수", "스팀 사용량")),
            new TableMap("t04", 6, 28,
                    List.of("LNG보일러", "폐합성소각로", "복합보일러", "외부보일러", "유동상소각로", "스팀 총 생산량")),
            new TableMap("t05", 6, 34,
                    List.of("LNG보일러", "폐합성소각로", "복합보일러", "외부보일러", "유동상소각로", "스팀 총 생산량")),
            new TableMap("t06", 10, 40,
                    List.of("LNG 보일러", "복합 LNG", "화장지3 LNG", "화장지4 LNG", "화장지5 LNG", "화장지 LNG소계", "제지3 LNG", "LNG(보일러용)소계", "LNG(버너용)소계", "LNG  총 사용량")),
            new TableMap("t07", 5, 50,
                    List.of("LNG 단가", "소각로", "복합(기본)", "외부(기본)", "유동상소각로")),
            new TableMap("t08", 6, 55,
                    List.of("LNG 비용", "소각로 운영비", "복합보일러", "외부보일러", "유동상소각로", "운영비 총 합계")),
            new TableMap("t09", 8, 62,
                    List.of("LNG 비용", "복합보일러", "외부보일러", "외부 선급금 차감", "외부페널티 차감", "폐합성소각로", "유동상소각로", "연료비 총 합계")),
            new TableMap("t10", 2, 70,
                    List.of("연료비 원단위", "연료비 원단위"))
    );

    /** 라벨 열 — 구분(B)이 우선, 비어 있으면 대분류(A). */
    static final int[] LABEL_COLS = {1, 2};
    /** 데이터가 시작되는 행. */
    static final int FIRST_DATA_ROW = 4;

    private final TableService tableService;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);
    private volatile Path cachedWorkbookPath;

    public EnergyFuelPlanWorkbookService(TableService tableService) {
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
        Map<String, Integer> rows = resolveRows(sheet);
        for (TableMap map : TABLE_MAPS) {
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                Integer excelRowOneBased = rows.get(map.tableId() + "|" + htmlRow);
                if (excelRowOneBased == null) continue;   // 원본에 없는 항목 — 건너뛴다
                Row row = sheet.getRow(excelRowOneBased - 1);
                if (row == null) continue;
                for (int colIndex = 1; colIndex <= 12; colIndex += 1) {
                    int excelColZeroBased = colIndex + 3;
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
        Map<String, Integer> rows = resolveRows(sheet);
        for (TableMap map : TABLE_MAPS) {
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                Integer excelRowOneBased = rows.get(map.tableId() + "|" + htmlRow);
                if (excelRowOneBased == null) continue;   // 원본에 없는 항목 — 건너뛴다
                for (int colIndex = 1; colIndex <= 12; colIndex += 1) {
                    String key = rowKey(map.tableId(), htmlRow) + "|" + colIndex;
                    String value = cells.get(key);
                    if (value == null) continue;
                    int excelColZeroBased = colIndex + 3;
                    setNonFormulaCellNumber(sheet, excelRowOneBased, excelColZeroBased, value);
                }
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

    /**
     * 정책: 일반 숫자 + 수식 셀의 마지막 계산(캐시)값을 import.
     *  - 사업계획대비 분석 등에서 계획 생산량(외부참조)·계획값을 가져올 수 있도록 캐시값 보존.
     *  - 페이지의 계산 행은 JS 가 다시 계산하므로 캐시값을 import 해도 무방(표시는 JS 가 덮어씀).
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
        // 작업 디렉터리에 기대지 않고 '원본' 폴더를 찾아 올라간다 (OriginalWorkbookFiles 주석 참고)
        Path resolved = OriginalWorkbookFiles.find(
                name -> name.startsWith(FILE_PREFIX) && name.endsWith(".xlsx"), "에너지 회계비용");
        cachedWorkbookPath = resolved;
        return resolved;
    }

    /**
     * 위에서 아래 순서대로 행 라벨을 찾는다. 직전에 찾은 행 다음부터 훑으므로
     * 같은 이름이 여러 번 나와도 섞이지 않는다.
     * 못 찾은 항목은 넣지 않는다 — 좌표로 폴백하면 다른 제목 줄에 값이 붙기 때문이다.
     */
    Map<String, Integer> resolveRows(Sheet sheet) {
        EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, 0);
        Map<String, Integer> rows = new LinkedHashMap<>();
        int cursor = FIRST_DATA_ROW;
        for (TableMap map : TABLE_MAPS) {
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                String label = map.labels().get(htmlRow - 1);
                Integer found = index.rowOrNull(cursor, 0, map.tableId() + " " + label, label);
                if (found == null) continue;
                rows.put(map.tableId() + "|" + htmlRow, found);
                cursor = found + 1;
            }
        }
        return rows;
    }

    private String rowKey(String tableId, int htmlRow) {
        return tableId + "|r" + String.format("%02d", htmlRow);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
