package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Anchor;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.PowerUsage;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Store;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 유동상 운전일지 엑셀(월 단위 통합본) 일괄 업로드.
 *
 * <p>매핑은 {@link FluidizedDailyLogCellMap} 의 셀 주소표 하나로만 정의한다.
 * 1~31 시트는 배치가 같아 시트 이름(=일자)만 구분하면 되고, 함수율 시트도 같은 표의 열 정의를 쓴다.
 * 시트 배치가 표와 다르면 값을 추정하지 않고 경고로 알린다.
 */
@Service
public class FluidizedDailyLogImportService {

    private static final String CELL_TABLE = "table_cell_value";
    private static final String DETAIL_TABLE = "fluidized_detail_main";
    private static final String FLOW_TABLE = "flow_m_fluid_incinerator";
    private static final String LOG_TABLE = "fluidized_daily_log";
    private static final String MOISTURE_SHEET = "함수율";

    private final TableService tableService;
    private final FluidizedDetailWorkbookService fluidizedDetailWorkbookService;
    private final DataFormatter dataFormatter = new DataFormatter();

    public FluidizedDailyLogImportService(
            TableService tableService,
            FluidizedDetailWorkbookService fluidizedDetailWorkbookService
    ) {
        this.tableService = tableService;
        this.fluidizedDetailWorkbookService = fluidizedDetailWorkbookService;
    }

    public Map<String, Object> importWorkbook(MultipartFile file, int year, int month) throws IOException {
        String monthKey = String.format("%04d-%02d", year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        YearMonth target = YearMonth.of(year, month);
        List<String> warnings = new ArrayList<>();
        int dayCount = 0;
        int cellCount = 0;
        int moistureCells = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            // 다른 도구로 저장된 파일은 수식의 캐시값이 없을 수 있다(전일재고·전일지침 등).
            // 그런 셀은 직접 계산해서 읽는다.
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Map<YearMonth, Integer> sheetMonths = new LinkedHashMap<>();
            Set<String> layoutMismatch = new LinkedHashSet<>();

            for (int day = 1; day <= monthDays; day += 1) {
                Sheet sheet = workbook.getSheet(String.valueOf(day));
                if (sheet == null) continue;

                LocalDate sheetDate = readDate(cellByRef(sheet, FluidizedDailyLogCellMap.DATE_REF));
                if (sheetDate != null) sheetMonths.merge(YearMonth.from(sheetDate), 1, Integer::sum);

                List<String> mismatched = checkLayout(sheet);
                if (!mismatched.isEmpty()) layoutMismatch.add(day + "일(" + String.join(", ", mismatched) + ")");

                int written = importDaySheet(sheet, evaluator, monthKey, year, month, day);
                if (written > 0) {
                    dayCount += 1;
                    cellCount += written;
                }
            }

            sheetMonths.forEach((sheetMonth, count) -> {
                if (sheetMonth.equals(target)) return;
                warnings.add("시트 날짜가 " + sheetMonth + " (" + count + "개 시트) 로 선택한 월 " + target + " 과 다릅니다.");
            });
            if (!layoutMismatch.isEmpty()) {
                warnings.add("양식 위치가 기준과 다른 시트가 있습니다. 해당 칸은 비어 있거나 잘못 들어갔을 수 있습니다 — "
                        + String.join(" / ", layoutMismatch));
            }

            Sheet moisture = workbook.getSheet(MOISTURE_SHEET);
            if (moisture == null) {
                warnings.add("'" + MOISTURE_SHEET + "' 시트가 없어 함수율은 건너뛰었습니다.");
            } else {
                moistureCells = importMoistureSheet(moisture, evaluator, monthKey, year, month, monthDays, target, warnings);
            }
        }

        fluidizedDetailWorkbookService.invalidateRenderCache(monthKey);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", monthKey);
        result.put("days", dayCount);
        result.put("cells", cellCount);
        result.put("moisture_cells", moistureCells);
        result.put("warnings", warnings);
        return result;
    }

    /** 매핑표의 앵커 셀과 실제 값을 대조해 어긋난 셀 주소를 돌려준다. */
    private List<String> checkLayout(Sheet sheet) {
        List<String> mismatched = new ArrayList<>();
        for (Anchor anchor : FluidizedDailyLogCellMap.DAY_ANCHORS) {
            String actual = textByRef(sheet, anchor.ref(), null).replace(" ", "");
            String expected = anchor.expected().replace(" ", "");
            if (!actual.startsWith(expected)) mismatched.add(anchor.ref());
        }
        return mismatched;
    }

    // ── 하루치 시트 ─────────────────────────────────────────────────
    private int importDaySheet(Sheet sheet, FormulaEvaluator evaluator, String monthKey, int year, int month, int day) {
        int written = 0;

        for (Entry entry : FluidizedDailyLogCellMap.DAY_CELLS) {
            Cell cell = cellByRef(sheet, entry.ref());
            if (entry.text()) {
                written += store(monthKey, year, month, day, entry.store(), entry.target(), readText(cell, evaluator));
                continue;
            }
            Double value = readNumber(cell, evaluator);
            if (value == null) continue;
            written += store(monthKey, year, month, day, entry.store(), entry.target(),
                    format(round(value * entry.scale())));
        }

        // 전력 사용량 = 금일지침 − 전일지침 (세부 운영내역 전력량 행)
        for (PowerUsage usage : FluidizedDailyLogCellMap.POWER_USAGE) {
            Double prev = readNumber(cellByRef(sheet, usage.prevRef()), evaluator);
            Double today = readNumber(cellByRef(sheet, usage.todayRef()), evaluator);
            if (prev == null && today == null) continue;
            double used = (today == null ? 0 : today) - (prev == null ? 0 : prev);
            written += store(monthKey, year, month, day, Store.DETAIL, usage.detailRow(), format(round(used)));
        }
        return written;
    }

    // ── 함수율 시트 ─────────────────────────────────────────────────
    private int importMoistureSheet(
            Sheet sheet, FormulaEvaluator evaluator, String monthKey, int year, int month, int monthDays,
            YearMonth target, List<String> warnings
    ) {
        int written = 0;
        boolean dateWarned = false;

        for (int day = 1; day <= monthDays; day += 1) {
            int baseRow = FluidizedDailyLogCellMap.MOISTURE_FIRST_ROW
                    + (day - 1) * FluidizedDailyLogCellMap.MOISTURE_ROWS_PER_DAY;

            LocalDate rowDate = readDate(cellByRef(sheet, FluidizedDailyLogCellMap.MOISTURE_DATE_COL + baseRow));
            if (!dateWarned && rowDate != null && !YearMonth.from(rowDate).equals(target)) {
                warnings.add("함수율 시트의 날짜가 " + YearMonth.from(rowDate) + " 로 선택한 월 " + target + " 과 다릅니다.");
                dateWarned = true;
            }

            for (int shift = 0; shift < FluidizedDailyLogCellMap.MOISTURE_ROWS_PER_DAY; shift += 1) {
                int rowNumber = baseRow + shift;
                for (int machine = 0; machine < FluidizedDailyLogCellMap.MOISTURE_TIME_COLS.length; machine += 1) {
                    String timeRef = FluidizedDailyLogCellMap.MOISTURE_TIME_COLS[machine] + rowNumber;
                    String valueRef = FluidizedDailyLogCellMap.MOISTURE_VALUE_COLS[machine] + rowNumber;
                    written += store(monthKey, year, month, day, Store.LOG,
                            200 + shift * 10 + machine * 2, textByRef(sheet, timeRef, evaluator));

                    Double ratio = readNumber(cellByRef(sheet, valueRef), evaluator);
                    // 원본은 비율(0.6161)로 저장돼 있고 화면은 %(61.61)로 다룬다.
                    String percent = ratio == null ? "" : format(round(ratio <= 1.5d ? ratio * 100 : ratio));
                    written += store(monthKey, year, month, day, Store.LOG,
                            201 + shift * 10 + machine * 2, percent);
                }
            }
        }
        return written;
    }

    // ── 저장 ────────────────────────────────────────────────────────
    private int store(String monthKey, int year, int month, int day, Store store, int target, String value) {
        if (value == null || value.isBlank()) return 0;
        return switch (store) {
            case DETAIL -> upsert(monthKey, year, month, DETAIL_TABLE, columnLabel(day + 2) + target, 0, value);
            case FLOW -> upsert(monthKey, year, month, FLOW_TABLE, String.format("%02d", day), 0, value);
            case LOG -> upsert(monthKey, year, month, LOG_TABLE, String.valueOf(day), target, value);
        };
    }

    private int upsert(String monthKey, int year, int month, String tableName, String rowKey, int colIndex, String value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", monthKey);
        payload.put("year_no", year);
        payload.put("month_no", month);
        payload.put("table_name", tableName);
        payload.put("row_key", rowKey);
        payload.put("col_index", colIndex);
        payload.put("cell_value", value);
        tableService.upsert(CELL_TABLE, payload);
        return 1;
    }

    // ── 셀 읽기 ─────────────────────────────────────────────────────
    private Cell cellByRef(Sheet sheet, String ref) {
        Row row = sheet.getRow(FluidizedDailyLogCellMap.parseRow(ref) - 1);
        return row == null ? null : row.getCell(FluidizedDailyLogCellMap.parseCol(ref) - 1);
    }

    private String textByRef(Sheet sheet, String ref, FormulaEvaluator evaluator) {
        return readText(cellByRef(sheet, ref), evaluator);
    }

    private String readText(Cell target, FormulaEvaluator evaluator) {
        if (target == null || target.getCellType() == CellType.BLANK) return "";
        if (target.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(target)) {
                LocalDateTime dateTime = target.getLocalDateTimeCellValue();
                return dateTime == null ? "" : String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute());
            }
            return format(target.getNumericCellValue());
        }
        if (target.getCellType() == CellType.FORMULA) {
            CellValue evaluated = evaluate(target, evaluator);
            if (evaluated != null) {
                return switch (evaluated.getCellType()) {
                    case NUMERIC -> format(evaluated.getNumberValue());
                    case STRING -> evaluated.getStringValue() == null ? "" : evaluated.getStringValue().trim();
                    default -> "";
                };
            }
            return switch (target.getCachedFormulaResultType()) {
                case NUMERIC -> format(target.getNumericCellValue());
                case STRING -> target.getStringCellValue() == null ? "" : target.getStringCellValue().trim();
                default -> "";
            };
        }
        String value = dataFormatter.formatCellValue(target);
        return value == null ? "" : value.trim();
    }

    private Double readNumber(Cell target, FormulaEvaluator evaluator) {
        if (target == null) return null;
        try {
            if (target.getCellType() == CellType.NUMERIC) return target.getNumericCellValue();
            if (target.getCellType() == CellType.FORMULA) {
                CellValue evaluated = evaluate(target, evaluator);
                if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                    return evaluated.getNumberValue();
                }
                if (target.getCachedFormulaResultType() == CellType.NUMERIC) {
                    return target.getNumericCellValue();
                }
            }
            String raw = readText(target, evaluator).replace(",", "").trim();
            if (raw.isEmpty()) return null;
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 수식 계산. 실패하면 null 을 돌려 캐시값으로 넘어가게 한다. */
    private CellValue evaluate(Cell target, FormulaEvaluator evaluator) {
        if (evaluator == null) return null;
        try {
            return evaluator.evaluate(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate readDate(Cell target) {
        if (target == null || target.getCellType() != CellType.NUMERIC || !DateUtil.isCellDateFormatted(target)) {
            return null;
        }
        LocalDateTime dateTime = target.getLocalDateTimeCellValue();
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private String format(Double value) {
        if (value == null) return "";
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) (double) value);
        }
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
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
}
