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
 * 에너지 회계비용 마감비교 (energy-month-compare) Excel ↔ DB 매핑.
 *
 * Excel sheet pattern : "회계비용\s*마감과\s*실마감\s*비교" (예: '회계비용 마감과 실마감 비교(2025)')
 * DB table            : energy_month_compare
 * Row key 형식         : {section}|{rowKey}  (section = fuel. 전력비·에너지 합산은 화면에서 제거돼 연동하지 않는다)
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
    private static final String CELL_TABLE = "steam_cell_value";
    private static final String PAGE_TABLE = "energy_month_compare";

    /**
     * @param excelRowOneBased 원본 시트 행 — 이름을 못 찾았을 때의 폴백 좌표
     * @param label            원본 시트에 적힌 항목 이름. 이걸로 행을 찾는다
     */
    record RowMap(String section, String rowKey, int excelRowOneBased, String label) {}

    // 위에서 아래 순서 그대로 둔다 — 직전에 찾은 행 다음부터 훑는다(resolveRowAfter).
    static final List<RowMap> ROW_MAPS = List.of(
            // -- fuel 섹션 --
            // r3 복합보일러 스팀구매 = SRF Boiler 인보이스 Summary(VK) TOTAL SERVICE CHARGES 연동값
            //    (화면이 인보이스에서 계산해 가져오므로 엑셀과 왕복하지 않는다)
            new RowMap("fuel", "zescoSteam",       4, "제스코 스팀구매"),
            new RowMap("fuel", "lngBuy",           5, "LNG 구매"),
            new RowMap("fuel", "pyhsynSteam",      6, "폐합성소각로 스팀구매"),
            new RowMap("fuel", "fluidSteam",       7, "유동상소각로 스팀구매"),
            // r8 fuelMonthly = formula
            new RowMap("fuel", "zescoOffset",      9, "제스코 상계비 (유틸리티)"),
            new RowMap("fuel", "zescoPenalty",    10, "제스코 페널티"),
            // r11 복합 LNG 사용량, 처분부담금 등 = SRF Boiler Client Invoice 청구금액 합계(전력 제외) 연동값
            new RowMap("fuel", "compositeInvest", 12, "복합 투자비 감가상각"),
            new RowMap("fuel", "compositeIncome", 13, "복합보일러 처분수익 연료비 차감분"),
            new RowMap("fuel", "landLease",       14, "토지 지상권 지료"),
            // r15 deductSubtotal = formula / r16 fuelActual = formula
            new RowMap("fuel", "paperFuel",       17, "제지 연료비(스팀사용비율)"),
            new RowMap("fuel", "livingFuel",      18, "생활 연료비(스팀사용비율)"),
            new RowMap("fuel", "fuelPlan",        19, "연료비(회계비용) SG 계획금액"),
            new RowMap("fuel", "diff",            20, "회계비용 대비 증감"),
            new RowMap("fuel", "monthDiff",       21, "전월대비 증감"),
            // r22 prodTotal = formula
            new RowMap("fuel", "paperProd",       23, "제지 제품생산량"),
            new RowMap("fuel", "tissueProd",      24, "화장지 제품생산량"),
            // r25~r27 unit = formula
            new RowMap("fuel", "profitDelta",     28, "전월대비 손익증감"),
            new RowMap("fuel", "paperProfit",     29, "제지손익"),
            new RowMap("fuel", "livingProfit",    30, "생활손익"),
            // r31 profitSum = formula
            new RowMap("fuel", "steamProd",       32, "스팀생산량"),
            new RowMap("fuel", "lngBurner",       33, "LNG 직버너 사용 비용")
            // r34~r36 = formula

            // 37행 이후(전력비·에너지 합산)는 연동하지 않는다 — 화면에서 뺀 항목이라
            // 엑셀에서 읽어 와도 쓰는 곳이 없다. 36행까지(연료비)만 왕복한다.
    );

    /** 라벨 열 - 구분(B)이 우선, 비어 있으면 대분류(A). */
    static final int[] LABEL_COLS = {1, 2};
    /** "1월"~"12월" 이 적힌 머리글 행. */
    static final int MONTH_HEADER_ROW = 2;
    static final int FIRST_DATA_ROW = 3;

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
        EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, MONTH_HEADER_ROW);
        Map<String, Integer> rows = resolveRows(index);
        for (RowMap map : ROW_MAPS) {
            Integer rowNumber = rows.get(dbRowKey(map));
            if (rowNumber == null) continue;   // 원본에 없는 항목
            Row row = sheet.getRow(rowNumber - 1);
            if (row == null) continue;
            for (int colIndex = 0; colIndex <= 13; colIndex += 1) {
                int excelColZeroBased = resolveMonthCol(index, colIndex);
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
        EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, MONTH_HEADER_ROW);
        Map<String, Integer> rows = resolveRows(index);
        for (RowMap map : ROW_MAPS) {
            Integer rowNumber = rows.get(dbRowKey(map));
            if (rowNumber == null) continue;   // 원본에 없는 항목
            for (int colIndex = 0; colIndex <= 13; colIndex += 1) {
                String key = dbRowKey(map) + "|" + colIndex;
                String value = cells.get(key);
                if (value == null) continue;
                int excelColZeroBased = resolveMonthCol(index, colIndex);
                setNonFormulaCellNumber(sheet, rowNumber, excelColZeroBased, value);
            }
        }
    }

    /* ── helpers ─────────────────────────────────────────────── */

    private String dbRowKey(RowMap m) { return m.section() + "|" + m.rowKey(); }

    /**
     * 매핑표 순서대로 항목 이름을 찾아 실제 행을 정한다.
     * 직전에 찾은 행 다음부터 훑으므로 연료비/전력비에 같은 이름이 있어도 섞이지 않는다.
     */
    private Map<String, Integer> resolveRows(EnergySheetIndex index) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        int cursor = FIRST_DATA_ROW;
        for (RowMap map : ROW_MAPS) {
            Integer found = index.rowOrNull(cursor, map.excelRowOneBased(), map.label(), map.label());
            if (found == null) continue;   // 원본에서 지워진 항목 — 건너뛴다(다른 줄에 붙이지 않는다)
            rows.put(dbRowKey(map), found);
            cursor = found + 1;            // 찾았을 때만 커서를 옮긴다
        }
        return rows;
    }

    /**
     * 화면 열 -> 원본 시트 열(0-based).
     * 0 = 전년 12월, 1~12 = 1~12월, 13 = 다음 해 1월(12월 오른쪽 칸).
     */
    private int resolveMonthCol(EnergySheetIndex index, int colIndex) {
        int declaredZeroBased = colIndex + 2;
        if (colIndex == 13) {
            int decemberOneBased = index.monthColumn(12, declaredZeroBased);
            return decemberOneBased == declaredZeroBased ? declaredZeroBased : decemberOneBased;
        }
        int month = colIndex == 0 ? 0 : colIndex;
        return index.monthColumn(month, declaredZeroBased + 1) - 1;
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
        // 작업 디렉터리에 기대지 않고 '원본' 폴더를 찾아 올라간다 (OriginalWorkbookFiles 주석 참고)
        Path resolved = OriginalWorkbookFiles.find(
                name -> name.startsWith(FILE_PREFIX) && name.endsWith(".xlsx"), "에너지 회계비용");
        cachedWorkbookPath = resolved;
        return resolved;
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
