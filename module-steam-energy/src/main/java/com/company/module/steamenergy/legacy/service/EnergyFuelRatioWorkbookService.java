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
    private static final String CELL_TABLE = "steam_cell_value";
    private static final String PAGE_TABLE = "energy_fuel_ratio";

    /**
     * @param firstExcelRow 원본 시트의 첫 행 — 제목/라벨을 못 찾았을 때의 폴백 좌표
     * @param title         표 제목(앞부분만 비교, 맨 앞 연도는 무시)
     * @param labels        표 안 행 라벨 — A열, 비어 있으면 B열
     */
    record TableMap(String tableId, int htmlRowCount, int firstExcelRow, String title, List<String> labels) {}

    // 제목으로 표 블록을 잡고, 그 안에서 행 라벨로 줄을 찾는다.
    // (예전에는 firstExcelRow + (htmlRow-1) 로만 계산해 원본에 행이 끼면 통째로 밀렸다.
    //  t11 의 "헤더 한 줄 건너뛰기" 예외도 라벨로 찾으면서 없어졌다.)
    static final List<TableMap> TABLE_MAPS = List.of(
            new TableMap("t01", 9, 4, "2025년 스팀생산량 (톤)",
                    List.of("LNG 보일러", "복합보일러", "폐합성소각로", "유동상소각로", "외부보일러", "합 계", "유동상 밴트량 (톤)", "복합 페널티량", "복합 보증량")),
            new TableMap("t02", 8, 16, "2025년 보일러 연료비 (백만원)",
                    List.of("LNG 보일러", "복합보일러 (LNG, 페널티 포함)", "폐합성소각로", "유동상소각로 (잉여포함)", "외부보일러", "합 계", "유동상 소내사용+밴트스팀비용", "복합보일러 페널티 비용")),
            new TableMap("t03", 6, 27, "2025년 스팀단가 (천원/톤.스팀)",
                    List.of("LNG 보일러", "복합보일러", "폐합성소각로", "유동상소각로", "외부보일러", "합 계")),
            new TableMap("t04", 12, 39, "2025 호기별 연료비배분 (백만원)",
                    List.of("제지 2호기", "제지 3호기", "제지 소계", "화장지 3호기", "화장지 4호기", "화장지 5호기", "화장지 소계", "합 계", "총 에너지비용", "스팀생산량", "스팀사용단가", "제지배분비율")),
            new TableMap("t05", 12, 53, "2025년 스팀사용량",
                    List.of("제지 2호기", "제지 3호기", "제지 소계", "화장지 3호기", "화장지 4호기", "화장지 5호기", "화장지 소계", "합 계", "총 생산스팀량", "기타 사용량", "제지 스팀사용원가", "화장지 스팀사용원가")),
            new TableMap("t06", 8, 68, "2025년 제품생산량 (kg)",
                    List.of("제지2", "제지3", "제지 소계", "화장지3", "화장지4", "화장지5", "화장지 소계", "제품생산량")),
            new TableMap("t07", 8, 79, "2025년 스팀 연료비 (백만원) - 버너 LNG사용금액 제외",
                    List.of("제지2", "제지3", "제지 소계", "화장지3", "화장지4", "화장지5", "화장지 소계", "합 계")),
            new TableMap("t08", 8, 90, "2025년 연료 원단위 (천원/톤.제품)",
                    List.of("연료단가", "제지2", "제지3", "제지 소계", "화장지3", "화장지4", "화장지5", "화장지 소계")),
            new TableMap("t09", 8, 101, "2025년 스팀 톤당 연료비(천원/톤.스팀) - 연료비/스팀사용량",
                    List.of("제지2", "제지3", "제지 소계", "화장지3", "화장지4", "화장지5", "화장지 소계", "합  계")),
            new TableMap("t10", 6, 112, "2025년 버너 LNG 사용량",
                    List.of("제지3", "화장지3", "화장지4", "화장지5", "화장지 소계", "합 계")),
            new TableMap("t11", 9, 120, "2025년 버너 LNG 비용",
                    List.of("24년 LNG 단가", "25년 LNG 단가", "증감률(%)", "제지3", "화장지3", "화장지4", "화장지5", "화장지 소계", "합 계")),
            new TableMap("t12", 5, 133, "2025년 버너 LNG 사용량 원단위",
                    List.of("제지3", "화장지3", "화장지4", "화장지5", "화장지 소계")),
            new TableMap("t13", 5, 141, "2025년 버너 LNG 사용비용 원단위",
                    List.of("제지3", "화장지3", "화장지4", "화장지5", "화장지 소계")),
            new TableMap("t14", 8, 149, "2025년 총 연료단가 (천원/톤.제품) - 총 연료비/제품생산량",
                    List.of("제지2", "제지3", "제지 소계", "화장지3", "화장지4", "화장지5", "화장지 소계", "합  계")),
            new TableMap("t15", 7, 160, "2025년 스팀사용량 원단위 (톤.스팀/톤.제품)",
                    List.of("제지2", "제지3", "제지 소계", "화장지3", "화장지4", "화장지5", "화장지 소계")),
            new TableMap("t16", 8, 170, "2025 호기별 스팀사용비율 - 호기별 배분",
                    List.of("제지 2호기", "제지 3호기", "제지 소계", "화장지 3호기", "화장지 4호기", "화장지 5호기", "화장지 소계", "합 계")),
            new TableMap("t17", 7, 181, "2025년 스팀사용량 원단위 (톤.스팀/톤.제품) - 기타 스팀량 포함",
                    List.of("제지2", "제지3", "제지 소계", "화장지3", "화장지4", "화장지5", "화장지 소계"))
    );

    /** 라벨 열 — A열이 우선, 비어 있으면 B열. */
    static final int[] LABEL_COLS = {2, 1};

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
        Map<String, Integer> rows = resolveRows(sheet);
        for (TableMap map : TABLE_MAPS) {
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                Integer excelRowOneBased = rows.get(map.tableId() + "|" + htmlRow);
                if (excelRowOneBased == null) continue;   // 원본에 없는 항목 — 건너뛴다
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
        Map<String, Integer> rows = resolveRows(sheet);
        for (TableMap map : TABLE_MAPS) {
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                Integer excelRowOneBased = rows.get(map.tableId() + "|" + htmlRow);
                if (excelRowOneBased == null) continue;   // 원본에 없는 항목
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
    /**
     * 표 제목으로 블록을 잡고 그 안에서 행 라벨로 줄을 찾는다.
     * 못 찾은 항목은 넣지 않는다 — 좌표로 폴백하면 다른 제목 줄에 값이 붙기 때문이다.
     */
    Map<String, Integer> resolveRows(Sheet sheet) {
        EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS, 1, 0);
        Map<String, Integer> rows = new LinkedHashMap<>();
        int sheetCursor = 1;
        for (TableMap map : TABLE_MAPS) {
            Integer titleRow = index.titleRowOrNull(sheetCursor, map.title());
            if (titleRow == null) continue;          // 표가 통째로 없는 경우
            sheetCursor = titleRow + 1;
            int cursor = titleRow + 1;
            for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                String label = map.labels().get(htmlRow - 1);
                Integer found = index.rowOrNull(cursor, 0, map.tableId() + " " + label, label);
                if (found == null) continue;
                rows.put(map.tableId() + "|" + htmlRow, found);
                cursor = found + 1;
                sheetCursor = found + 1;
            }
        }
        return rows;
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
        // 작업 디렉터리에 기대지 않고 '원본' 폴더를 찾아 올라간다 (OriginalWorkbookFiles 주석 참고)
        Path resolved = OriginalWorkbookFiles.find(
                name -> name.startsWith(FILE_PREFIX) && name.endsWith(".xlsx"), "에너지 회계비용");
        cachedWorkbookPath = resolved;
        return resolved;
    }

    private String rowKey(String tableId, int htmlRow) {
        return tableId + "|r" + String.format("%02d", htmlRow);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
