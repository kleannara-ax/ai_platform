package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 소각로 위탁운영 6개 페이지 엑셀 import/export.
 *
 * 시트 구성 (워크북 1개에 모두 들어감):
 *   "1. 도급내역"              — incinerator_contract,    monthKey=YYYY-MM, row_key={section}|{day}|{field}
 *   "2. 사업계획대비"          — incinerator_plan_vs,     monthKey=YYYY-01 표준화, row_key={section}|{field}
 *   "3. 전년대비"              — incinerator_yoy,         monthKey=YYYY,   row_key={form}|{rk}-m{N}
 *   "4. 가동실적"              — incinerator_operation,   monthKey=YYYY,   row_key={section}|{month}|{field}
 *   "5. 총 운영비용"           — incinerator_total_cost,  monthKey=YYYY,   row_key={section}|{month}|{field}
 *   "6. 사고이력"              — table_cell_value,        monthKey=YYYY-00, row_key={prefix}:{id}
 *
 * 도급내역은 한 시트 안에 1월~12월 블록을 세로로 누적해 표현한다 (block-per-month).
 * 시트는 원본 엑셀과 비교하면 단순화된 레이아웃이지만 round-trip 가능한 형태로 설계되어 있다.
 */
@Service
public class IncineratorWorkbookService {
    private static final String CONTRACT_TABLE = "incinerator_contract";
    private static final String PLAN_VS_TABLE = "incinerator_plan_vs";
    private static final String YOY_TABLE = "incinerator_yoy";
    private static final String OPERATION_TABLE = "incinerator_operation";
    private static final String TOTAL_COST_TABLE = "incinerator_total_cost";
    private static final String CELL_TABLE = "table_cell_value";
    private static final String ACCIDENT_PREFIX = "incinerator-accident-record:";
    private static final String MAINTENANCE_PREFIX = "incinerator-maintenance-record:";

    private static final String SHEET_CONTRACT = "1. 도급내역";
    private static final String SHEET_PLAN_VS = "2. 사업계획대비";
    private static final String SHEET_YOY = "3. 전년대비";
    private static final String SHEET_OPERATION = "4. 가동실적";
    private static final String SHEET_TOTAL_COST = "5. 총 운영비용";
    private static final String SHEET_ACCIDENT = "6. 사고이력";

    // 도급내역 — 한 일 row 컬럼: 0=day/section-marker, 1=steam, 2=unit, 3=oper(auto), 4=runtime, 5=fixed, 6=stop(auto), 7=sub(auto)
    // 섹션은 section-marker 행 (col 0 = "1호기"/"2호기"/"실 스팀량 기준") 으로 구분.
    // real 섹션은 컬럼이 다르다: 1=steam-1, 2=unit-1, 3=oper-1, 4=steam-2, 5=unit-2, 6=oper-2, 7=steam-sum, 8=total-cost
    private static final String[] UNIT_FIELDS = {"steam", "unit", "oper", "runtime", "fixed", "stop", "sub"};
    private static final String[] REAL_FIELDS = {"steam-1", "unit-1", "oper-1", "steam-2", "unit-2", "oper-2", "steam-sum", "total-cost"};

    /** 우리 내려받기 양식의 도급내역 머리글 — UNIT_FIELDS / REAL_FIELDS 와 같은 순서. 가져오기도 이 글자로 칸을 찾는다. */
    private static final String[] CONTRACT_UNIT_HEADERS = {"스팀량", "운영단가", "운영비용", "운휴시간", "시간당 고정비", "운휴비용", "소계"};
    private static final String[] CONTRACT_REAL_HEADERS = {"1호기 스팀량", "1호기 단가", "1호기 운영비용", "2호기 스팀량", "2호기 단가", "2호기 운영비용", "스팀량 합계", "총 운영비용"};

    /** 우리 내려받기 양식의 가동실적 머리글 — {필드, 머리글}. 가져오기도 이 글자로 칸을 찾는다. */
    private static final String[][] OPERATION_HOURS_HEADERS = {
            {"u1-plan", "1호기 계획"}, {"u1-actual", "1호기 실적"}, {"u1-rate", "1호기 달성율"},
            {"u2-plan", "2호기 계획"}, {"u2-actual", "2호기 실적"}, {"u2-rate", "2호기 달성율"}
    };
    private static final String[][] OPERATION_METRIC_HEADERS = {
            {"steam-plan", "스팀량 계획"}, {"steam-actual", "스팀량 실적"}, {"steam-diff", "스팀량 증감"},
            {"rate-plan", "단가 계획"}, {"rate-actual", "단가 실적"}, {"rate-diff", "단가 증감"},
            {"cost-plan", "운영비 계획"}, {"cost-actual", "운영비 실적"}, {"cost-diff", "운영비 증감"}
    };

    private final TableService tableService;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);

    public IncineratorWorkbookService(TableService tableService) {
        this.tableService = tableService;
    }

    // =============================================================
    // EXPORT  — 원본 엑셀 양식을 템플릿으로 사용. DB 값만 채워넣는다.
    // =============================================================
    private static final String TEMPLATE_PATH = "/templates/incinerator-template.xlsx";

    public byte[] exportWorkbook(int year, int month) throws IOException {
        try (InputStream tmpl = getClass().getResourceAsStream(TEMPLATE_PATH);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (tmpl == null) throw new IOException("템플릿 파일을 찾을 수 없습니다: " + TEMPLATE_PATH);
            XSSFWorkbook workbook = new XSSFWorkbook(tmpl);
            fillContractTemplate(workbook.getSheetAt(0), year, month);
            fillPlanVsTemplate(workbook.getSheetAt(1), year, month);
            fillYoyTemplate(workbook.getSheetAt(2), year);
            fillOperationTemplate(workbook.getSheetAt(3), year);
            fillTotalCostTemplate(workbook.getSheetAt(4), year);
            fillAccidentTemplate(workbook.getSheetAt(5), year);
            // 시트의 모든 공식 다시 평가하도록 (입력값이 바뀌었으므로)
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        }
    }

    /** 셀에 값을 채워넣는다. 공식 셀이면 건드리지 않아 Excel 이 재계산할 수 있게 함. */
    private void setTemplateValue(Sheet sheet, int rowIdx, int colIdx, Object value) {
        Row r = sheet.getRow(rowIdx);
        if (r == null) r = sheet.createRow(rowIdx);
        Cell c = r.getCell(colIdx);
        if (c == null) c = r.createCell(colIdx);
        if (c.getCellType() == CellType.FORMULA) return; // 공식 보존
        if (value == null || String.valueOf(value).isBlank()) { c.setBlank(); return; }
        String s = String.valueOf(value).replace(",", "").trim();
        try { c.setCellValue(Double.parseDouble(s)); }
        catch (NumberFormatException e) { c.setCellValue(s); }
    }

    private void setTemplateText(Sheet sheet, int rowIdx, int colIdx, String value) {
        Row r = sheet.getRow(rowIdx);
        if (r == null) r = sheet.createRow(rowIdx);
        Cell c = r.getCell(colIdx);
        if (c == null) c = r.createCell(colIdx);
        if (c.getCellType() == CellType.FORMULA) return;
        if (value == null) { c.setBlank(); return; }
        c.setCellValue(value);
    }

    /** Sheet 1 도급내역 — 가져오기와 같은 {@link ContractLayout} 로 칸·일자를 찾아 쓴다. */
    private void fillContractTemplate(Sheet sheet, int year, int month) {
        String monthKey = monthKey(year, month);
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, monthKey), CONTRACT_TABLE);
        int[] title = findTitleCell(sheet, k -> k.startsWith("1.") && k.contains("도급"));
        if (title != null) setTemplateText(sheet, title[0], title[1], "1. " + year + "년 " + month + "월 소각로 도급비용");

        ContractLayout layout = ContractLayout.of(sheet);
        // 입력 칸만 쓴다(운영비용·소계 등은 템플릿 수식이 계산한다)
        String[][] writes = {
                {"unit1", "steam"}, {"unit1", "unit"}, {"unit1", "runtime"}, {"unit1", "fixed"},
                {"unit2", "steam"}, {"unit2", "unit"}, {"unit2", "runtime"}, {"unit2", "fixed"},
                {"real", "steam-1"}, {"real", "unit-1"}, {"real", "steam-2"}, {"real", "unit-2"},
        };
        for (String[] w : writes) {
            Integer col = layout.columns(w[0]).get(w[1]);
            if (col == null) continue;
            for (Map.Entry<Integer, Integer> day : layout.dayRows(w[0]).entrySet()) {
                setTemplateValue(sheet, day.getValue(), col, contractVal(cells, w[0], day.getKey(), w[1]));
            }
        }
        int[] labor = contractLaborCell(sheet);
        Object laborValue = cells.get(rowKey(CONTRACT_TABLE, monthKeyMarker(), "summary|0|labor", 1));
        if (labor != null && laborValue != null) setTemplateValue(sheet, labor[0], labor[1], laborValue);
    }

    /** 도급 지급현황 표의 인건비 칸 — "구 분 / 인건비 / 총 금액" 머리글 아래 첫 줄. 없으면 null. */
    private static int[] contractLaborCell(Sheet sheet) {
        int header = SheetLabels.findRowWithAll(sheet, 0, "구 분", "인건비", "총 금액");
        if (header < 0) return null;
        Integer nameCol = SheetLabels.column(sheet, header, "구 분", 0);
        Integer laborCol = SheetLabels.column(sheet, header, "인건비", 0);
        int dataRow = SheetLabels.findRowInColumn(sheet, nameCol, header + 1, header + 5, k -> !k.isEmpty());
        return (laborCol == null || dataRow < 0) ? null : new int[]{dataRow, laborCol};
    }

    /** 앞쪽 줄에서 조건에 맞는 제목 칸 {행, 열}. 없으면 null. */
    private static int[] findTitleCell(Sheet sheet, java.util.function.Predicate<String> match) {
        for (int r = 0; r <= 5; r += 1) {
            for (int c = 0; c < SheetLabels.MAX_COL; c += 1) {
                if (match.test(SheetLabels.key(SheetLabels.text(sheet, r, c)))) return new int[]{r, c};
            }
        }
        return null;
    }

    private Object contractVal(Map<String, Object> cells, String section, int day, String field) {
        return cells.get(rowKey(CONTRACT_TABLE, monthKeyMarker(), section + "|" + day + "|" + field, 1));
    }

    /** Sheet 2 사업계획대비 — 가져오기와 같은 {@link #planVsLayout} 으로 칸을 찾아 쓴다. */
    private void fillPlanVsTemplate(Sheet sheet, int year, int month) {
        Map<String, String> values = collectPlanVsValues(year, month);
        hydratePlanVsComputedValues(values, year);
        PlanVsLayout layout = planVsLayout(sheet);
        for (Map.Entry<String, Integer> row : layout.rows().entrySet()) {
            for (Map.Entry<Integer, Integer> m : layout.months().entrySet()) {
                setTemplateValue(sheet, row.getValue(), m.getValue(), values.get(row.getKey() + "-m" + m.getKey()));
            }
        }
        for (Map.Entry<String, Integer> b : layout.breakCols().entrySet()) {
            String v = values.get("break|" + b.getKey());
            if ("note".equals(b.getKey())) setTemplateText(sheet, layout.breakRow(), b.getValue(), v);
            else setTemplateValue(sheet, layout.breakRow(), b.getValue(), v);
        }
        // 비고: 라벨 오른쪽 글자 칸들을 비우고 첫 칸에 쓴다 — 템플릿에 남은 옛 글자가 다시 올릴 때 붙지 않게
        for (Map.Entry<String, int[]> n : layout.notes().entrySet()) {
            int row = n.getValue()[0];
            int[] textCols = java.util.Arrays.copyOfRange(n.getValue(), 1, n.getValue().length);
            for (int col : textCols) setTemplateText(sheet, row, col, null);
            if (textCols.length > 0) setTemplateText(sheet, row, textCols[0], values.get("notes|" + n.getKey()));
        }
    }

    /**
     * 사업계획대비 시트 배치 — 가져오기·내보내기가 같이 쓴다.
     *
     * @param rows      "labor|plan" 등 → 줄
     * @param months    월 → 열
     * @param breakRow  이달 요약 줄. 없으면 -1
     * @param breakCols "m-plan" 등 → 열
     * @param notes     "m1-u1" 등 → {줄, 글자 칸…}
     */
    private record PlanVsLayout(Map<String, Integer> rows, Map<Integer, Integer> months, int breakRow,
                                Map<String, Integer> breakCols, Map<String, int[]> notes) {
    }

    private PlanVsLayout planVsLayout(Sheet sheet) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        Map<String, Integer> breakCols = new LinkedHashMap<>();
        Map<String, int[]> notes = new LinkedHashMap<>();
        int header = -1;
        for (int r = 0; r <= SheetLabels.MAX_ROW && header < 0; r += 1) {
            if (SheetLabels.column(sheet, r, "구 분", 0) != null && SheetLabels.isMonthHeader(sheet, r)) header = r;
        }
        if (header < 0) return new PlanVsLayout(rows, Map.of(), -1, breakCols, notes);
        int labelCol = SheetLabels.column(sheet, header, "구 분", 0);
        Map<Integer, Integer> months = SheetLabels.monthColumns(sheet, header);
        int firstMonthCol = months.values().stream().min(Integer::compare).orElse(labelCol + 2);

        // ① 월별 표 — A열 "(인건비)/(운영비)/(합계)" × 구분 칸 "…계획/…실적"
        String group = "";
        int r = header + 1;
        for (; r <= header + 14; r += 1) {
            if (SheetLabels.isMonthHeader(sheet, r) || SheetLabels.is(sheet, r, labelCol, "구 분")) break;
            String a = SheetLabels.key(SheetLabels.text(sheet, r, labelCol));
            if (!a.isEmpty()) group = a;
            String section = group.contains("인건비") ? "labor" : group.contains("운영비") ? "operation"
                    : group.contains("합계") ? "total" : null;
            String kind = kindOf(sheet, r, labelCol + 1, firstMonthCol);
            if (section != null && kind != null) rows.putIfAbsent(section + "|" + kind, r);
        }

        // ② 이달 요약 — 윗줄 "계획/실적" 왼쪽부터: 이달 계획·실적, 누계 계획·실적, 전월 실적 / "비 고"
        int breakRow = SheetLabels.findRowInColumn(sheet, labelCol, r, SheetLabels.MAX_ROW,
                k -> k.startsWith(SheetLabels.key("위탁운영비")));
        if (breakRow > 0) {
            int itemRow = -1;
            for (int up = breakRow - 1; up >= Math.max(breakRow - 3, 0) && itemRow < 0; up -= 1) {
                if (SheetLabels.column(sheet, up, "계 획", 0) != null && SheetLabels.column(sheet, up, "실 적", 0) != null) itemRow = up;
            }
            if (itemRow >= 0) {
                java.util.List<Integer> plans = new java.util.ArrayList<>();
                java.util.List<Integer> actuals = new java.util.ArrayList<>();
                for (int col = 0; col < SheetLabels.MAX_COL; col += 1) {
                    String k = SheetLabels.key(SheetLabels.text(sheet, itemRow, col));
                    if (k.equals("계획")) plans.add(col);
                    if (k.equals("실적")) actuals.add(col);
                }
                if (plans.size() > 0) breakCols.put("m-plan", plans.get(0));
                if (actuals.size() > 0) breakCols.put("m-actual", actuals.get(0));
                if (plans.size() > 1) breakCols.put("y-plan", plans.get(1));
                if (actuals.size() > 1) breakCols.put("y-actual", actuals.get(1));
                if (actuals.size() > 2) breakCols.put("prev-actual", actuals.get(2));
                for (int up = itemRow; up >= Math.max(itemRow - 2, 0); up -= 1) {
                    Integer noteCol = SheetLabels.column(sheet, up, "비 고", 0);
                    if (noteCol != null) { breakCols.put("note", noteCol); break; }
                }
            }
        }

        // ③ 비고 — "N월" 은 위에서 이어받고, "#1/#2 폐합성수지소각로" 라벨 오른쪽이 글자 칸이다
        int notesRow = SheetLabels.findRowInColumn(sheet, labelCol, Math.max(breakRow, r), SheetLabels.MAX_ROW,
                k -> k.equals(SheetLabels.key("비 고")));
        if (notesRow >= 0) {
            Integer noteMonth = null;
            for (int nr = notesRow; nr <= sheet.getLastRowNum(); nr += 1) {
                Integer unitCol = null;
                String unit = null;
                for (int col = 0; col < SheetLabels.MAX_COL && unitCol == null; col += 1) {
                    String k = SheetLabels.key(SheetLabels.text(sheet, nr, col));
                    if (!k.contains("폐합성")) continue;
                    if (k.startsWith("#1")) { unitCol = col; unit = "u1"; }
                    else if (k.startsWith("#2")) { unitCol = col; unit = "u2"; }
                }
                if (unitCol == null) continue;
                for (int col = 0; col < unitCol; col += 1) {
                    Integer m = SheetLabels.monthOf(SheetLabels.text(sheet, nr, col));
                    if (m != null) noteMonth = m;
                }
                if (noteMonth == null) continue;
                notes.put("m" + noteMonth + "-" + unit, noteTextCells(sheet, nr, unitCol));
            }
        }
        return new PlanVsLayout(rows, months, breakRow, breakCols, notes);
    }

    /**
     * 비고 줄의 글자 칸 {줄, 칸…}. 라벨 칸이 병합돼 있으면 병합 끝 다음부터, 그 줄에서 병합 묶음마다 첫 칸을 모은다.
     * (원본은 라벨 C:D 병합, 글자 E:F · G:… 두 묶음)
     */
    private static int[] noteTextCells(Sheet sheet, int row, int labelCol) {
        java.util.List<Integer> cols = new java.util.ArrayList<>();
        cols.add(row);
        int col = mergedLastColumn(sheet, row, labelCol) + 1;
        int last = Math.max(col, lastUsedColumn(sheet, row));
        while (col <= last) {
            cols.add(col);
            col = mergedLastColumn(sheet, row, col) + 1;
        }
        return cols.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int mergedLastColumn(Sheet sheet, int row, int col) {
        for (org.apache.poi.ss.util.CellRangeAddress m : sheet.getMergedRegions()) {
            if (m.isInRange(row, col)) return m.getLastColumn();
        }
        return col;
    }

    private static int lastUsedColumn(Sheet sheet, int row) {
        for (int col = SheetLabels.MAX_COL - 1; col >= 0; col -= 1) {
            if (!SheetLabels.text(sheet, row, col).isBlank()) return col;
        }
        return -1;
    }

    /** Sheet 3 전년대비 — "구 분 | 1월 …" 표마다 첫 "…실적" 줄이 전년, 둘째가 올해다. */
    private void fillYoyTemplate(Sheet sheet, int year) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)), YOY_TABLE);
        hydrateYoyComputedCells(cells, year);
        for (Map.Entry<String, Integer> r : yoyRows(sheet).entrySet()) {
            Map<Integer, Integer> months = SheetLabels.monthColumns(sheet, yoyHeaderAbove(sheet, r.getValue()));
            for (Map.Entry<Integer, Integer> m : months.entrySet()) {
                setTemplateValue(sheet, r.getValue(), m.getValue(),
                        cells.get(rowKey(YOY_TABLE, monthKeyMarker(), r.getKey() + "-m" + m.getKey(), 1)));
            }
        }
    }

    /** 전년대비 표의 "cost|prev" · "cost|curr" · "ton|prev" · "ton|curr" → 줄. */
    private Map<String, Integer> yoyRows(Sheet sheet) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        for (int header = 0; header <= SheetLabels.MAX_ROW; header += 1) {
            if (!SheetLabels.isMonthHeader(sheet, header)) continue;
            Integer labelCol = SheetLabels.column(sheet, header, "구 분", 0);
            if (labelCol == null) continue;
            int firstMonthCol = SheetLabels.monthColumns(sheet, header).values().stream().min(Integer::compare).orElse(labelCol + 2);
            String group = "";
            int actualSeen = 0;
            for (int r = header + 1; r <= header + 5 && actualSeen < 2; r += 1) {
                String a = SheetLabels.key(SheetLabels.text(sheet, r, labelCol));
                if (!a.isEmpty()) group = a;
                if (!"actual".equals(kindOf(sheet, r, labelCol + 1, firstMonthCol))) continue;
                String form = group.contains("스팀생산량") ? "ton" : group.contains("합계") ? "cost" : null;
                if (form == null) break;
                rows.put(form + "|" + (actualSeen == 0 ? "prev" : "curr"), r);
                actualSeen += 1;
            }
        }
        return rows;
    }

    private static int yoyHeaderAbove(Sheet sheet, int row) {
        for (int r = row - 1; r >= 0; r -= 1) if (SheetLabels.isMonthHeader(sheet, r)) return r;
        return -1;
    }

    /** Sheet 4 계획대비 가동실적 — 가져오기와 같은 {@link #operationLayout} 으로 칸·월을 찾아 쓴다. */
    private void fillOperationTemplate(Sheet sheet, int year) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)), OPERATION_TABLE);
        hydrateOperationComputedCells(cells);
        OperationLayout layout = operationLayout(sheet);
        for (Map.Entry<Integer, Integer> m : layout.monthRows().entrySet()) {
            for (Map.Entry<String, Integer> h : layout.hours().entrySet()) {
                setTemplateValue(sheet, m.getValue(), h.getValue(),
                        cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "hours|" + m.getKey() + "|" + h.getKey(), 1)));
            }
            for (Map.Entry<String, Integer> h : layout.metrics().entrySet()) {
                setTemplateValue(sheet, m.getValue(), h.getValue(),
                        cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m.getKey() + "|" + h.getKey(), 1)));
            }
        }
    }

    /** 가동실적 배치 — 월 → 줄, 필드 → 열. 가져오기·내보내기가 같이 쓴다. */
    private record OperationLayout(Map<Integer, Integer> monthRows, Map<String, Integer> hours, Map<String, Integer> metrics) {
    }

    /**
     * 설비(위 줄) + 항목(아래 줄) 제목으로 칸을, "N월" 로 줄을 찾는다.
     * 스팀생산량·운영단가·위탁운영비용은 왼쪽에도 같은 이름의 표가 있어서 "폐합성 2호기" 다음부터 찾는다.
     */
    private static OperationLayout operationLayout(Sheet sheet) {
        Map<String, Integer> hours = new LinkedHashMap<>();
        Map<String, Integer> metrics = new LinkedHashMap<>();
        int groupRow = SheetLabels.findRow(sheet, 0, k -> k.equals(SheetLabels.key("폐합성 1호기")));
        if (groupRow < 0) return new OperationLayout(Map.of(), hours, metrics);
        int itemRow = groupRow + 1;
        HeaderColumnIndex index = HeaderColumnIndex.build(sheet, -1, groupRow, itemRow, itemRow + 1, itemRow + 16, 0);
        putIfFound(hours, "u1-plan", index.column("폐합성 1호기", "가동계획(hr)"));
        putIfFound(hours, "u1-actual", index.column("폐합성 1호기", "가동실적(hr)"));
        putIfFound(hours, "u2-plan", index.column("폐합성 2호기", "가동계획(hr)"));
        putIfFound(hours, "u2-actual", index.column("폐합성 2호기", "가동실적(hr)"));
        int cursor = hours.values().stream().max(Integer::compare).orElse(0) + 1;
        String[][] metricHeaders = {
                {"steam-plan", "스팀생산량", "계획"}, {"steam-actual", "스팀생산량", "실적"},
                {"rate-plan", "운영단가(위탁)", "계획"}, {"rate-actual", "운영단가(위탁)", "실적"},
                {"cost-plan", "위탁운영비용(천원)", "계획"}, {"cost-actual", "위탁운영비용(천원)", "실적"}
        };
        for (String[] h : metricHeaders) {
            Integer col = index.columnAfter(cursor, h[1], h[2]);
            if (col == null) continue;
            metrics.put(h[0], col);
            cursor = col + 1;
        }
        int monthCol = SheetLabels.monthLabelColumn(sheet, itemRow + 1, itemRow + 16);
        Map<Integer, Integer> monthRows = monthCol < 0 ? Map.of()
                : SheetLabels.monthRows(sheet, monthCol, itemRow + 1, itemRow + 16, k -> k.equals(SheetLabels.key("합계")));
        return new OperationLayout(monthRows, hours, metrics);
    }

    /**
     * Sheet 5 총비용 — 가져오기와 같은 제목 기반 칸·"N월" 줄로 세 표(운영내역·자체 운영비용·소모품)를 모두 쓴다.
     * (예전에는 운영내역만, 그것도 한 줄 아래에 써서 다시 올리면 달이 하나씩 밀렸다)
     */
    private void fillTotalCostTemplate(Sheet sheet, int year) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)), TOTAL_COST_TABLE);
        hydrateTotalCostComputedCells(cells, year);
        for (String table : new String[]{"op", "self", "mat"}) {
            String anchor = totalCostAnchor(table);
            Map<String, Integer> columns = totalCostColumns(sheet, anchor, totalCostHeaders(table));
            for (Map.Entry<Integer, Integer> m : totalCostMonthRows(sheet, anchor).entrySet()) {
                for (Map.Entry<String, Integer> c : columns.entrySet()) {
                    String[] sectionField = c.getKey().split("\\|", 2);
                    String key = sectionField[0] + "|" + m.getKey() + "|" + sectionField[1];
                    setTemplateValue(sheet, m.getValue(), c.getValue(), cells.get(rowKey(TOTAL_COST_TABLE, monthKeyMarker(), key, 1)));
                }
            }
        }
    }

    static String totalCostAnchor(String table) {
        return switch (table) {
            case "op" -> "폐기물 실소각량";
            case "self" -> "외부 폐합성수지 수익금";
            default -> "소각로별 부생유 사용량";
        };
    }

    /** 총 운영비용 표 한 벌의 월 → 줄. 표 제목 줄 아래 "N월" 칸, "합계" 에서 멈춘다. */
    static Map<Integer, Integer> totalCostMonthRows(Sheet sheet, String anchor) {
        int groupRow = SheetLabels.findRow(sheet, 0, k -> k.startsWith(SheetLabels.key(anchor)));
        if (groupRow < 0) return Map.of();
        int monthCol = SheetLabels.monthLabelColumn(sheet, groupRow + 1, groupRow + 20);
        if (monthCol < 0) return Map.of();
        return SheetLabels.monthRows(sheet, monthCol, groupRow + 1, groupRow + 20, k -> k.equals(SheetLabels.key("합계")));
    }

    private void fillTotalCostBlock(Sheet sheet, int rowIdx, int m, Map<String, Object> cells, TotalCostBlock block, int firstCol) {
        for (int i = 0; i < block.fields.length; i += 1) {
            String key = block.section + "|" + m + "|" + block.fields[i];
            setTemplateValue(sheet, rowIdx, firstCol + i, cells.get(rowKey(TOTAL_COST_TABLE, monthKeyMarker(), key, 1)));
        }
    }

    /**
     * Sheet 6 사고이력 — 가져오기와 같은 머리글("No. / 일 자 / 사 유 / …")로 사고·정비 두 표를 찾아,
     * 템플릿에 남은 옛 기록을 비우고 DB 기록을 번호 순서대로 채운다.
     * (예전에는 머리글 위치와 상관없이 4행 A열부터 써서, 내려받은 파일을 다시 올리면 기록이 모두 빠졌다)
     */
    private void fillAccidentTemplate(Sheet sheet, int year) {
        String mk = year + "-00";
        Map<String, java.util.TreeMap<Integer, Map<Integer, Object>>> byPrefix = new LinkedHashMap<>();
        byPrefix.put(ACCIDENT_PREFIX, new java.util.TreeMap<>());
        byPrefix.put(MAINTENANCE_PREFIX, new java.util.TreeMap<>());
        for (Map<String, Object> row : tableService.listAllByMonth(CELL_TABLE, mk)) {
            String rowKey = String.valueOf(row.get("row_key"));
            for (Map.Entry<String, java.util.TreeMap<Integer, Map<Integer, Object>>> e : byPrefix.entrySet()) {
                if (!rowKey.startsWith(e.getKey())) continue;
                int id = recordNumber(rowKey.substring(e.getKey().length()));
                int colIndex = ((Number) row.getOrDefault("col_index", 0)).intValue();
                e.getValue().computeIfAbsent(id, k -> new LinkedHashMap<>()).put(colIndex, row.get("cell_value"));
            }
        }
        java.util.List<AccidentBlock> blocks = accidentBlocks(sheet);
        String[] prefixes = {ACCIDENT_PREFIX, MAINTENANCE_PREFIX};
        for (int b = 0; b < blocks.size() && b < prefixes.length; b += 1) {
            AccidentBlock block = blocks.get(b);
            java.util.List<Map<Integer, Object>> records = new java.util.ArrayList<>(byPrefix.get(prefixes[b]).values());
            int i = 0;
            for (int r = block.firstRow(); r <= block.lastRow(); r += 1, i += 1) {
                Map<Integer, Object> rec = i < records.size() ? records.get(i) : Map.of();
                setAccidentCell(sheet, r, block.cols().get("no"), rec.isEmpty() ? null : rec.getOrDefault(7, String.valueOf(i + 1)), true);
                setAccidentCell(sheet, r, block.cols().get("date"), rec.get(1), false);
                setAccidentCell(sheet, r, block.cols().get("reason"), rec.get(2), false);
                setAccidentCell(sheet, r, block.cols().get("action"), rec.get(3), false);
                setAccidentCell(sheet, r, block.cols().get("downtime"), rec.get(4), true);
                setAccidentCell(sheet, r, block.cols().get("loss"), rec.get(5), true);
                setAccidentCell(sheet, r, block.cols().get("note"), rec.get(6), false);
            }
        }
    }

    /** 기록 번호("1", "12.0") — 숫자가 아니면 뒤로 보낸다. */
    private static int recordNumber(String id) {
        String digits = id == null ? "" : id.replaceAll("\\.0$", "").replaceAll("\\D", "");
        try {
            return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private void setAccidentCell(Sheet sheet, int row, Integer col, Object value, boolean numeric) {
        if (col == null) return;
        if (numeric) setTemplateValue(sheet, row, col, value);
        else setTemplateText(sheet, row, col, value == null ? null : String.valueOf(value));
    }

    /** 사고·정비 표 한 벌 — 머리글 칸과 데이터 줄 범위. */
    private record AccidentBlock(Map<String, Integer> cols, int firstRow, int lastRow) {
    }

    /** "일 자 / 조치내역" 머리글이 있는 표를 위에서부터 — 첫 표 사고, 둘째 정비. 끝은 "…횟수" 집계 줄 바로 위. */
    private static java.util.List<AccidentBlock> accidentBlocks(Sheet sheet) {
        java.util.List<AccidentBlock> blocks = new java.util.ArrayList<>();
        for (int header = 0; header <= SheetLabels.MAX_ROW && blocks.size() < 2; header += 1) {
            Map<String, Integer> cols = accidentColumns(sheet, header);
            if (cols == null) continue;
            int noCol = cols.getOrDefault("no", cols.get("date") - 1);
            int r = header + 1;
            for (; r <= header + 40; r += 1) {
                String k = SheetLabels.key(SheetLabels.text(sheet, r, noCol));
                if (k.endsWith("횟수") || accidentColumns(sheet, r) != null) break;
            }
            blocks.add(new AccidentBlock(cols, header + 1, r - 1));
            header = r;
        }
        return blocks;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // ---------- Sheet 1: 도급내역 (해당 월 1개월치만) ----------
    private void writeContractSheet(Sheet sheet, int year, int month, CellStyle headerStyle) {
        int row = 0;
        setString(sheet, row++, 0, year + "년 " + month + "월 소각로 도급내역");
        row += 1;
        String monthKey = monthKey(year, month);
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, monthKey), CONTRACT_TABLE);
        hydrateContractComputedCells(cells);
        for (String section : new String[]{"unit1", "unit2", "real"}) {
            row = writeContractSection(sheet, row, section, cells, headerStyle);
            row += 1;
        }
        sheet.setColumnWidth(0, 4500);
        for (int c = 1; c < 10; c += 1) sheet.setColumnWidth(c, 3600);
    }

    private int writeContractSection(Sheet sheet, int startRow, String section, Map<String, Object> cells, CellStyle headerStyle) {
        String sectionLabel = switch (section) {
            case "unit1" -> "1호기";
            case "unit2" -> "2호기";
            default -> "실 스팀량 기준";
        };
        setString(sheet, startRow, 0, sectionLabel);
        getOrCreateCell(sheet, startRow, 0).setCellStyle(headerStyle);
        startRow += 1;
        // header row
        Row header = sheet.getRow(startRow);
        if (header == null) header = sheet.createRow(startRow);
        String[] headers = "real".equals(section) ? CONTRACT_REAL_HEADERS : CONTRACT_UNIT_HEADERS;
        Cell dayHeader = header.createCell(0);
        dayHeader.setCellValue("일별");
        dayHeader.setCellStyle(headerStyle);
        for (int c = 0; c < headers.length; c += 1) {
            Cell cell = header.createCell(c + 1);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }
        startRow += 1;
        String[] fields = "real".equals(section) ? REAL_FIELDS : UNIT_FIELDS;
        for (int day = 1; day <= 31; day += 1) {
            setString(sheet, startRow, 0, day + "일");
            for (int idx = 0; idx < fields.length; idx += 1) {
                Object v = cells.get(rowKey(CONTRACT_TABLE, monthKeyMarker(), section + "|" + day + "|" + fields[idx], 1));
                if (v != null && !String.valueOf(v).isBlank()) {
                    setNumericOrText(sheet, startRow, idx + 1, v);
                }
            }
            startRow += 1;
        }
        return startRow;
    }

    private String monthKeyMarker() {
        // 도급내역 cells 는 month 별로 별도 buildCellMap 했기에 rowKey 의 monthKey 부분은 marker 만 쓰면 됨
        // 실제 구현은 buildCellMap 에서 month 무관하게 row_key+col_index 만 key 화
        return "*";
    }

    // ---------- Sheet 2: 사업계획대비 (선택 월의 monthKey 데이터 사용) ----------
    private void writePlanVsSheet(Sheet sheet, int year, int month, CellStyle headerStyle) {
        // 페이지는 한 monthKey에 12개월 데이터를 통째로 저장하므로, 선택한 월(monthKey)을 그대로 사용.
        // 그 monthKey에 값이 없으면 다른 월 버킷에서 최신값을 보조로 채워줌.
        Map<String, String> values = collectPlanVsValues(year, month);
        hydratePlanVsComputedValues(values, year);

        int row = 0;
        setString(sheet, row++, 0, year + "년 " + month + "월 기준 소각로 위탁운영 사업계획대비 실적");
        row += 1;

        // 본 표 (cost): labor.plan, labor.actual, operation.plan, operation.actual, total.plan, total.actual, total.rate, total.diff
        // 각 행은 m1..m12 + total
        setString(sheet, row, 0, "구분");
        setString(sheet, row, 1, "행");
        for (int m = 1; m <= 12; m += 1) setString(sheet, row, 1 + m, m + "월");
        setString(sheet, row, 14, "합계");
        styleRow(sheet, row, 0, 14, headerStyle);
        row += 1;

        String[][] planGroups = {
                {"labor", "인건비", "plan", "계획"},
                {"labor", "인건비", "actual", "실적"},
                {"operation", "운영비", "plan", "계획"},
                {"operation", "운영비", "actual", "실적"},
                {"total", "합계", "plan", "계획"},
                {"total", "합계", "actual", "실적"},
                {"total", "합계", "rate", "달성율(%)"},
                {"total", "합계", "diff", "증감"},
        };
        for (String[] grp : planGroups) {
            setString(sheet, row, 0, grp[1]);
            setString(sheet, row, 1, grp[3]);
            for (int m = 1; m <= 12; m += 1) {
                String key = grp[0] + "|" + grp[2] + "-m" + m;
                String v = values.get(key);
                if (v != null && !v.isBlank()) setNumericOrText(sheet, row, 1 + m, v);
            }
            row += 1;
        }
        row += 1;

        // 구분 표 (break): m-plan, m-actual, y-plan, y-actual, prev-actual, note
        setString(sheet, row, 0, "구분 (백만원)");
        styleRow(sheet, row, 0, 6, headerStyle);
        row += 1;
        String[] breakFields = {"m-plan", "m-actual", "y-plan", "y-actual", "prev-actual", "note"};
        String[] breakLabels = {"월 계획", "월 실적", "누계 계획", "누계 실적", "전월 실적", "비고"};
        for (int i = 0; i < breakFields.length; i += 1) {
            setString(sheet, row, 0, breakLabels[i]);
            String v = values.get("break|" + breakFields[i]);
            if (v != null && !v.isBlank()) {
                if ("note".equals(breakFields[i])) setString(sheet, row, 1, v);
                else setNumericOrText(sheet, row, 1, v);
            }
            row += 1;
        }
        row += 1;

        // 비고 표 (notes): m{N}-u1, m{N}-u2 (N=1..12)
        setString(sheet, row, 0, "비고");
        setString(sheet, row, 1, "호기");
        setString(sheet, row, 2, "내용");
        styleRow(sheet, row, 0, 2, headerStyle);
        row += 1;
        for (int m = 1; m <= 12; m += 1) {
            for (String unit : new String[]{"u1", "u2"}) {
                setString(sheet, row, 0, m + "월");
                setString(sheet, row, 1, "u1".equals(unit) ? "#1 폐합성수지소각로" : "#2 폐합성수지소각로");
                String v = values.get("notes|m" + m + "-" + unit);
                if (v != null && !v.isBlank()) setString(sheet, row, 2, v);
                row += 1;
            }
        }
        sheet.setColumnWidth(0, 4500);
        sheet.setColumnWidth(1, 5500);
        for (int c = 2; c < 15; c += 1) sheet.setColumnWidth(c, 3000);
    }

    private Map<String, String> collectPlanVsValues(int year, int selectedMonth) {
        // 1) 다른 월 버킷 먼저 스캔해 백필 (오래된 데이터)
        // 2) 선택한 월 버킷 마지막에 적용 → 우선순위 최상
        Map<String, String> result = new LinkedHashMap<>();
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int m = 1; m <= 12; m += 1) if (m != selectedMonth) order.add(m);
        order.add(selectedMonth);
        for (Integer m : order) {
            String mk = monthKey(year, m);
            for (Map<String, Object> row : tableService.listAllByMonth(CELL_TABLE, mk)) {
                if (!PLAN_VS_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
                String rowKey = String.valueOf(row.get("row_key"));
                Object value = row.get("cell_value");
                if (value == null) continue;
                String text = String.valueOf(value);
                if (!text.isBlank()) result.put(rowKey, text);
            }
        }
        return result;
    }

    // ---------- Sheet 3: 전년대비 ----------
    private void writeYoySheet(Sheet sheet, int year, CellStyle headerStyle) {
        Map<String, Object> cells = buildCellMap(
                tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)),
                YOY_TABLE
        );
        hydrateYoyComputedCells(cells, year);
        int row = 0;
        setString(sheet, row++, 0, year + "년 전년대비 및 절감내역");
        row += 1;
        setString(sheet, row - 2, 0, year + "년 전년 실적 비교");
        for (String form : new String[]{"cost", "ton"}) {
            String label = "cost".equals(form) ? "폐합성소각로 합계 (천원)" : "폐합성소각로 스팀생산량 (톤)";
            setString(sheet, row, 0, label);
            styleRow(sheet, row, 0, 14, headerStyle);
            row += 1;
            setString(sheet, row, 0, "구분");
            for (int m = 1; m <= 12; m += 1) setString(sheet, row, m, m + "월");
            setString(sheet, row, 13, "합계");
            row += 1;
            // 입력 가능 rk: prev (curr, diff 는 자동)
            for (String rk : new String[]{"prev", "curr", "diff"}) {
                String rowLabel = switch (rk) {
                    case "prev" -> (year - 1) + " 실적";
                    case "curr" -> year + " 실적";
                    default -> "증감";
                };
                setString(sheet, row, 0, rowLabel);
                for (int m = 1; m <= 12; m += 1) {
                    Object v = cells.get(rowKey(YOY_TABLE, monthKeyMarker(), form + "|" + rk + "-m" + m, 1));
                    if (v != null && !String.valueOf(v).isBlank()) setNumericOrText(sheet, row, m, v);
                }
                row += 1;
            }
            row += 1;
        }
        sheet.setColumnWidth(0, 4500);
        for (int c = 1; c < 14; c += 1) sheet.setColumnWidth(c, 3000);
    }

    // ---------- Sheet 4: 가동실적 ----------
    private void writeOperationSheet(Sheet sheet, int year, CellStyle headerStyle) {
        Map<String, Object> cells = buildCellMap(
                tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)),
                OPERATION_TABLE
        );
        hydrateOperationComputedCells(cells);
        int row = 0;
        setString(sheet, row++, 0, year + "년 계획대비 가동실적");
        row += 1;

        // hours 섹션: u1-plan, u1-actual, u1-rate(auto), u2-plan, u2-actual, u2-rate(auto)
        setString(sheet, row, 0, "폐합성 소각로 가동시간 (Hr)");
        styleRow(sheet, row, 0, 6, headerStyle);
        row += 1;
        setString(sheet, row, 0, "월");
        for (int i = 0; i < OPERATION_HOURS_HEADERS.length; i += 1) setString(sheet, row, i + 1, OPERATION_HOURS_HEADERS[i][1]);
        row += 1;
        for (int m = 1; m <= 12; m += 1) {
            setString(sheet, row, 0, m + "월");
            for (int i = 0; i < OPERATION_HOURS_HEADERS.length; i += 1) {
                Object v = cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "hours|" + m + "|" + OPERATION_HOURS_HEADERS[i][0], 1));
                if (v != null && !String.valueOf(v).isBlank()) setNumericOrText(sheet, row, i + 1, v);
            }
            row += 1;
        }
        row += 1;

        // metric 섹션: metric (steam/rate/cost) × plan/actual/diff(auto)
        setString(sheet, row, 0, "스팀생산량 / 단가 / 운영비용");
        styleRow(sheet, row, 0, 10, headerStyle);
        row += 1;
        setString(sheet, row, 0, "월");
        for (int i = 0; i < OPERATION_METRIC_HEADERS.length; i += 1) setString(sheet, row, i + 1, OPERATION_METRIC_HEADERS[i][1]);
        row += 1;
        for (int m = 1; m <= 12; m += 1) {
            setString(sheet, row, 0, m + "월");
            for (int i = 0; i < OPERATION_METRIC_HEADERS.length; i += 1) {
                Object v = cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|" + OPERATION_METRIC_HEADERS[i][0], 1));
                if (v != null && !String.valueOf(v).isBlank()) setNumericOrText(sheet, row, i + 1, v);
            }
            row += 1;
        }
        sheet.setColumnWidth(0, 3500);
        for (int c = 1; c < 11; c += 1) sheet.setColumnWidth(c, 3000);
    }

    // ---------- Sheet 5: 총 운영비용 ----------
    private static final TotalCostBlock[] TOTAL_COST_BLOCKS = {
            new TotalCostBlock("op-burn", "운영내역 - 소각량/가동시간/스팀",
                    new String[]{"burn-1", "burn-2", "burn-sum", "runtime-1", "runtime-2", "stop-1", "stop-2", "steam-1", "steam-2", "steam-sum", "rate-1", "rate-2"}),
            new TotalCostBlock("op-cost", "운영내역 - 위탁운영비용",
                    new String[]{"unit", "u1-steam", "u1-op", "u1-stop", "u1-sub", "u2-steam", "u2-op", "u2-stop", "u2-sub", "deduct", "total"}),
            new TotalCostBlock("self-srf", "자체 - SRF 수익/폐기물",
                    new String[]{"in-qty", "in-rev", "floor-recycle", "floor-bury", "floor-sub", "fly-solid", "fly-bury", "fly-sub", "cost-floor", "cost-fly", "cost-misc"}),
            new TotalCostBlock("self-util", "자체 - 전력/가성소다/기타",
                    new String[]{"p1", "p2", "prate", "psub", "naoh-qty", "naoh-unit", "naoh-cost", "water", "waste-charge", "etc"}),
            new TotalCostBlock("self-total", "자체 - 비용합계/원단위",
                    new String[]{"total", "burn-rate", "steam-rate"}),
            new TotalCostBlock("mat-oil", "소모품 - 부생유/청관제",
                    new String[]{"u1", "u2", "sub", "unit", "cost", "ck-qty", "ck-unit", "ck-cost"}),
            new TotalCostBlock("mat-chem", "소모품 - 요소수/소금",
                    new String[]{"u-qty", "u-unit", "u-cost", "s-qty", "s-unit", "s-cost"}),
            new TotalCostBlock("mat-misc", "소모품 - 활성탄/크링커",
                    new String[]{"a-qty", "a-unit", "a-cost", "k-qty", "k-unit", "k-cost", "total"}),
    };

    private record TotalCostBlock(String section, String label, String[] fields) {}

    private void writeTotalCostSheet(Sheet sheet, int year, CellStyle headerStyle) {
        Map<String, Object> cells = buildCellMap(
                tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)),
                TOTAL_COST_TABLE
        );
        hydrateTotalCostComputedCells(cells, year);
        int row = 0;
        setString(sheet, row++, 0, year + "년 폐합성소각로 총 운영비용");
        for (TotalCostBlock block : TOTAL_COST_BLOCKS) {
            row += 1;
            setString(sheet, row, 0, block.label);
            styleRow(sheet, row, 0, block.fields.length, headerStyle);
            row += 1;
            setString(sheet, row, 0, "월");
            for (int i = 0; i < block.fields.length; i += 1) setString(sheet, row, i + 1, block.fields[i]);
            row += 1;
            for (int m = 1; m <= 12; m += 1) {
                setString(sheet, row, 0, m + "월");
                for (int i = 0; i < block.fields.length; i += 1) {
                    Object v = cells.get(rowKey(TOTAL_COST_TABLE, monthKeyMarker(), block.section + "|" + m + "|" + block.fields[i], 1));
                    if (v != null && !String.valueOf(v).isBlank()) setNumericOrText(sheet, row, i + 1, v);
                }
                row += 1;
            }
        }
        sheet.setColumnWidth(0, 3500);
        for (int c = 1; c < 15; c += 1) sheet.setColumnWidth(c, 3000);
    }

    // ---------- Sheet 6: 사고이력 ----------
    private void writeAccidentSheet(Sheet sheet, int year, CellStyle headerStyle) {
        String monthKey = year + "-00";
        List<Map<String, Object>> rows = tableService.listAllByMonth(CELL_TABLE, monthKey);
        // 사고: 6 cols (date, reason, action, downtime, loss, note)
        // 정비: 5 cols (date, reason, action, downtime, note) — loss 없음, col_index 6=note
        int row = 0;
        setString(sheet, row++, 0, year + "년 사고/정비 이력");
        row += 1;
        setString(sheet, row, 0, "사고이력");
        styleRow(sheet, row, 0, 6, headerStyle);
        row += 1;
        String[] accHeaders = {"ID", "일자", "사유", "조치내역", "운휴시간", "손실금액", "비고"};
        for (int i = 0; i < accHeaders.length; i += 1) setString(sheet, row, i, accHeaders[i]);
        styleRow(sheet, row, 0, accHeaders.length - 1, headerStyle);
        row += 1;
        row = writeAccidentRowsByPrefix(sheet, row, rows, ACCIDENT_PREFIX, true);

        row += 1;
        setString(sheet, row, 0, "정비이력");
        styleRow(sheet, row, 0, 6, headerStyle);
        row += 1;
        String[] mntHeaders = {"ID", "일자", "사유", "조치내역", "운휴시간", "비고"};
        for (int i = 0; i < mntHeaders.length; i += 1) setString(sheet, row, i, mntHeaders[i]);
        styleRow(sheet, row, 0, mntHeaders.length - 1, headerStyle);
        row += 1;
        row = writeAccidentRowsByPrefix(sheet, row, rows, MAINTENANCE_PREFIX, false);

        for (int c = 0; c < 7; c += 1) sheet.setColumnWidth(c, c == 0 ? 3500 : 5500);
    }

    private int writeAccidentRowsByPrefix(Sheet sheet, int startRow, List<Map<String, Object>> rows, String prefix, boolean hasLoss) {
        // group by row_key
        Map<String, Map<Integer, String>> byRowKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (!CELL_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            String rowKey = String.valueOf(row.get("row_key"));
            if (rowKey == null || !rowKey.startsWith(prefix)) continue;
            int colIndex = toInt(row.get("col_index"));
            Object value = row.get("cell_value");
            byRowKey.computeIfAbsent(rowKey, k -> new LinkedHashMap<>()).put(colIndex, value == null ? "" : String.valueOf(value));
        }
        for (Map.Entry<String, Map<Integer, String>> entry : byRowKey.entrySet()) {
            String id = entry.getKey().substring(prefix.length());
            Map<Integer, String> cols = entry.getValue();
            setString(sheet, startRow, 0, id);
            setString(sheet, startRow, 1, cols.getOrDefault(1, ""));
            setString(sheet, startRow, 2, cols.getOrDefault(2, ""));
            setString(sheet, startRow, 3, cols.getOrDefault(3, ""));
            setNumericOrText(sheet, startRow, 4, cols.getOrDefault(4, ""));
            if (hasLoss) {
                setNumericOrText(sheet, startRow, 5, cols.getOrDefault(5, ""));
                setString(sheet, startRow, 6, cols.getOrDefault(6, ""));
            } else {
                setString(sheet, startRow, 5, cols.getOrDefault(6, ""));
            }
            startRow += 1;
        }
        return startRow;
    }

    // =============================================================
    // IMPORT
    // =============================================================
    @Transactional
    public Map<String, Object> importWorkbook(MultipartFile file, int year, int month) throws IOException {
        int contractRows = 0, planVsRows = 0, yoyRows = 0, opRows = 0, totalCostRows = 0, accidentRows = 0;
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet contractSheet = findSheet(workbook, "도급내역", 0);
            Sheet planVsSheet = findSheet(workbook, "사업계획대비", 1);
            Sheet yoySheet = findSheet(workbook, "전년대비", 2);
            Sheet operationSheet = findSheet(workbook, "가동실적", 3);
            Sheet totalCostSheet = findSheet(workbook, "총 운영비용", 4);
            Sheet accidentSheet = findSheet(workbook, "사고이력", 5);

            // 선택한 월에 해당하는 데이터만 클리어 (도급내역/사업계획대비). 연 기준 표는 매 import 마다 전체 갱신.
            clearImportTargets(year, month);

            if (contractSheet != null) contractRows = importContractSheet(contractSheet, year, month);
            if (planVsSheet != null) planVsRows = importPlanVsSheet(planVsSheet, year, month);
            // The year-over-year page is derived from actual results in DB.
            // Ignore uploaded sheet data so stale/manual Excel values cannot override it.
            if (yoySheet != null) yoyRows = 0;
            if (operationSheet != null) opRows = importOperationSheet(operationSheet, year);
            if (totalCostSheet != null) totalCostRows = importTotalCostSheet(totalCostSheet, year);
            if (accidentSheet != null) accidentRows = importAccidentSheet(accidentSheet, year);
        }
        int totalRows = contractRows + planVsRows + yoyRows + opRows + totalCostRows + accidentRows;
        if (totalRows <= 0) {
            throw new IllegalArgumentException("업로드한 소각로 위탁운영비 엑셀에서 반영할 데이터를 찾지 못했습니다. 다운로드한 양식의 시트와 셀 위치를 확인해주세요.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("contract_rows", contractRows);
        result.put("plan_vs_rows", planVsRows);
        result.put("yoy_rows", yoyRows);
        result.put("operation_rows", opRows);
        result.put("total_cost_rows", totalCostRows);
        result.put("accident_rows", accidentRows);
        return result;
    }

    private void clearImportTargets(int year, int month) {
        // 도급내역·사업계획대비: 선택한 월 monthKey 만 클리어 (다른 월 데이터 보존)
        for (int m = 1; m <= 12; m += 1) {
            tableService.deleteTableCellValuesByTables(monthKey(year, m), List.of(CONTRACT_TABLE, PLAN_VS_TABLE));
        }
        // 연 기준 표: 매번 전체 갱신 (엑셀에 12개월치 다 들어있음)
        String yearMk = String.valueOf(year);
        tableService.deleteTableCellValuesByTables(yearMk, List.of(YOY_TABLE, OPERATION_TABLE, TOTAL_COST_TABLE));
        String accidentMk = year + "-00";
        tableService.deleteTableCellRowsByMonthAndPrefixes(accidentMk, List.of(ACCIDENT_PREFIX, MAINTENANCE_PREFIX));
    }

    private Sheet findSheet(Workbook workbook, String nameFragment, int fallbackIndex) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
            Sheet s = workbook.getSheetAt(i);
            String name = s.getSheetName();
            if (name != null && name.contains(nameFragment)) return s;
        }
        if (fallbackIndex < workbook.getNumberOfSheets()) return workbook.getSheetAt(fallbackIndex);
        return null;
    }

    private int importContractSheet(Sheet sheet, int year, int month) {
        // 1) label-based 먼저 시도 (우리 export 형식: unit1/unit2/real 이 세로로 stacked)
        int labeled = importContractSheetByLabel(sheet, year, month);
        if (labeled > 0) return labeled;
        // 2) position-based fallback (원본 엑셀 형식: unit1/unit2/real 이 같은 행에 가로로)
        return importContractSheetByPosition(sheet, year, month);
    }

    /**
     * 우리 내려받기 양식의 도급내역 — 머리글 글자로 칸을 찾는다.
     *
     * <p>호기 표는 "일별 | 스팀량 | 운영단가 | … | 운휴비용 | 소계", 실 스팀량 표는 "일별 | 1호기 스팀량 | …" 머리글이다.
     * 원본 양식에도 "일별"·"운영단가" 는 있지만 "운휴비용"·"1호기 스팀량" 같은 한 칸짜리 제목은 우리 양식에만 있어서
     * 그걸로 가른다(원본이면 0 을 돌려주고 원본용 읽기로 넘어간다). 칸 목록은 내려받기와 같은 상수를 쓴다.
     */
    private int importContractSheetByLabel(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        for (int header = 0; header <= SheetLabels.MAX_ROW; header += 1) {
            Integer dayCol = SheetLabels.column(sheet, header, "일별", 0);
            if (dayCol == null) continue;
            String section;
            String[] headers;
            String[] fields;
            if (SheetLabels.column(sheet, header, CONTRACT_REAL_HEADERS[0], 0) != null) {
                section = "real";
                headers = CONTRACT_REAL_HEADERS;
                fields = REAL_FIELDS;
            } else if (SheetLabels.column(sheet, header, "운휴비용", 0) != null) {
                String above = SheetLabels.key(SheetLabels.text(sheet, header - 1, dayCol));
                section = above.equals(SheetLabels.key("1호기")) ? "unit1" : above.equals(SheetLabels.key("2호기")) ? "unit2" : null;
                headers = CONTRACT_UNIT_HEADERS;
                fields = UNIT_FIELDS;
            } else {
                continue;
            }
            if (section == null) continue;
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int idx = 0; idx < fields.length; idx += 1) {
                putIfFound(columns, fields[idx], SheetLabels.column(sheet, header, headers[idx], dayCol + 1));
            }
            for (int r = header + 1; r <= header + 33; r += 1) {
                int day = parseDay(SheetLabels.text(sheet, r, dayCol));
                if (day < 1 || day > 31) continue;
                Row row = sheet.getRow(r);
                for (Map.Entry<String, Integer> c : columns.entrySet()) {
                    String value = readCellValue(row.getCell(c.getValue()));
                    if (value == null || value.isBlank()) continue;
                    imported += upsertCell(monthKey, year, month, CONTRACT_TABLE, section + "|" + day + "|" + c.getKey(), 1, value);
                }
            }
        }
        return imported;
    }

    private int importPlanVsSheet(Sheet sheet, int year, int month) {
        // 우리 내려받기 양식은 "구분 | 행 | 1월 …" 머리글이 있다 — 원본에는 "행" 칸이 없다.
        if (SheetLabels.findRowWithAll(sheet, 0, "구분", "행") >= 0) {
            return importPlanVsSheetByLabel(sheet, year, month);
        }
        return importPlanVsSheetByPosition(sheet, year, month);
    }

    /**
     * 우리 내려받기 양식의 사업계획대비 — 세 표 모두 머리글로 찾는다.
     *
     * <pre>
     *  월별 표 : "구분 | 행 | 1월 … 12월"      — 구분 칸 인건비/운영비/합계 × 행 칸 계획/실적
     *  요약 표 : "구분 (백만원)" 아래 라벨 줄     — 라벨 바로 오른쪽 칸이 값(머리글이 따로 없다)
     *  비고 표 : "비고 | 호기 | 내용"
     * </pre>
     * 자동 계산 칸(달성율·증감, 운영비·합계 실적, 요약의 증감)은 예전처럼 읽지 않는다.
     */
    private int importPlanVsSheetByLabel(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);

        int header = SheetLabels.findRowWithAll(sheet, 0, "구분", "행");
        int groupCol = SheetLabels.column(sheet, header, "구분", 0);
        int kindCol = SheetLabels.column(sheet, header, "행", 0);
        Map<Integer, Integer> months = SheetLabels.monthColumns(sheet, header);
        int breakRow = SheetLabels.findRow(sheet, header + 1, k -> k.startsWith(SheetLabels.key("구분 (백만원)")));
        int notesHeader = SheetLabels.findRowWithAll(sheet, header + 1, "비고", "호기", "내용");
        int planEnd = breakRow > 0 ? breakRow : (notesHeader > 0 ? notesHeader : sheet.getLastRowNum() + 1);

        for (int r = header + 1; r < planEnd; r += 1) {
            String section = mapPlanGroup(SheetLabels.text(sheet, r, groupCol));
            String kind = mapPlanKind(SheetLabels.text(sheet, r, kindCol));
            if (section == null || kind == null) continue;
            if ("rate".equals(kind) || "diff".equals(kind)) continue;
            if (("operation".equals(section) || "total".equals(section)) && "actual".equals(kind)) continue;
            Row row = sheet.getRow(r);
            for (Map.Entry<Integer, Integer> m : months.entrySet()) {
                String v = readCellValue(row.getCell(m.getValue()));
                if (v == null || v.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, PLAN_VS_TABLE, section + "|" + kind + "-m" + m.getKey(), 1, v);
            }
        }

        if (breakRow > 0) {
            int labelCol = SheetLabels.columnStartingWith(sheet, breakRow, "구분 (백만원)", 0);
            int end = notesHeader > 0 ? notesHeader : sheet.getLastRowNum() + 1;
            for (int r = breakRow + 1; r < end; r += 1) {
                String field = mapBreakField(SheetLabels.text(sheet, r, labelCol));
                if (field == null || field.endsWith("-diff") || "prev-rate".equals(field)) continue;
                Row row = sheet.getRow(r);
                String v = row == null ? null : readCellValue(row.getCell(labelCol + 1));
                if (v == null || v.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, PLAN_VS_TABLE, "break|" + field, 1, v);
            }
        }

        if (notesHeader > 0) {
            int monthCol = SheetLabels.column(sheet, notesHeader, "비고", 0);
            int unitCol = SheetLabels.column(sheet, notesHeader, "호기", 0);
            int textCol = SheetLabels.column(sheet, notesHeader, "내용", 0);
            for (int r = notesHeader + 1; r <= sheet.getLastRowNum(); r += 1) {
                int m = parseMonthLabel(SheetLabels.text(sheet, r, monthCol));
                String unitText = SheetLabels.text(sheet, r, unitCol);
                String unit = unitText.contains("#1") ? "u1" : unitText.contains("#2") ? "u2" : null;
                if (unit == null || m < 1 || m > 12) continue;
                String v = SheetLabels.text(sheet, r, textCol);
                if (v.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, PLAN_VS_TABLE, "notes|m" + m + "-" + unit, 1, v);
            }
        }
        return imported;
    }

    /** 전년대비 — 지금은 가져오기에서 쓰지 않는다(importWorkbook 참고). 다시 켤 때를 위해 제목 기반 읽기만 남긴다. */
    private int importYoySheet(Sheet sheet, int year) {
        return importYoySheetByPosition(sheet, year);
    }

    private int importOperationSheet(Sheet sheet, int year) {
        int labeled = importOperationSheetByLabel(sheet, year);
        if (labeled > 0) return labeled;
        return importOperationSheetByPosition(sheet, year);
    }

    /**
     * 우리 내려받기 양식의 가동실적 — "월 | 1호기 계획 | …" / "월 | 스팀량 계획 | …" 머리글로 칸을 찾는다.
     * 달성율·증감은 자동 계산 칸이라 읽지 않는다. 칸 목록은 내려받기와 같은 상수를 쓴다.
     */
    private int importOperationSheetByLabel(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = String.valueOf(year);
        String[][][] tables = {OPERATION_HOURS_HEADERS, OPERATION_METRIC_HEADERS};
        String[] prefixes = {"hours", "metric"};
        for (int t = 0; t < tables.length; t += 1) {
            String[][] headers = tables[t];
            int header = SheetLabels.findRowWithAll(sheet, 0, "월", headers[0][1]);
            if (header < 0) continue;
            int monthCol = SheetLabels.column(sheet, header, "월", 0);
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (String[] h : headers) {
                if (h[0].endsWith("-rate") && "hours".equals(prefixes[t])) continue;
                if (h[0].endsWith("-diff")) continue;
                putIfFound(columns, h[0], SheetLabels.column(sheet, header, h[1], monthCol + 1));
            }
            Map<Integer, Integer> monthRows = SheetLabels.monthRows(sheet, monthCol, header + 1, header + 13, null);
            for (Map.Entry<Integer, Integer> m : monthRows.entrySet()) {
                Row row = sheet.getRow(m.getValue());
                for (Map.Entry<String, Integer> c : columns.entrySet()) {
                    String v = readCellValue(row.getCell(c.getValue()));
                    if (v == null || v.isBlank()) continue;
                    imported += upsertCell(monthKey, year, m.getKey(), OPERATION_TABLE,
                            prefixes[t] + "|" + m.getKey() + "|" + c.getKey(), 1, v);
                }
            }
        }
        return imported;
    }

    private int importTotalCostSheet(Sheet sheet, int year) {
        int labeled = importTotalCostSheetByLabel(sheet, year);
        if (labeled > 0) return labeled;
        return importTotalCostSheetByPosition(sheet, year);
    }

    /**
     * 우리 내려받기 양식의 총 운영비용 — 블록 이름 줄 다음의 "월 | burn-1 | burn-2 | …" 머리글이 곧 필드 이름이라
     * 그 글자로 칸을 찾는다. 자동 계산 필드는 읽지 않는다.
     */
    private int importTotalCostSheetByLabel(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = String.valueOf(year);
        for (TotalCostBlock block : TOTAL_COST_BLOCKS) {
            int labelRow = SheetLabels.findRow(sheet, 0, k -> k.equals(SheetLabels.key(block.label)));
            if (labelRow < 0) continue;
            int header = SheetLabels.findRowWithAll(sheet, labelRow + 1, "월", block.fields[0]);
            if (header < 0 || header > labelRow + 3) continue;
            int monthCol = SheetLabels.column(sheet, header, "월", 0);
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (String field : block.fields) {
                if (isAutoTotalCostField(block.section, field)) continue;
                putIfFound(columns, field, SheetLabels.column(sheet, header, field, monthCol + 1));
            }
            Map<Integer, Integer> monthRows = SheetLabels.monthRows(sheet, monthCol, header + 1, header + 13, null);
            for (Map.Entry<Integer, Integer> m : monthRows.entrySet()) {
                Row row = sheet.getRow(m.getValue());
                for (Map.Entry<String, Integer> c : columns.entrySet()) {
                    String v = readCellValue(row.getCell(c.getValue()));
                    if (v == null || v.isBlank()) continue;
                    imported += upsertCell(monthKey, year, m.getKey(), TOTAL_COST_TABLE,
                            block.section + "|" + m.getKey() + "|" + c.getKey(), 1, v);
                }
            }
        }
        return imported;
    }

    private boolean isAutoTotalCostField(String section, String field) {
        return switch (section) {
            case "op-burn" -> List.of("burn-sum", "steam-sum", "steam-1", "steam-2", "stop-1", "stop-2", "runtime-1", "runtime-2").contains(field);
            case "op-cost" -> List.of("u1-steam", "u1-op", "u1-stop", "u1-sub", "u2-steam", "u2-op", "u2-stop", "u2-sub", "total").contains(field);
            case "self-srf" -> List.of("floor-sub", "fly-sub").contains(field);
            case "self-util" -> List.of("psub", "naoh-cost").contains(field);
            case "mat-oil" -> List.of("sub", "cost", "ck-cost").contains(field);
            case "mat-chem" -> List.of("u-cost", "s-cost").contains(field);
            case "mat-misc" -> List.of("a-cost", "k-cost", "total").contains(field);
            default -> false;
        };
    }

    private int importContractSheetByPosition(Sheet sheet, int year, int month) {
        String mk = monthKey(year, month);
        int imported = 0;
        // 열은 머리글(호기 + 항목)로, 행은 '일별' 칸으로 찾는다. 열 번호를 박아 두면
        // 원본에 열이 하나만 추가돼도 오른쪽 값이 전부 다른 항목으로 저장된다.
        ContractLayout layout = ContractLayout.of(sheet);
        imported += importContractSection(sheet, mk, year, month, layout, "unit1", UNIT_FIELDS);
        imported += importContractSection(sheet, mk, year, month, layout, "unit2", UNIT_FIELDS);
        imported += importContractSection(sheet, mk, year, month, layout, "real", REAL_FIELDS);
        // 월합계 표 인건비 → summary|0|labor. "구 분 / 인건비 / 총 금액" 머리글 아래 첫 줄의 "인건비" 칸이다.
        //  (예전에는 J43 으로 박아 뒀다. 도급내역 페이지 월합계 표의 운영비 = 총금액 − 인건비 계산 입력)
        int header = SheetLabels.findRowWithAll(sheet, 0, "구 분", "인건비", "총 금액");
        if (header >= 0) {
            Integer nameCol = SheetLabels.column(sheet, header, "구 분", 0);
            Integer laborCol = SheetLabels.column(sheet, header, "인건비", 0);
            int dataRow = SheetLabels.findRowInColumn(sheet, nameCol, header + 1, header + 5, k -> !k.isEmpty());
            if (laborCol != null && dataRow >= 0) {
                Row row = sheet.getRow(dataRow);
                String labor = row == null ? null : readCellValue(row.getCell(laborCol));
                if (labor != null && !labor.isBlank()) {
                    imported += upsertCell(mk, year, month, CONTRACT_TABLE, "summary|0|labor", 1, labor);
                }
            }
        }
        return imported;
    }

    private int importContractSection(Sheet sheet, String monthKey, int year, int month,
                                      ContractLayout layout, String section, String[] fields) {
        Map<String, Integer> columns = layout.columns(section);
        Map<Integer, Integer> dayRows = layout.dayRows(section);
        if (columns.isEmpty() || dayRows.isEmpty()) return 0;

        int imported = 0;
        for (int day = 1; day <= 31; day += 1) {
            Integer rowIndex = dayRows.get(day);
            if (rowIndex == null) continue;
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            for (String field : fields) {
                Integer col = columns.get(field);
                if (col == null) continue;          // 머리글에 없는 항목 — 건너뛴다
                String value = readCellValue(row.getCell(col));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, CONTRACT_TABLE,
                        section + "|" + day + "|" + field, 1, value);
            }
        }
        return imported;
    }

    /**
     * 원본 "사업계획대비" 시트 — {@link #planVsLayout} 으로 찾은 칸에서 읽는다.
     *
     * <pre>
     *  ① 월별 표   : "구 분 | 1월 … 12월" 머리글, A열 "(인건비)/(운영비)/(합계)" × B열 "…계획/…실적"
     *  ② 이달 요약 : A열 "위탁운영비" 줄 — 윗줄 "계획/실적" 순서로 이달·누계·전월, "비 고" 칸
     *  ③ 비고      : A열 "비 고" 아래 — "N월" 과 "#1/#2 폐합성수지소각로" 라벨 오른쪽 글자
     * </pre>
     */
    private int importPlanVsSheetByPosition(Sheet sheet, int year, int month) {
        String mk = monthKey(year, month);
        int imported = 0;
        PlanVsLayout layout = planVsLayout(sheet);
        for (Map.Entry<String, Integer> r : layout.rows().entrySet()) {
            Row row = sheet.getRow(r.getValue());
            for (Map.Entry<Integer, Integer> m : layout.months().entrySet()) {
                String value = readCellValue(row.getCell(m.getValue()));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, month, PLAN_VS_TABLE, r.getKey() + "-m" + m.getKey(), 1, value);
            }
        }
        if (layout.breakRow() > 0) {
            Row row = sheet.getRow(layout.breakRow());
            for (Map.Entry<String, Integer> b : layout.breakCols().entrySet()) {
                imported += upsertCellIfPresent(mk, year, month, PLAN_VS_TABLE, "break|" + b.getKey(), 1, row, b.getValue());
            }
        }
        for (Map.Entry<String, int[]> n : layout.notes().entrySet()) {
            int row = n.getValue()[0];
            StringBuilder note = new StringBuilder();
            for (int i = 1; i < n.getValue().length; i += 1) {
                String text = SheetLabels.text(sheet, row, n.getValue()[i]);
                if (text.isBlank()) continue;
                if (note.length() > 0) note.append(' ');
                note.append(text);
            }
            if (note.length() == 0) continue;
            imported += upsertCell(mk, year, month, PLAN_VS_TABLE, "notes|" + n.getKey(), 1, note.toString());
        }
        return imported;
    }

    /** 구분 칸(머리글과 첫 월 칸 사이)의 "…계획" / "…실적" — 연도가 붙어 있어도("2025계획") 끝으로 가른다. */
    private String kindOf(Sheet sheet, int row, int fromCol, int untilCol) {
        for (int col = fromCol; col < untilCol; col += 1) {
            String k = SheetLabels.key(SheetLabels.text(sheet, row, col));
            if (k.endsWith("계획")) return "plan";
            if (k.endsWith("실적")) return "actual";
        }
        return null;
    }

    /**
     * 원본 "전년대비" 시트 — "구 분 | 1월 … 12월" 머리글마다 표 하나. 각 표의 첫 "…실적" 줄이 전년 실적이다.
     * 표 이름(A열)이 "…합계" 면 비용(cost|prev), "…스팀생산량" 이면 스팀량(ton|prev).
     */
    private int importYoySheetByPosition(Sheet sheet, int year) {
        String mk = String.valueOf(year);
        int imported = 0;
        for (Map.Entry<String, Integer> r : yoyRows(sheet).entrySet()) {
            if (!r.getKey().endsWith("|prev")) continue;
            Row row = sheet.getRow(r.getValue());
            for (Map.Entry<Integer, Integer> m : SheetLabels.monthColumns(sheet, yoyHeaderAbove(sheet, r.getValue())).entrySet()) {
                String value = readCellValue(row.getCell(m.getValue()));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, 0, YOY_TABLE, r.getKey() + "-m" + m.getKey(), 1, value);
            }
        }
        return imported;
    }

    /** 원본 "계획대비 가동실적" 시트 — {@link #operationLayout} 으로 찾은 칸에서 읽는다. */
    private int importOperationSheetByPosition(Sheet sheet, int year) {
        String mk = String.valueOf(year);
        int imported = 0;
        OperationLayout layout = operationLayout(sheet);
        for (Map.Entry<Integer, Integer> m : layout.monthRows().entrySet()) {
            Row row = sheet.getRow(m.getValue());
            if (row == null) continue;
            for (Map.Entry<String, Integer> h : layout.hours().entrySet()) {
                String value = readCellValue(row.getCell(h.getValue()));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, m.getKey(), OPERATION_TABLE, "hours|" + m.getKey() + "|" + h.getKey(), 1, value);
            }
            for (Map.Entry<String, Integer> h : layout.metrics().entrySet()) {
                String value = readCellValue(row.getCell(h.getValue()));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, m.getKey(), OPERATION_TABLE, "metric|" + m.getKey() + "|" + h.getKey(), 1, value);
            }
        }
        return imported;
    }

    private static void putIfFound(Map<String, Integer> map, String key, Integer col) {
        if (col != null) map.put(key, col);
    }

    /**
     * 원본 "총 운영비용" 시트 — 표 세 개(운영내역 / 자체 운영비용 / 소모품)를 표 제목으로 찾고,
     * 칸은 설비·항목 제목으로, 줄은 A열 "N월" 로 찾는다.
     *
     * <p>예전 위치 읽기는 위탁운영비용 11칸을 O열부터 순서대로 읽어서, 원본의 "1호기(천원): 고정비·스팀구매비·합계"
     * 세 칸 구조와 어긋났다(차감비 자리에 X열 등). 제목으로 찾으면서 바로잡았다 — 호기별 스팀량(u1-steam)은
     * 이 표에 없어(운영내역의 스팀생산량과 같다) 건너뛴다. 화면에서는 이 칸들이 도급내역에서 자동 계산된다.
     */
    private int importTotalCostSheetByPosition(Sheet sheet, int year) {
        String mk = String.valueOf(year);
        int imported = 0;
        for (String table : new String[]{"op", "self", "mat"}) {
            String anchor = totalCostAnchor(table);
            Map<String, Integer> columns = totalCostColumns(sheet, anchor, totalCostHeaders(table));
            for (Map.Entry<Integer, Integer> m : totalCostMonthRows(sheet, anchor).entrySet()) {
                Row row = sheet.getRow(m.getValue());
                if (row == null) continue;
                for (Map.Entry<String, Integer> c : columns.entrySet()) {
                    String value = readCellValue(row.getCell(c.getValue()));
                    if (value == null || value.isBlank()) continue;
                    String[] sectionField = c.getKey().split("\\|", 2);
                    imported += upsertCell(mk, year, m.getKey(), TOTAL_COST_TABLE,
                            sectionField[0] + "|" + m.getKey() + "|" + sectionField[1], 1, value);
                }
            }
        }
        return imported;
    }

    /**
     * 총 운영비용 표 한 벌 — {@code {구역, 필드, 층, 위 제목, 아래 제목}}.
     * 층 1 = 표 제목 줄 + 다음 줄, 층 2 = 그다음 두 줄(위탁운영비용의 호기별 고정비·스팀구매비), top = 제목 줄에만 이름이 있는 칸.
     * 같은 이름이 한 설비 안에 두 번 나오면(바닥재·비산재) 왼쪽부터 순서대로 찾으므로 표 순서대로 적는다.
     */
    private static final String[][] TOTAL_COST_OP_HEADERS = {
            {"op-burn", "burn-1", "1", "폐기물 실소각량", "1호기"}, {"op-burn", "burn-2", "1", "폐기물 실소각량", "2호기"},
            {"op-burn", "burn-sum", "1", "폐기물 실소각량", "합계"},
            {"op-burn", "runtime-1", "1", "소각로 가동시간", "1호기"}, {"op-burn", "runtime-2", "1", "소각로 가동시간", "2호기"},
            {"op-burn", "stop-1", "1", "소각로 운휴시간", "1호기"}, {"op-burn", "stop-2", "1", "소각로 운휴시간", "2호기"},
            {"op-burn", "steam-1", "1", "스팀생산량", "1호기"}, {"op-burn", "steam-2", "1", "스팀생산량", "2호기"},
            {"op-burn", "steam-sum", "1", "스팀생산량", "합계"},
            {"op-burn", "rate-1", "1", "스팀생산원단위", "1호기"}, {"op-burn", "rate-2", "1", "스팀생산원단위", "2호기"},
            {"op-cost", "unit", "1", "위탁운영비용", "위탁계약단가"},
            {"op-cost", "u1-stop", "2", "1호기(천원)", "고정비"}, {"op-cost", "u1-op", "2", "1호기(천원)", "스팀구매비"},
            {"op-cost", "u1-sub", "2", "1호기(천원)", "합 계"},
            {"op-cost", "u2-stop", "2", "2호기(천원)", "고정비"}, {"op-cost", "u2-op", "2", "2호기(천원)", "스팀구매비"},
            {"op-cost", "u2-sub", "2", "2호기(천원)", "합 계"},
            {"op-cost", "deduct", "1", "위탁운영비용", "차감비"}, {"op-cost", "total", "1", "위탁운영비용", "비용합계"},
    };
    private static final String[][] TOTAL_COST_SELF_HEADERS = {
            {"self-srf", "in-qty", "1", "외부 폐합성수지 수익금(톤, 천원)", "입고량"},
            {"self-srf", "in-rev", "1", "외부 폐합성수지 수익금(톤, 천원)", "수익비용"},
            {"self-srf", "floor-recycle", "1", "폐기물 처리량(톤)", "바닥재(재활용)"},
            {"self-srf", "floor-bury", "1", "폐기물 처리량(톤)", "바닥재(매립)"},
            {"self-srf", "floor-sub", "1", "폐기물 처리량(톤)", "바닥재 소계"},
            {"self-srf", "fly-solid", "1", "폐기물 처리량(톤)", "비산재(고형화)"},
            {"self-srf", "fly-bury", "1", "폐기물 처리량(톤)", "비산재(매립)"},
            {"self-srf", "fly-sub", "1", "폐기물 처리량(톤)", "비산재 소계"},
            {"self-srf", "cost-floor", "1", "폐기물처리비용(천원)", "바닥재"},
            {"self-srf", "cost-fly", "1", "폐기물처리비용(천원)", "비산재"},
            {"self-srf", "cost-misc", "1", "폐기물처리비용(천원)", "기타(내화물 등)"},
            {"self-util", "p1", "1", "전력비용(Kw, 천원)", "1호기사용량"},
            {"self-util", "p2", "1", "전력비용(Kw, 천원)", "2호기사용량"},
            {"self-util", "prate", "1", "전력비용(Kw, 천원)", "전력단가"},
            {"self-util", "psub", "1", "전력비용(Kw, 천원)", "비용소계"},
            {"self-util", "naoh-qty", "1", "가성소다 (Kg, 천원)", "구입량"},
            {"self-util", "naoh-unit", "1", "가성소다 (Kg, 천원)", "단가"},
            {"self-util", "naoh-cost", "1", "가성소다 (Kg, 천원)", "구입비용"},
            {"self-util", "water", "1", "기타 운영비용(천원)", "용수사용비"},
            {"self-util", "waste-charge", "1", "기타 운영비용(천원)", "폐기물처분부담금"},
            {"self-util", "etc", "1", "기타 운영비용(천원)", "기타"},
            {"self-total", "total", "top", "비용합계", ""},
            {"self-total", "burn-rate", "1", "원단위", "처리량당 비용"},
            {"self-total", "steam-rate", "1", "원단위", "스팀톤당 비용"},
    };
    private static final String[][] TOTAL_COST_MAT_HEADERS = {
            {"mat-oil", "u1", "1", "소각로별 부생유 사용량", "#1폐합성"}, {"mat-oil", "u2", "1", "소각로별 부생유 사용량", "#2폐합성"},
            {"mat-oil", "sub", "1", "소각로별 부생유 사용량", "사용량소계"}, {"mat-oil", "unit", "1", "소각로별 부생유 사용량", "단가"},
            {"mat-oil", "cost", "1", "소각로별 부생유 사용량", "비용"},
            {"mat-oil", "ck-qty", "1", "청관제", "구입량"}, {"mat-oil", "ck-unit", "1", "청관제", "단가"}, {"mat-oil", "ck-cost", "1", "청관제", "구입비용"},
            {"mat-chem", "u-qty", "1", "요소수", "구입량"}, {"mat-chem", "u-unit", "1", "요소수", "단가"}, {"mat-chem", "u-cost", "1", "요소수", "구입비용"},
            {"mat-chem", "s-qty", "1", "소 금", "구입량"}, {"mat-chem", "s-unit", "1", "소 금", "단가"}, {"mat-chem", "s-cost", "1", "소 금", "구입비용"},
            {"mat-misc", "a-qty", "1", "분말활성탄", "구입량"}, {"mat-misc", "a-unit", "1", "분말활성탄", "단가"}, {"mat-misc", "a-cost", "1", "분말활성탄", "구입비용"},
            {"mat-misc", "k-qty", "1", "크링커제거제", "구입량"}, {"mat-misc", "k-unit", "1", "크링커제거제", "단가"}, {"mat-misc", "k-cost", "1", "크링커제거제", "구입비용"},
            {"mat-misc", "total", "1", "합 계", "사용비용"},
    };



    /** "구역|필드" → 열. 표 제목·항목 제목으로 찾는다. 테스트에서도 쓴다. */
    static Map<String, Integer> totalCostColumns(Sheet sheet, String anchor, String[][] headers) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        int groupRow = SheetLabels.findRow(sheet, 0, k -> k.startsWith(SheetLabels.key(anchor)));
        if (groupRow < 0) return columns;
        HeaderColumnIndex level1 = HeaderColumnIndex.build(sheet, groupRow, groupRow, groupRow + 1, groupRow + 2, groupRow + 20, 0);
        HeaderColumnIndex level2 = HeaderColumnIndex.build(sheet, -1, groupRow + 1, groupRow + 2, groupRow + 3, groupRow + 20, 0);
        Map<String, Integer> cursors = new LinkedHashMap<>();
        for (String[] h : headers) {
            String cursorKey = h[2] + "|" + h[3];
            int cursor = cursors.getOrDefault(cursorKey, 0);
            Integer col = switch (h[2]) {
                case "top" -> level1.columnByTopLabel(h[3]);
                case "2" -> level2.columnAfter(cursor, h[3], h[4]);
                default -> level1.columnAfter(cursor, h[3], h[4]);
            };
            if (col == null) continue;
            columns.put(h[0] + "|" + h[1], col);
            cursors.put(cursorKey, col + 1);
        }
        return columns;
    }

    static String[][] totalCostHeaders(String table) {
        return switch (table) {
            case "op" -> TOTAL_COST_OP_HEADERS;
            case "self" -> TOTAL_COST_SELF_HEADERS;
            default -> TOTAL_COST_MAT_HEADERS;
        };
    }

    private void hydrateContractComputedCells(Map<String, Object> cells) {
        for (int day = 1; day <= 31; day += 1) {
            for (String section : new String[]{"unit1", "unit2"}) {
                String prefix = section + "|" + day + "|";
                Double steam = getNumber(cells, CONTRACT_TABLE, prefix + "steam");
                Double unit = getNumber(cells, CONTRACT_TABLE, prefix + "unit");
                Double runtime = getNumber(cells, CONTRACT_TABLE, prefix + "runtime");
                Double fixed = getNumber(cells, CONTRACT_TABLE, prefix + "fixed");
                Double oper = multiply(steam, unit);
                Double stop = multiply(runtime, fixed);
                putNumber(cells, CONTRACT_TABLE, prefix + "oper", oper);
                putNumber(cells, CONTRACT_TABLE, prefix + "stop", stop);
                putNumber(cells, CONTRACT_TABLE, prefix + "sub", sumNullable(oper, stop));
            }

            Double steam1 = getNumber(cells, CONTRACT_TABLE, "real|" + day + "|steam-1");
            Double unit1 = getNumber(cells, CONTRACT_TABLE, "real|" + day + "|unit-1");
            Double steam2 = getNumber(cells, CONTRACT_TABLE, "real|" + day + "|steam-2");
            Double unit2 = getNumber(cells, CONTRACT_TABLE, "real|" + day + "|unit-2");
            Double oper1 = multiply(steam1, unit1);
            Double oper2 = multiply(steam2, unit2);
            putNumber(cells, CONTRACT_TABLE, "real|" + day + "|oper-1", oper1);
            putNumber(cells, CONTRACT_TABLE, "real|" + day + "|oper-2", oper2);
            putNumber(cells, CONTRACT_TABLE, "real|" + day + "|steam-sum", sumNullable(steam1, steam2));
            putNumber(cells, CONTRACT_TABLE, "real|" + day + "|total-cost", sumNullable(oper1, oper2));
        }
    }

    private void hydratePlanVsComputedValues(Map<String, String> values, int year) {
        for (int month = 1; month <= 12; month += 1) {
            ContractMonth contract = collectContractMonth(year, month);
            // Excel 공식: 운영비(K43) = 총금액(L43) - 인건비(J43)
            // = (unit1.sub + unit2.sub 합계) - labor.actual
            double totalSub = contract.unit1Oper() + contract.unit1Stop() + contract.unit2Oper() + contract.unit2Stop();
            double totalKw = totalSub / 1000.0;
            Double laborActual = parseNumber(values.get("labor|actual-m" + month));
            Double operationActualValue = null;
            if (totalKw > 0 && laborActual != null) {
                double operationActual = totalKw - laborActual;
                values.put("operation|actual-m" + month, numberString(operationActual));
                operationActualValue = operationActual;
            }

            Double totalPlan = parseNumber(values.get("total|plan-m" + month));
            Double totalActual = sumNullable(laborActual, operationActualValue);
            if (totalActual != null) {
                values.put("total|actual-m" + month, numberString(totalActual));
            }
            if (totalPlan != null && totalPlan != 0 && totalActual != null) {
                values.put("total|rate-m" + month, numberString(totalActual / totalPlan * 100.0));
            }
            if (totalPlan != null && totalActual != null) {
                values.put("total|diff-m" + month, numberString(totalActual - totalPlan));
            }
        }
    }

    private void hydrateYoyComputedCells(Map<String, Object> cells, int year) {
        Map<String, String> planValues = collectPlanVsValues(year, 12);
        Map<String, String> previousPlanValues = collectPlanVsValues(year - 1, 12);
        hydratePlanVsComputedValues(planValues, year);
        hydratePlanVsComputedValues(previousPlanValues, year - 1);
        for (int month = 1; month <= 12; month += 1) {
            ContractMonth contract = collectContractMonth(year, month);
            ContractMonth previousContract = collectContractMonth(year - 1, month);
            Double costCurr = parseNumber(planValues.get("total|actual-m" + month));
            Double tonCurr = nonZero(contract.unit1Steam() + contract.unit2Steam());
            Double costPrev = parseNumber(previousPlanValues.get("total|actual-m" + month));
            Double tonPrev = nonZero(previousContract.unit1Steam() + previousContract.unit2Steam());
            putNumber(cells, YOY_TABLE, "cost|prev-m" + month, costPrev);
            putNumber(cells, YOY_TABLE, "ton|prev-m" + month, tonPrev);
            putNumber(cells, YOY_TABLE, "cost|curr-m" + month, costCurr);
            putNumber(cells, YOY_TABLE, "ton|curr-m" + month, tonCurr);

            if (costPrev != null && costCurr != null) {
                putNumber(cells, YOY_TABLE, "cost|diff-m" + month, costCurr - costPrev);
            }
            if (tonPrev != null && tonCurr != null) {
                putNumber(cells, YOY_TABLE, "ton|diff-m" + month, tonCurr - tonPrev);
            }
        }
    }

    private void hydrateOperationComputedCells(Map<String, Object> cells) {
        for (int month = 1; month <= 12; month += 1) {
            for (String unit : new String[]{"u1", "u2"}) {
                Double plan = getNumber(cells, OPERATION_TABLE, "hours|" + month + "|" + unit + "-plan");
                Double actual = getNumber(cells, OPERATION_TABLE, "hours|" + month + "|" + unit + "-actual");
                if (plan != null && plan != 0 && actual != null) {
                    putNumber(cells, OPERATION_TABLE, "hours|" + month + "|" + unit + "-rate", actual / plan * 100.0);
                }
            }
            for (String metric : new String[]{"steam", "rate", "cost"}) {
                Double plan = getNumber(cells, OPERATION_TABLE, "metric|" + month + "|" + metric + "-plan");
                Double actual = getNumber(cells, OPERATION_TABLE, "metric|" + month + "|" + metric + "-actual");
                if (plan != null && actual != null) {
                    putNumber(cells, OPERATION_TABLE, "metric|" + month + "|" + metric + "-diff", actual - plan);
                }
            }
        }
    }

    private void hydrateTotalCostComputedCells(Map<String, Object> cells, int year) {
        for (int month = 1; month <= 12; month += 1) {
            ContractMonth contract = collectContractMonth(year, month);
            Double u1Actual = collectOperationActual(year, month, "u1");
            Double u2Actual = collectOperationActual(year, month, "u2");

            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|steam-1", nonZero(contract.unit1Steam()));
            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|steam-2", nonZero(contract.unit2Steam()));
            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|stop-1", nonZero(contract.unit1Runtime()));
            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|stop-2", nonZero(contract.unit2Runtime()));
            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|runtime-1", u1Actual);
            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|runtime-2", u2Actual);
            Double burn1 = getNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|burn-1");
            Double burn2 = getNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|burn-2");
            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|burn-sum", sumNullable(burn1, burn2));
            putNumber(cells, TOTAL_COST_TABLE, "op-burn|" + month + "|steam-sum", nonZero(contract.unit1Steam() + contract.unit2Steam()));

            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u1-steam", nonZero(contract.unit1Steam()));
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u2-steam", nonZero(contract.unit2Steam()));
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u1-op", divide(nonZero(contract.unit1Oper()), 1000.0));
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u2-op", divide(nonZero(contract.unit2Oper()), 1000.0));
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u1-stop", divide(nonZero(contract.unit1Stop()), 1000.0));
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u2-stop", divide(nonZero(contract.unit2Stop()), 1000.0));
            Double u1Sub = sumNullable(getNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u1-op"), getNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u1-stop"));
            Double u2Sub = sumNullable(getNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u2-op"), getNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u2-stop"));
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u1-sub", u1Sub);
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|u2-sub", u2Sub);
            Double deduct = getNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|deduct");
            putNumber(cells, TOTAL_COST_TABLE, "op-cost|" + month + "|total", sumNullable(sumNullable(u1Sub, u2Sub), deduct == null ? null : -deduct));

            Double floorRecycle = getNumber(cells, TOTAL_COST_TABLE, "self-srf|" + month + "|floor-recycle");
            Double floorBury = getNumber(cells, TOTAL_COST_TABLE, "self-srf|" + month + "|floor-bury");
            Double flySolid = getNumber(cells, TOTAL_COST_TABLE, "self-srf|" + month + "|fly-solid");
            Double flyBury = getNumber(cells, TOTAL_COST_TABLE, "self-srf|" + month + "|fly-bury");
            putNumber(cells, TOTAL_COST_TABLE, "self-srf|" + month + "|floor-sub", sumNullable(floorRecycle, floorBury));
            putNumber(cells, TOTAL_COST_TABLE, "self-srf|" + month + "|fly-sub", sumNullable(flySolid, flyBury));

            Double p1 = getNumber(cells, TOTAL_COST_TABLE, "self-util|" + month + "|p1");
            Double p2 = getNumber(cells, TOTAL_COST_TABLE, "self-util|" + month + "|p2");
            Double prate = getNumber(cells, TOTAL_COST_TABLE, "self-util|" + month + "|prate");
            putNumber(cells, TOTAL_COST_TABLE, "self-util|" + month + "|psub", multiply(sumNullable(p1, p2), prate));
            Double naohQty = getNumber(cells, TOTAL_COST_TABLE, "self-util|" + month + "|naoh-qty");
            Double naohUnit = getNumber(cells, TOTAL_COST_TABLE, "self-util|" + month + "|naoh-unit");
            putNumber(cells, TOTAL_COST_TABLE, "self-util|" + month + "|naoh-cost", multiply(naohQty, naohUnit));

            Double oilU1 = getNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|u1");
            Double oilU2 = getNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|u2");
            Double oilSub = sumNullable(oilU1, oilU2);
            putNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|sub", oilSub);
            putNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|cost", multiply(oilSub, getNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|unit")));
            putNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|ck-cost", multiply(getNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|ck-qty"), getNumber(cells, TOTAL_COST_TABLE, "mat-oil|" + month + "|ck-unit")));

            putNumber(cells, TOTAL_COST_TABLE, "mat-chem|" + month + "|u-cost", multiply(getNumber(cells, TOTAL_COST_TABLE, "mat-chem|" + month + "|u-qty"), getNumber(cells, TOTAL_COST_TABLE, "mat-chem|" + month + "|u-unit")));
            putNumber(cells, TOTAL_COST_TABLE, "mat-chem|" + month + "|s-cost", multiply(getNumber(cells, TOTAL_COST_TABLE, "mat-chem|" + month + "|s-qty"), getNumber(cells, TOTAL_COST_TABLE, "mat-chem|" + month + "|s-unit")));

            Double aCost = multiply(getNumber(cells, TOTAL_COST_TABLE, "mat-misc|" + month + "|a-qty"), getNumber(cells, TOTAL_COST_TABLE, "mat-misc|" + month + "|a-unit"));
            Double kCost = multiply(getNumber(cells, TOTAL_COST_TABLE, "mat-misc|" + month + "|k-qty"), getNumber(cells, TOTAL_COST_TABLE, "mat-misc|" + month + "|k-unit"));
            putNumber(cells, TOTAL_COST_TABLE, "mat-misc|" + month + "|a-cost", aCost);
            putNumber(cells, TOTAL_COST_TABLE, "mat-misc|" + month + "|k-cost", kCost);
            putNumber(cells, TOTAL_COST_TABLE, "mat-misc|" + month + "|total", sumNullable(aCost, kCost));
        }
    }

    private ContractMonth collectContractMonth(int year, int month) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, monthKey(year, month)), CONTRACT_TABLE);
        hydrateContractComputedCells(cells);
        double unit1Steam = 0;
        double unit2Steam = 0;
        double unit1Oper = 0;
        double unit2Oper = 0;
        double unit1Stop = 0;
        double unit2Stop = 0;
        double unit1Runtime = 0;
        double unit2Runtime = 0;
        for (int day = 1; day <= 31; day += 1) {
            unit1Steam += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit1|" + day + "|steam"));
            unit2Steam += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit2|" + day + "|steam"));
            unit1Oper += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit1|" + day + "|oper"));
            unit2Oper += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit2|" + day + "|oper"));
            unit1Stop += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit1|" + day + "|stop"));
            unit2Stop += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit2|" + day + "|stop"));
            unit1Runtime += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit1|" + day + "|runtime"));
            unit2Runtime += numberOrZero(getNumber(cells, CONTRACT_TABLE, "unit2|" + day + "|runtime"));
        }
        return new ContractMonth(unit1Steam, unit2Steam, unit1Oper, unit2Oper, unit1Stop, unit2Stop, unit1Runtime, unit2Runtime);
    }

    private Double collectOperationActual(int year, int month, String unit) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)), OPERATION_TABLE);
        return getNumber(cells, OPERATION_TABLE, "hours|" + month + "|" + unit + "-actual");
    }

    private record ContractMonth(
            double unit1Steam,
            double unit2Steam,
            double unit1Oper,
            double unit2Oper,
            double unit1Stop,
            double unit2Stop,
            double unit1Runtime,
            double unit2Runtime
    ) {}

    /**
     * 사고이력 — 원본("No. / 일 자 / 사 유 / …")과 우리 내려받기 양식("ID / 일자 / 사유 / …") 모두
     * 같은 머리글 기반 읽기로 처리한다({@link #importAccidentSheetByPosition}).
     */
    private int importAccidentSheet(Sheet sheet, int year) {
        return importAccidentSheetByPosition(sheet, year);
    }

    /**
     * 사고이력 — 원본("No. / 일 자 / 사 유 / …")과 우리 내려받기 양식("ID / 일자 / 사유 / …") 모두
     * {@link #accidentBlocks} 로 찾은 표에서 읽는다. 정비 표에는 "손실금액" 칸이 없다.
     */
    private int importAccidentSheetByPosition(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = year + "-00";
        java.util.List<AccidentBlock> blocks = accidentBlocks(sheet);
        String[] prefixes = {ACCIDENT_PREFIX, MAINTENANCE_PREFIX};
        for (int b = 0; b < blocks.size() && b < prefixes.length; b += 1) {
            AccidentBlock block = blocks.get(b);
            for (int r = block.firstRow(); r <= block.lastRow(); r += 1) {
                imported += importAccidentRecordRow(monthKey, year, sheet.getRow(r), prefixes[b], block.cols());
            }
        }
        return imported;
    }

    /** 사고·정비 표 머리글이면 항목 → 열, 아니면 null. */
    static Map<String, Integer> accidentColumns(Sheet sheet, int row) {
        Integer date = SheetLabels.column(sheet, row, "일 자", 0);
        Integer action = SheetLabels.column(sheet, row, "조치내역", 0);
        if (date == null || action == null) return null;
        Map<String, Integer> cols = new LinkedHashMap<>();
        Integer no = SheetLabels.column(sheet, row, "No.", 0);
        putIfFound(cols, "no", no != null ? no : SheetLabels.column(sheet, row, "ID", 0));   // 우리 양식은 "ID"
        cols.put("date", date);
        putIfFound(cols, "reason", SheetLabels.column(sheet, row, "사 유", 0));
        cols.put("action", action);
        putIfFound(cols, "downtime", SheetLabels.columnStartingWith(sheet, row, "운휴시간", 0));
        putIfFound(cols, "loss", SheetLabels.columnStartingWith(sheet, row, "손실금액", 0));
        putIfFound(cols, "note", SheetLabels.column(sheet, row, "비 고", 0));
        return cols;
    }

    private int importAccidentRecordRow(String monthKey, int year, Row row, String prefix, Map<String, Integer> cols) {
        if (row == null) return 0;
        String no = cellAt(row, cols, "no");
        if (no == null || no.isBlank()) return 0;
        String rowId = no.replaceAll("\\.0$", "").trim();
        if (rowId.isBlank()) return 0;
        boolean hasLoss = cols.containsKey("loss");
        String rowKey = prefix + rowId;
        String date = cols.containsKey("date") ? readDateCellValue(row.getCell(cols.get("date"))) : null;
        String reason = cellAt(row, cols, "reason");
        String action = cellAt(row, cols, "action");
        String downtime = cellAt(row, cols, "downtime");
        String loss = cellAt(row, cols, "loss");
        String note = cellAt(row, cols, "note");
        if (isAllBlank(date, reason, action, downtime, loss, note)) return 0;
        int imported = 0;
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 1, date);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 2, reason);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 3, action);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 4, downtime);
        if (hasLoss) imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 5, loss);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 6, note);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 7, rowId);
        return imported;
    }

    private String cellAt(Row row, Map<String, Integer> cols, String key) {
        Integer col = cols.get(key);
        return col == null ? null : readCellValue(row.getCell(col));
    }

    // =============================================================
    // Helpers
    // =============================================================

    private String mapPlanGroup(String label) {
        if (label == null) return null;
        return switch (label.trim()) {
            case "인건비" -> "labor";
            case "운영비" -> "operation";
            case "합계" -> "total";
            default -> null;
        };
    }

    private String mapPlanKind(String label) {
        if (label == null) return null;
        return switch (label.trim()) {
            case "계획" -> "plan";
            case "실적" -> "actual";
            case "달성율(%)" -> "rate";
            case "증감" -> "diff";
            default -> null;
        };
    }

    private String mapBreakField(String label) {
        if (label == null) return null;
        return switch (label.trim()) {
            case "월 계획" -> "m-plan";
            case "월 실적" -> "m-actual";
            case "누계 계획" -> "y-plan";
            case "누계 실적" -> "y-actual";
            case "전월 실적" -> "prev-actual";
            case "비고" -> "note";
            default -> null;
        };
    }

    private int parseMonth(String text) {
        java.util.regex.Matcher mat = java.util.regex.Pattern.compile("(\\d{1,2})월").matcher(text);
        return mat.find() ? Integer.parseInt(mat.group(1)) : 0;
    }

    private int parseMonthLabel(String text) {
        if (text == null) return 0;
        java.util.regex.Matcher mat = java.util.regex.Pattern.compile("^(\\d{1,2})월$").matcher(text.trim());
        return mat.matches() ? Integer.parseInt(mat.group(1)) : 0;
    }

    private int parseDay(String text) {
        if (text == null) return 0;
        java.util.regex.Matcher mat = java.util.regex.Pattern.compile("^(\\d{1,2})일$").matcher(text.trim());
        return mat.matches() ? Integer.parseInt(mat.group(1)) : 0;
    }

    private boolean isAllBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    private Object getCellObject(Map<String, Object> cells, String tableName, String sourceRowKey) {
        return cells.get(rowKey(tableName, monthKeyMarker(), sourceRowKey, 1));
    }

    private Double getNumber(Map<String, Object> cells, String tableName, String sourceRowKey) {
        return parseNumber(getCellObject(cells, tableName, sourceRowKey));
    }

    private Double parseNumber(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).replace(",", "").replace("%", "").trim();
        if (text.isEmpty()) return null;
        try {
            double parsed = Double.parseDouble(text);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void putNumber(Map<String, Object> cells, String tableName, String sourceRowKey, Double value) {
        if (value == null || !Double.isFinite(value)) return;
        cells.put(rowKey(tableName, monthKeyMarker(), sourceRowKey, 1), numberString(value));
    }

    private Double multiply(Double left, Double right) {
        if (left == null || right == null) return null;
        return left * right;
    }

    private Double divide(Double value, double divisor) {
        if (value == null || divisor == 0) return null;
        return value / divisor;
    }

    private Double sumNullable(Double left, Double right) {
        if (left == null && right == null) return null;
        return (left == null ? 0.0 : left) + (right == null ? 0.0 : right);
    }

    private double numberOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private Double nonZero(double value) {
        return value == 0.0 ? null : value;
    }

    private Map<String, Object> buildCellMap(List<Map<String, Object>> rows, String tableName) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (!tableName.equals(String.valueOf(row.get("table_name")))) continue;
            mapped.put(
                    rowKey(tableName, monthKeyMarker(), String.valueOf(row.get("row_key")), toInt(row.get("col_index"))),
                    row.get("cell_value")
            );
        }
        return mapped;
    }

    private String rowKey(String tableName, String month, String rowKey, int colIndex) {
        return tableName + "|" + month + "|" + rowKey + "|" + colIndex;
    }

    private int upsertCell(String month, int year, int monthNo, String tableName, String rowKey, int colIndex, String value) {
        if (value == null || value.isBlank()) return 0;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", month);
        payload.put("year_no", year);
        payload.put("month_no", monthNo);
        payload.put("table_name", tableName);
        payload.put("row_key", rowKey);
        payload.put("col_index", colIndex);
        payload.put("cell_value", value);
        tableService.upsert(CELL_TABLE, payload);
        return 1;
    }

    private int upsertCellIfPresent(String month, int year, int monthNo, String tableName, String rowKey, int colIndex, Row row, int sourceCol) {
        if (row == null) return 0;
        String value = readCellValue(row.getCell(sourceCol));
        return upsertCell(month, year, monthNo, tableName, rowKey, colIndex, value);
    }

    private void setString(Sheet sheet, int rowIndex, int colIndex, String value) {
        Cell cell = getOrCreateCell(sheet, rowIndex, colIndex);
        cell.setCellValue(value == null ? "" : value);
    }

    private void setNumericOrText(Sheet sheet, int rowIndex, int colIndex, Object value) {
        if (value == null) return;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return;
        Cell cell = getOrCreateCell(sheet, rowIndex, colIndex);
        String cleaned = text.replace(",", "");
        try {
            cell.setCellValue(new BigDecimal(cleaned).doubleValue());
        } catch (NumberFormatException e) {
            cell.setCellValue(text);
        }
    }

    private Cell getOrCreateCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        return cell;
    }

    private void styleRow(Sheet sheet, int rowIndex, int fromCol, int toCol, CellStyle style) {
        for (int c = fromCol; c <= toCol; c += 1) {
            getOrCreateCell(sheet, rowIndex, c).setCellStyle(style);
        }
    }

    private String readCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA) {
            return switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> numberString(cell.getNumericCellValue());
                case STRING -> {
                    String s = cell.getStringCellValue();
                    yield s == null ? "" : s.trim();
                }
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                default -> "";
            };
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDate date = DateUtil.getJavaDate(cell.getNumericCellValue())
                        .toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                return date.toString();
            }
            return numberString(cell.getNumericCellValue());
        }
        String text = dataFormatter.formatCellValue(cell);
        return text == null ? "" : text.trim();
    }

    private String readDateCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell) || (v >= 30000 && v <= 60000)) {
                LocalDate date = DateUtil.getJavaDate(v).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                return date.toString();
            }
        }
        return readCellValue(cell);
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value).trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String numberString(double value) {
        if (!Double.isFinite(value)) return "";
        BigDecimal bd = BigDecimal.valueOf(value);
        if (bd.scale() > 10) bd = bd.setScale(10, RoundingMode.HALF_UP);
        bd = bd.stripTrailingZeros();
        if (bd.scale() < 0) bd = bd.setScale(0);
        return bd.toPlainString();
    }

    private String monthKey(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }
}
