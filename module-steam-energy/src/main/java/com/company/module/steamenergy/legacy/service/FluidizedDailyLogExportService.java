package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.PowerUsage;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Store;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 유동상 운전일지 엑셀 다운로드.
 *
 * <p>{@code 원본} 폴더의 운전일지 파일을 서식 그대로 틀로 쓰고, 선택한 월의 DB 값을 채워 내려준다.
 * 채우는 위치는 업로드와 같은 {@link FluidizedDailyLogCellMap} 매핑표를 반대 방향으로 사용한다.
 *
 * <p>템플릿의 수식 셀(재고·누계·합계·전일지침 등)은 건드리지 않는다.
 * 엑셀에서 열면 채워 넣은 입력값으로 다시 계산된다.
 */
@Service
public class FluidizedDailyLogExportService {

    private static final String ORIGINAL_DIR = "원본";
    private static final String TEMPLATE_KEYWORD = "유동상 운전일지";
    private static final String DETAIL_TABLE = "fluidized_detail_main";
    private static final String FLOW_TABLE = "flow_m_fluid_incinerator";
    private static final String LOG_TABLE = "fluidized_daily_log";
    private static final String MOISTURE_SHEET = "함수율";

    private final TableService tableService;

    public FluidizedDailyLogExportService(TableService tableService) {
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

            for (int day = 1; day <= 31; day += 1) {
                Sheet sheet = workbook.getSheet(String.valueOf(day));
                if (sheet == null) continue;
                if (day > monthDays) {
                    clearDaySheet(sheet);
                    continue;
                }
                writeDaySheet(sheet, cells, monthKey, year, month, day);
            }

            Sheet moisture = workbook.getSheet(MOISTURE_SHEET);
            if (moisture != null) writeMoistureSheet(moisture, cells, year, month, monthDays);

            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public String fileName(int year, int month) {
        return String.format("유동상 운전일지 (%d년 %d월).xlsx", year, month);
    }

    // ── 하루치 시트 ─────────────────────────────────────────────────
    private void writeDaySheet(Sheet sheet, Map<String, String> cells, String monthKey, int year, int month, int day) {
        setDate(cell(sheet, FluidizedDailyLogCellMap.DATE_REF, true), LocalDate.of(year, month, day));

        for (Entry entry : FluidizedDailyLogCellMap.DAY_CELLS) {
            Cell target = cell(sheet, entry.ref(), true);
            if (target == null || target.getCellType() == CellType.FORMULA) continue; // 수식은 그대로 둔다
            String value = lookup(cells, entry, monthKey, day);
            if (value == null || value.isBlank()) {
                target.setBlank();
                continue;
            }
            if (entry.text()) {
                writeTextOrNumber(target, value);
                continue;
            }
            Double number = parse(value);
            if (number == null) {
                target.setCellValue(value);
                continue;
            }
            target.setCellValue(entry.scale() == 0 ? number : number / entry.scale());
        }
    }

    /** 해당 항목의 DB 값 (업로드 매핑의 반대 방향) */
    private String lookup(Map<String, String> cells, Entry entry, String monthKey, int day) {
        return switch (entry.store()) {
            case DETAIL -> cells.get(key(DETAIL_TABLE, columnLabel(day + 2) + entry.target(), 0));
            case FLOW -> cells.get(key(FLOW_TABLE, String.format("%02d", day), 0));
            case LOG -> cells.get(key(LOG_TABLE, String.valueOf(day), entry.target()));
        };
    }

    /** 그 달에 없는 날짜(30·31일 등)의 시트는 입력칸을 비운다. */
    private void clearDaySheet(Sheet sheet) {
        for (Entry entry : FluidizedDailyLogCellMap.DAY_CELLS) {
            Cell target = cell(sheet, entry.ref(), false);
            if (target != null && target.getCellType() != CellType.FORMULA) target.setBlank();
        }
        for (PowerUsage usage : FluidizedDailyLogCellMap.POWER_USAGE) {
            Cell prev = cell(sheet, usage.prevRef(), false);
            if (prev != null && prev.getCellType() != CellType.FORMULA) prev.setBlank();
        }
    }

    // ── 함수율 시트 ─────────────────────────────────────────────────
    private void writeMoistureSheet(Sheet sheet, Map<String, String> cells, int year, int month, int monthDays) {
        for (int day = 1; day <= 31; day += 1) {
            int baseRow = FluidizedDailyLogCellMap.MOISTURE_FIRST_ROW
                    + (day - 1) * FluidizedDailyLogCellMap.MOISTURE_ROWS_PER_DAY;
            boolean inMonth = day <= monthDays;

            Cell dateCell = cell(sheet, FluidizedDailyLogCellMap.MOISTURE_DATE_COL + baseRow, inMonth);
            if (dateCell != null) {
                if (inMonth) setDate(dateCell, LocalDate.of(year, month, day));
                else if (dateCell.getCellType() != CellType.FORMULA) dateCell.setBlank();
            }

            for (int shift = 0; shift < FluidizedDailyLogCellMap.MOISTURE_ROWS_PER_DAY; shift += 1) {
                int rowNumber = baseRow + shift;
                for (int machine = 0; machine < FluidizedDailyLogCellMap.MOISTURE_TIME_COLS.length; machine += 1) {
                    String time = inMonth
                            ? cells.get(key(LOG_TABLE, String.valueOf(day), 200 + shift * 10 + machine * 2))
                            : null;
                    String percent = inMonth
                            ? cells.get(key(LOG_TABLE, String.valueOf(day), 201 + shift * 10 + machine * 2))
                            : null;

                    Cell timeCell = cell(sheet, FluidizedDailyLogCellMap.MOISTURE_TIME_COLS[machine] + rowNumber, inMonth);
                    if (timeCell != null && timeCell.getCellType() != CellType.FORMULA) {
                        if (time == null || time.isBlank()) timeCell.setBlank();
                        else writeTime(timeCell, time);
                    }

                    Cell valueCell = cell(sheet, FluidizedDailyLogCellMap.MOISTURE_VALUE_COLS[machine] + rowNumber, inMonth);
                    if (valueCell != null && valueCell.getCellType() != CellType.FORMULA) {
                        Double ratio = parse(percent);
                        // 화면은 %(61.61), 원본 시트는 비율(0.6161)
                        if (ratio == null) valueCell.setBlank();
                        else valueCell.setCellValue(ratio / 100d);
                    }
                }
            }
        }
    }

    // ── DB 읽기 ─────────────────────────────────────────────────────
    private Map<String, String> loadMonthCells(String monthKey) {
        Map<String, String> cells = new HashMap<>();
        for (Map<String, Object> row : tableService.listAllByMonth("table_cell_value", monthKey)) {
            String tableName = String.valueOf(row.get("table_name"));
            if (!DETAIL_TABLE.equals(tableName) && !FLOW_TABLE.equals(tableName) && !LOG_TABLE.equals(tableName)) {
                continue;
            }
            Object value = row.get("cell_value");
            if (value == null) continue;
            cells.put(key(tableName, String.valueOf(row.get("row_key")), toInt(row.get("col_index"))), String.valueOf(value));
        }
        return cells;
    }

    private String key(String tableName, String rowKey, int colIndex) {
        return tableName + "|" + rowKey + "|" + colIndex;
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
    private Cell cell(Sheet sheet, String ref, boolean create) {
        int rowIndex = FluidizedDailyLogCellMap.parseRow(ref) - 1;
        int colIndex = FluidizedDailyLogCellMap.parseCol(ref) - 1;
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            if (!create) return null;
            row = sheet.createRow(rowIndex);
        }
        Cell target = row.getCell(colIndex);
        if (target == null && create) target = row.createCell(colIndex);
        return target;
    }

    private void writeTextOrNumber(Cell target, String value) {
        Double number = parse(value);
        if (number == null) target.setCellValue(value);
        else target.setCellValue(number);
    }

    /** "HH:MM" 은 원본 시트의 시간 서식에 맞게 시간값으로 넣는다. 형식이 다르면 글자 그대로. */
    private void writeTime(Cell target, String value) {
        String text = value.trim();
        int colon = text.indexOf(':');
        if (colon > 0) {
            try {
                int hour = Integer.parseInt(text.substring(0, colon).trim());
                int minute = Integer.parseInt(text.substring(colon + 1).trim());
                if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                    target.setCellValue((hour * 60 + minute) / 1440d);
                    return;
                }
            } catch (NumberFormatException ignored) {
                // 형식이 다르면 아래에서 글자 그대로 저장
            }
        }
        target.setCellValue(text);
    }

    private void setDate(Cell target, LocalDate date) {
        if (target == null || target.getCellType() == CellType.FORMULA) return;
        target.setCellValue(date);
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

    private String columnLabel(int index) {
        StringBuilder label = new StringBuilder();
        int remaining = index;
        while (remaining > 0) {
            int mod = (remaining - 1) % 26;
            label.insert(0, (char) ('A' + mod));
            remaining = (remaining - 1) / 26;
        }
        return label.toString();
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

    /** 원본 폴더에서 운전일지 서식 파일을 찾는다 (백업본·임시파일 제외). */
    private Path resolveTemplatePath() throws IOException {
        Path dir = resolveOriginalDir();
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        String lower = name.toLowerCase();
                        return name.contains(TEMPLATE_KEYWORD)
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

    public List<String> mappedRefs() {
        return FluidizedDailyLogCellMap.mappedRefs();
    }

    public Store storeOf(Entry entry) {
        return entry.store();
    }
}
