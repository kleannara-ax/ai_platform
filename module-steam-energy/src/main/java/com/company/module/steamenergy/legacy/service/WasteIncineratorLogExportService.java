package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Layout;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.SheetMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 폐합성소각로 운전일지 엑셀 다운로드.
 *
 * <p>{@code 원본} 폴더의 운전일지 파일을 서식 그대로 틀로 쓰고, 선택한 월의 DB 값을 채워 내려준다.
 * 채우는 위치는 업로드와 같은 {@link WasteIncineratorLogCellMap} 매핑표를 반대 방향으로 사용한다.
 * (유동상 다운로드 {@link FluidizedDailyLogExportService} 와 같은 방식)
 *
 * <p>템플릿의 수식 셀(합계·평균·적산량·누계·재고 2일 이후·투입비)은 건드리지 않는다.
 * 엑셀에서 열면 채워 넣은 입력값으로 다시 계산된다.
 */
@Service
public class WasteIncineratorLogExportService {

    private static final String ORIGINAL_DIR = "원본";
    private static final String TEMPLATE_KEYWORD = "폐합성소각로운전일지";
    private static final String LOG_TABLE = "waste_incin_log";
    /** 제목의 "(07월)" 부분 — 내려받는 월로 바꿔 준다. */
    private static final Pattern TITLE_MONTH = Pattern.compile("\\(\\s*\\d{1,2}\\s*월\\s*\\)");

    private final TableService tableService;

    public WasteIncineratorLogExportService(TableService tableService) {
        this.tableService = tableService;
    }

    public byte[] exportWorkbook(int year, int month) throws IOException {
        String monthKey = String.format("%04d-%02d", year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        Map<String, String> cells = loadMonthCells(monthKey);

        Path template = resolveTemplatePath();
        try (InputStream inputStream = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (SheetMap sheetMap : WasteIncineratorLogCellMap.SHEETS) {
                Sheet sheet = workbook.getSheet(sheetMap.sheetName());
                if (sheet == null) continue;
                writeTitleMonth(sheet, sheetMap, month);
                writeSheet(sheet, sheetMap, cells, monthDays);
            }

            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public String fileName(int year, int month) {
        return String.format("폐합성소각로 운전일지 (%d년 %d월).xlsx", year, month);
    }

    // ── 시트 채우기 ─────────────────────────────────────────────────
    private void writeSheet(Sheet sheet, SheetMap sheetMap, Map<String, String> cells, int monthDays) {
        boolean daysInColumns = sheetMap.layout() == Layout.DAYS_IN_COLUMNS;

        // 템플릿은 31일까지 있으므로, 그 달에 없는 날짜 칸은 비운다(지난달 값이 남지 않게).
        for (int day = 1; day <= 31; day += 1) {
            boolean inMonth = day <= monthDays;
            for (Entry entry : sheetMap.entries()) {
                if (entry.firstDay() && day != 1) continue;

                int rowNumber = daysInColumns
                        ? WasteIncineratorLogCellMap.parseRow(entry.line())
                        : sheetMap.firstDayRow() + day - 1;
                int colNumber = daysInColumns
                        ? sheetMap.firstDayCol() + day - 1
                        : WasteIncineratorLogCellMap.parseCol(entry.line());

                Cell target = cellAt(sheet, rowNumber, colNumber, inMonth);
                if (target == null || target.getCellType() == CellType.FORMULA) continue;

                String value = inMonth
                        ? cells.get(key(String.valueOf(day), entry.colIndex()))
                        : null;

                if (value == null || value.isBlank()) {
                    target.setBlank();
                } else if (entry.text()) {
                    target.setCellValue(value);
                } else {
                    writeTextOrNumber(target, value);
                }
            }
        }
    }

    /** 제목 셀의 "(07월)" 을 내려받는 월로 바꾼다. */
    private void writeTitleMonth(Sheet sheet, SheetMap sheetMap, int month) {
        Cell title = cellAt(sheet,
                WasteIncineratorLogCellMap.parseRow(sheetMap.titleRef()),
                WasteIncineratorLogCellMap.parseCol(sheetMap.titleRef()), false);
        if (title == null || title.getCellType() != CellType.STRING) return;

        String text = title.getStringCellValue();
        if (text == null || text.isBlank()) return;

        Matcher matcher = TITLE_MONTH.matcher(text);
        String replacement = String.format("(%02d월)", month);
        title.setCellValue(matcher.find() ? matcher.replaceFirst(replacement) : text + " " + replacement);
    }

    // ── DB 읽기 ─────────────────────────────────────────────────────
    private Map<String, String> loadMonthCells(String monthKey) {
        Map<String, String> cells = new HashMap<>();
        for (Map<String, Object> row : tableService.listAllByMonth("table_cell_value", monthKey)) {
            if (!LOG_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            Object value = row.get("cell_value");
            if (value == null) continue;
            cells.put(key(String.valueOf(row.get("row_key")), toInt(row.get("col_index"))), String.valueOf(value));
        }
        return cells;
    }

    private String key(String rowKey, int colIndex) {
        return rowKey + "|" + colIndex;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    // ── 셀 쓰기 도우미 ──────────────────────────────────────────────
    private Cell cellAt(Sheet sheet, int rowNumber, int colNumber, boolean create) {
        if (rowNumber < 1 || colNumber < 1) return null;
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) {
            if (!create) return null;
            row = sheet.createRow(rowNumber - 1);
        }
        Cell target = row.getCell(colNumber - 1);
        if (target == null && create) target = row.createCell(colNumber - 1);
        return target;
    }

    private void writeTextOrNumber(Cell target, String value) {
        Double number = parse(value);
        if (number == null) target.setCellValue(value);
        else target.setCellValue(number);
    }

    private Double parse(String value) {
        if (value == null) return null;
        String text = value.replace(",", "").trim();
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 원본 폴더 위치. 실행 위치가 app 하위일 수 있어 상위 폴더까지 훑는다. */
    private Path resolveOriginalDir() throws IOException {
        Path base = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && base != null; depth += 1) {
            Path candidate = base.resolve(ORIGINAL_DIR);
            if (Files.isDirectory(candidate)) return candidate;
            base = base.getParent();
        }
        throw new IOException("'" + ORIGINAL_DIR + "' 폴더를 찾지 못했습니다.");
    }

    /** 원본 폴더에서 폐합성소각로 운전일지 서식 파일을 찾는다 (백업본·임시파일 제외). */
    private Path resolveTemplatePath() throws IOException {
        Path dir = resolveOriginalDir();
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        String lower = name.toLowerCase();
                        return name.replace(" ", "").contains(TEMPLATE_KEYWORD)
                                && !name.contains("백업")
                                && !name.startsWith("~$")
                                && (lower.endsWith(".xlsx") || lower.endsWith(".xlsm"));
                    })
                    .findFirst();
            if (found.isEmpty()) {
                throw new IOException("원본 폴더에서 '" + TEMPLATE_KEYWORD + "' 서식 파일을 찾지 못했습니다.");
            }
            return found.get();
        }
    }
}
