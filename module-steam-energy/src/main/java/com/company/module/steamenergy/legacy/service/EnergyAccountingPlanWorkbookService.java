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
    private static final String CELL_TABLE = "steam_cell_value";
    private static final String PAGE_TABLE = "energy_accounting_plan";
    private static final int HTML_ROW_COUNT = 32;
    private static final int HTML_TO_EXCEL_ROW_OFFSET = 2; // HTML r01 → Excel row 3 (이름을 못 찾을 때의 폴백)

    /**
     * 화면 행(r01~r32) 에 대응하는 <b>원본 시트의 항목 이름</b>.
     * 구분 열(B)에 적힌 글자를 그대로 쓰되, 소계 행처럼 B 가 비어 있으면 대분류 열(A)의 글자다.
     * 위치(행 번호)가 아니라 이 이름으로 찾으므로 원본에 행이 끼어들어도 밀리지 않는다.
     */
    private static final String[] ROW_LABELS = {
            "복합보일러 스팀구매",            // r01
            "제스코 스팀구매",               // r02
            "LNG 구매",                   // r03
            "폐합성소각로",                  // r04
            "유동상소각로",                  // r05
            "연료비 월별",                  // r06
            "제스코 상계비 (용수사용 등)",      // r07
            "제스코 페널티",                 // r08
            "제스코 선급금 상환",             // r09
            "복합 LNG, 용수사용량",          // r10
            "복합 투자비 감가상각",            // r11
            "복합보일러 처분수익 연료비 차감분",   // r12
            "토지 지상권 지료",               // r13
            "차감비 소계",                   // r14
            "연료비(회계비용) 실적",           // r15  (A열)
            "제지 연료비(스팀사용비율)",        // r16
            "생활 연료비(스팀사용비율)",        // r17
            "연료비(회계비용) 계획금액",        // r18  (A열)
            "회계비용 대비 증감",             // r19  (A열)
            "전월대비 증감",                 // r20  (A열)
            "생산량",                      // r21  (A열)
            "제지 제품생산량 (24년 반영)",     // r22
            "화장지 제품생산량 (24년 반영)",    // r23
            "총 원단위 (연료비/생산량)",       // r24  (A열)
            "제지 원단위(연료비/생산량)",       // r25
            "생활 원단위(연료비/생산량)",       // r26
            "전월대비 손익증감",              // r27  (A열)
            "제지손익",                     // r28
            "생활손익",                     // r29
            "손익합계(제지+생활)",            // r30
            "스팀생산량",                    // r31
            "LNG 직버너 사용 비용"           // r32
    };

    /** 라벨이 있는 열 — 뒤쪽(구분 B)이 우선이고, 비어 있으면 A 를 쓴다. */
    private static final int[] LABEL_COLS = {1, 2};
    /** "1월"~"12월" 이 적힌 머리글 행. */
    private static final int MONTH_HEADER_ROW = 2;


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
        EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS,
                HTML_TO_EXCEL_ROW_OFFSET + 1, MONTH_HEADER_ROW);
        for (int htmlRow = 1; htmlRow <= HTML_ROW_COUNT; htmlRow += 1) {
            Integer excelRowOneBased = resolveRow(index, htmlRow);
            if (excelRowOneBased == null) continue;   // 원본에 없는 항목
            Row row = sheet.getRow(excelRowOneBased - 1);
            if (row == null) continue;
            for (int colIndex = 1; colIndex <= 13; colIndex += 1) {
                int excelColZeroBased = resolveMonthCol(index, colIndex);
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
        EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS,
                HTML_TO_EXCEL_ROW_OFFSET + 1, MONTH_HEADER_ROW);
        for (int htmlRow = 1; htmlRow <= HTML_ROW_COUNT; htmlRow += 1) {
            Integer excelRowOneBased = resolveRow(index, htmlRow);
            if (excelRowOneBased == null) continue;   // 원본에 없는 항목
            for (int colIndex = 1; colIndex <= 13; colIndex += 1) {
                String key = rowKey(htmlRow) + "|" + colIndex;
                String value = cells.get(key);
                if (value == null) continue;
                int excelColZeroBased = resolveMonthCol(index, colIndex);
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
        // 작업 디렉터리에 기대지 않고 '원본' 폴더를 찾아 올라간다 (OriginalWorkbookFiles 주석 참고)
        Path resolved = OriginalWorkbookFiles.find(
                name -> name.startsWith(FILE_PREFIX) && name.endsWith(".xlsx"), "에너지 회계비용");
        cachedWorkbookPath = resolved;
        return resolved;
    }

    /** 화면 행 → 원본 시트 행. 이름으로 찾고, 못 찾으면 기존 offset 으로 폴백한다. */
    private Integer resolveRow(EnergySheetIndex index, int htmlRow) {
        int declared = htmlRow + HTML_TO_EXCEL_ROW_OFFSET;
        String label = htmlRow <= ROW_LABELS.length ? ROW_LABELS[htmlRow - 1] : null;
        if (label == null) return declared;
        // 이름을 못 찾으면 null — 호출부가 그 항목을 건너뛴다(다른 제목 줄에 붙이지 않는다)
        return index.rowOrNull(0, declared, label, label);
    }

    /**
     * 화면 열(1=전년 12월, 2~13=1~12월) → 원본 시트 열(0-based).
     * 머리글에서 찾고, 못 찾으면 기존 계산식으로 폴백한다.
     */
    private int resolveMonthCol(EnergySheetIndex index, int colIndex) {
        int declaredZeroBased = colIndex + 1;
        int month = colIndex == 1 ? 0 : colIndex - 1;
        return index.monthColumn(month, declaredZeroBased + 1) - 1;
    }

    private String rowKey(int htmlRow) {
        return "r" + String.format("%02d", htmlRow);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
