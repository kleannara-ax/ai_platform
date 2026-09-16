package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Anchor;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Layout;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.SheetMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 폐합성소각로 운전일지 엑셀(월 단위 통합본) 일괄 업로드.
 *
 * <p>매핑은 {@link WasteIncineratorLogCellMap} 의 셀 위치표 하나로만 정의한다.
 * 시트 배치가 표와 다르면 값을 추정하지 않고 경고로 알린다(유동상과 같은 방식).
 */
@Service
public class WasteIncineratorLogImportService {

    private static final String CELL_TABLE = "table_cell_value";
    private static final String LOG_TABLE = "waste_incin_log";
    /** 제목의 "(07월)" 에서 월을 읽는다. */
    private static final Pattern TITLE_MONTH = Pattern.compile("(\\d{1,2})\\s*월");

    private final TableService tableService;
    private final DataFormatter dataFormatter = new DataFormatter();

    public WasteIncineratorLogImportService(TableService tableService) {
        this.tableService = tableService;
    }

    public Map<String, Object> importWorkbook(MultipartFile file, int year, int month) throws IOException {
        String monthKey = String.format("%04d-%02d", year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        List<String> warnings = new ArrayList<>();
        List<String> missingSheets = new ArrayList<>();
        List<String> monthMismatch = new ArrayList<>();
        List<String> layoutMismatch = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        // 같은 col_index 를 쓰는 시트가 있다(스팀량 생산량 = 스팀량(적산) 생산량).
        // 값이 서로 다르면 나중 시트가 덮어쓰므로 그 사실을 알린다.
        Map<String, String[]> writtenBy = new LinkedHashMap<>();
        int sheetCount = 0;
        int cellCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            // 다른 도구로 저장된 파일은 수식의 캐시값이 없을 수 있다. 그런 셀은 직접 계산해서 읽는다.
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (SheetMap sheetMap : WasteIncineratorLogCellMap.SHEETS) {
                Sheet sheet = workbook.getSheet(sheetMap.sheetName());
                if (sheet == null) {
                    missingSheets.add(sheetMap.sheetName());
                    continue;
                }

                Integer titleMonth = readTitleMonth(sheet, sheetMap.titleRef(), evaluator);
                if (titleMonth != null && titleMonth != month) {
                    monthMismatch.add(sheetMap.sheetName() + "(" + titleMonth + "월)");
                }

                List<String> mismatched = checkLayout(sheet, sheetMap, evaluator);
                if (!mismatched.isEmpty()) {
                    layoutMismatch.add(sheetMap.sheetName() + "(" + String.join(", ", mismatched) + ")");
                }

                int written = importSheet(sheet, evaluator, sheetMap, monthKey, year, month, monthDays,
                        writtenBy, conflicts);
                if (written > 0) {
                    sheetCount += 1;
                    cellCount += written;
                }
            }
        }

        if (!missingSheets.isEmpty()) {
            warnings.add("시트를 찾지 못해 건너뛰었습니다 — " + String.join(", ", missingSheets));
        }
        if (!monthMismatch.isEmpty()) {
            warnings.add("시트 제목의 월이 선택한 " + month + "월 과 다릅니다 — " + String.join(", ", monthMismatch));
        }
        if (!conflicts.isEmpty()) {
            warnings.add("같은 항목이 두 시트에 다르게 적혀 있어 나중 시트 값으로 저장했습니다 — "
                    + String.join(" / ", conflicts));
        }
        if (!layoutMismatch.isEmpty()) {
            warnings.add("양식 위치가 기준과 다른 시트가 있습니다. 해당 칸은 비어 있거나 잘못 들어갔을 수 있습니다 — "
                    + String.join(" / ", layoutMismatch));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", monthKey);
        result.put("sheets", sheetCount);
        result.put("cells", cellCount);
        result.put("warnings", warnings);
        return result;
    }

    /** 매핑표의 앵커 셀과 실제 값을 대조해 어긋난 셀 주소를 돌려준다. */
    private List<String> checkLayout(Sheet sheet, SheetMap sheetMap, FormulaEvaluator evaluator) {
        List<String> mismatched = new ArrayList<>();
        for (Anchor anchor : sheetMap.anchors()) {
            String actual = readText(cellByRef(sheet, anchor.ref()), evaluator).replace(" ", "");
            String expected = anchor.expected().replace(" ", "");
            if (!actual.startsWith(expected)) mismatched.add(anchor.ref());
        }
        return mismatched;
    }

    private int importSheet(
            Sheet sheet, FormulaEvaluator evaluator, SheetMap sheetMap,
            String monthKey, int year, int month, int monthDays,
            Map<String, String[]> writtenBy, List<String> conflicts
    ) {
        int written = 0;
        for (int day = 1; day <= monthDays; day += 1) {
            for (Entry entry : sheetMap.entries()) {
                if (entry.firstDay() && day != 1) continue;

                Cell cell = sheetMap.layout() == Layout.DAYS_IN_COLUMNS
                        // 일자가 열: 항목 = 엑셀 행, 날짜 = 시작열 + (일 - 1)
                        ? cellAt(sheet, WasteIncineratorLogCellMap.parseRow(entry.line()), sheetMap.firstDayCol() + day - 1)
                        // 일자가 행: 항목 = 엑셀 열, 날짜 = 시작행 + (일 - 1)
                        : cellAt(sheet, sheetMap.firstDayRow() + day - 1, WasteIncineratorLogCellMap.parseCol(entry.line()));

                String value;
                if (entry.text()) {
                    value = readText(cell, evaluator);
                } else {
                    Double number = readNumber(cell, evaluator);
                    value = number == null ? "" : format(round(number));
                }
                if (value == null || value.isBlank()) continue;

                String key = day + "|" + entry.colIndex();
                String[] before = writtenBy.get(key);
                if (before != null && !before[1].equals(value) && conflicts.size() < 10) {
                    conflicts.add(day + "일 " + entry.label()
                            + "(" + before[0] + "=" + before[1] + ", " + sheetMap.sheetName() + "=" + value + ")");
                }
                writtenBy.put(key, new String[]{sheetMap.sheetName(), value});
                written += store(monthKey, year, month, day, entry.colIndex(), value);
            }
        }
        return written;
    }

    private Integer readTitleMonth(Sheet sheet, String ref, FormulaEvaluator evaluator) {
        Matcher matcher = TITLE_MONTH.matcher(readText(cellByRef(sheet, ref), evaluator));
        if (!matcher.find()) return null;
        int value = Integer.parseInt(matcher.group(1));
        return (value >= 1 && value <= 12) ? value : null;
    }

    // ── 저장 ────────────────────────────────────────────────────────
    private int store(String monthKey, int year, int month, int day, int colIndex, String value) {
        if (value == null || value.isBlank()) return 0;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", monthKey);
        payload.put("year_no", year);
        payload.put("month_no", month);
        payload.put("table_name", LOG_TABLE);
        payload.put("row_key", String.valueOf(day));
        payload.put("col_index", colIndex);
        payload.put("cell_value", value);
        tableService.upsert(CELL_TABLE, payload);
        return 1;
    }

    // ── 셀 읽기 (유동상 업로드와 같은 규칙) ─────────────────────────
    private Cell cellByRef(Sheet sheet, String ref) {
        return cellAt(sheet, WasteIncineratorLogCellMap.parseRow(ref), WasteIncineratorLogCellMap.parseCol(ref));
    }

    /** 1-based 행/열로 셀을 읽는다. */
    private Cell cellAt(Sheet sheet, int rowNumber, int colNumber) {
        if (rowNumber < 1 || colNumber < 1) return null;
        Row row = sheet.getRow(rowNumber - 1);
        return row == null ? null : row.getCell(colNumber - 1);
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
}
