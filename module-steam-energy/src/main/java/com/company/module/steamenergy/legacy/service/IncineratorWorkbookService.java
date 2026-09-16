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

    /** Sheet 1 도급내역 — rows 7~37 (1-indexed) = day 1~31. 컬럼은 원본 엑셀과 동일. */
    private void fillContractTemplate(Sheet sheet, int year, int month) {
        String monthKey = monthKey(year, month);
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, monthKey), CONTRACT_TABLE);
        // 제목 B1 업데이트
        setTemplateText(sheet, 0, 1, "1. " + year + "년 " + month + "월 소각로 도급비용");
        for (int day = 1; day <= 31; day += 1) {
            int row = 5 + day; // POI 0-indexed: row 6 = day 1
            // unit1: cols C(2) steam, D(3) unit, F(5) runtime, G(6) fixed
            setTemplateValue(sheet, row, 2, contractVal(cells, "unit1", day, "steam"));
            setTemplateValue(sheet, row, 3, contractVal(cells, "unit1", day, "unit"));
            setTemplateValue(sheet, row, 5, contractVal(cells, "unit1", day, "runtime"));
            setTemplateValue(sheet, row, 6, contractVal(cells, "unit1", day, "fixed"));
            // unit2: cols J(9) steam, K(10) unit, M(12) runtime, N(13) fixed
            setTemplateValue(sheet, row, 9, contractVal(cells, "unit2", day, "steam"));
            setTemplateValue(sheet, row, 10, contractVal(cells, "unit2", day, "unit"));
            setTemplateValue(sheet, row, 12, contractVal(cells, "unit2", day, "runtime"));
            setTemplateValue(sheet, row, 13, contractVal(cells, "unit2", day, "fixed"));
            // real: cols U(20) steam-1, V(21) unit-1, X(23) steam-2, Y(24) unit-2
            setTemplateValue(sheet, row, 20, contractVal(cells, "real", day, "steam-1"));
            setTemplateValue(sheet, row, 21, contractVal(cells, "real", day, "unit-1"));
            setTemplateValue(sheet, row, 23, contractVal(cells, "real", day, "steam-2"));
            setTemplateValue(sheet, row, 24, contractVal(cells, "real", day, "unit-2"));
        }
        // 인건비 (J43) — summary|0|labor
        Object labor = cells.get(rowKey(CONTRACT_TABLE, monthKeyMarker(), "summary|0|labor", 1));
        if (labor != null) setTemplateValue(sheet, 42, 9, labor);  // J43 = row 42, col 9
    }

    private Object contractVal(Map<String, Object> cells, String section, int day, String field) {
        return cells.get(rowKey(CONTRACT_TABLE, monthKeyMarker(), section + "|" + day + "|" + field, 1));
    }

    /** Sheet 2 사업계획대비 — 본표는 rows 5~10, 비고는 rows 18~41 */
    private void fillPlanVsTemplate(Sheet sheet, int year, int month) {
        Map<String, String> values = collectPlanVsValues(year, month);
        hydratePlanVsComputedValues(values, year);
        // 본표 — POI 0-indexed: row 4=labor.plan, row 5=labor.actual, row 6=op.plan, row 7=op.actual, row 8=total.plan, row 9=total.actual
        // 각 row 의 month 컬럼: 0-indexed col 2 = m1, ..., col 13 = m12. 합계는 col 14.
        String[][] planRows = {
                {"labor", "plan"}, {"labor", "actual"},
                {"operation", "plan"}, {"operation", "actual"},
                {"total", "plan"}, {"total", "actual"},
        };
        for (int i = 0; i < planRows.length; i += 1) {
            int rowIdx = 4 + i;
            String section = planRows[i][0];
            String kind = planRows[i][1];
            for (int m = 1; m <= 12; m += 1) {
                String v = values.get(section + "|" + kind + "-m" + m);
                setTemplateValue(sheet, rowIdx, 1 + m, v);
            }
        }
        // 구분(break) — POI row 15 (Excel row 16)
        setTemplateValue(sheet, 15, 1, values.get("break|m-plan"));
        setTemplateValue(sheet, 15, 2, values.get("break|m-actual"));
        setTemplateValue(sheet, 15, 4, values.get("break|y-plan"));
        setTemplateValue(sheet, 15, 5, values.get("break|y-actual"));
        setTemplateValue(sheet, 15, 7, values.get("break|prev-actual"));
        setTemplateText (sheet, 15, 10, values.get("break|note"));
        // 비고 — POI rows 17..40 (Excel rows 18..41), 12 month × 2 unit
        int rowIdx = 17;
        for (int m = 1; m <= 12; m += 1) {
            for (String unit : new String[]{"u1", "u2"}) {
                String v = values.get("notes|m" + m + "-" + unit);
                setTemplateText(sheet, rowIdx, 4, v);  // col E (4) — 비고 텍스트 위치
                rowIdx += 1;
            }
        }
    }

    /** Sheet 3 전년대비 — row 4 (prev), row 5 (curr), row 6 (diff) for cost; rows 11/12/13 for ton */
    private void fillYoyTemplate(Sheet sheet, int year) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)), YOY_TABLE);
        hydrateYoyComputedCells(cells, year);
        // cost section
        for (int m = 1; m <= 12; m += 1) {
            setTemplateValue(sheet, 3, 1 + m, cells.get(rowKey(YOY_TABLE, monthKeyMarker(), "cost|prev-m" + m, 1)));
            setTemplateValue(sheet, 4, 1 + m, cells.get(rowKey(YOY_TABLE, monthKeyMarker(), "cost|curr-m" + m, 1)));
        }
        // ton section
        for (int m = 1; m <= 12; m += 1) {
            setTemplateValue(sheet, 10, 1 + m, cells.get(rowKey(YOY_TABLE, monthKeyMarker(), "ton|prev-m" + m, 1)));
            setTemplateValue(sheet, 11, 1 + m, cells.get(rowKey(YOY_TABLE, monthKeyMarker(), "ton|curr-m" + m, 1)));
        }
    }

    /** Sheet 4 계획대비 가동실적 — rows 5~16 (1-indexed) = m1~m12 */
    private void fillOperationTemplate(Sheet sheet, int year) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)), OPERATION_TABLE);
        hydrateOperationComputedCells(cells);
        // POI 0-indexed: row 4 = m1, row 15 = m12
        // hours: cols T(19), U(20), W(22), X(23) for u1-plan, u1-actual, u2-plan, u2-actual
        for (int m = 1; m <= 12; m += 1) {
            int rowIdx = 3 + m;
            setTemplateValue(sheet, rowIdx, 19, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "hours|" + m + "|u1-plan", 1)));
            setTemplateValue(sheet, rowIdx, 20, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "hours|" + m + "|u1-actual", 1)));
            setTemplateValue(sheet, rowIdx, 22, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "hours|" + m + "|u2-plan", 1)));
            setTemplateValue(sheet, rowIdx, 23, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "hours|" + m + "|u2-actual", 1)));
            // metric: steam, rate, cost — Z(25), AA(26), AC(28), AD(29), AF(31), AG(32) — plan/actual columns
            // Excel cols: Z=25 (plan steam), AA=26 (actual steam), AC=28 (plan rate), AD=29 (actual rate), AF=31 (plan cost), AG=32 (actual cost)
            setTemplateValue(sheet, rowIdx, 25, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|steam-plan", 1)));
            setTemplateValue(sheet, rowIdx, 26, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|steam-actual", 1)));
            setTemplateValue(sheet, rowIdx, 28, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|rate-plan", 1)));
            setTemplateValue(sheet, rowIdx, 29, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|rate-actual", 1)));
            setTemplateValue(sheet, rowIdx, 31, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|cost-plan", 1)));
            setTemplateValue(sheet, rowIdx, 32, cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|cost-actual", 1)));
        }
    }

    /** Sheet 5 총비용 — rows 6~17 (1-indexed) = m1~m12. 블록별로 다른 시작 col. */
    private void fillTotalCostTemplate(Sheet sheet, int year) {
        Map<String, Object> cells = buildCellMap(tableService.listAllByMonth(CELL_TABLE, String.valueOf(year)), TOTAL_COST_TABLE);
        hydrateTotalCostComputedCells(cells, year);
        for (int m = 1; m <= 12; m += 1) {
            int rowIdx = 5 + m;
            // op-burn (block 0): cols B(1) ~ N(13). order matches TOTAL_COST_BLOCKS[0].fields
            fillTotalCostBlock(sheet, rowIdx, m, cells, TOTAL_COST_BLOCKS[0], 1);
            // op-cost (block 1): cols O(14) ~ W(22)
            fillTotalCostBlock(sheet, rowIdx, m, cells, TOTAL_COST_BLOCKS[1], 14);
        }
        // 자체운영비용 블록 (rows 26~37 in Excel, 0-indexed 25~36? Need to check actual template structure)
        // 소모품 블록 (rows 49~60 in Excel)
        // 위 블록들은 원본 엑셀에 없거나 위치가 다를 수 있어서 일단 op-burn/op-cost 만 처리.
    }

    private void fillTotalCostBlock(Sheet sheet, int rowIdx, int m, Map<String, Object> cells, TotalCostBlock block, int firstCol) {
        for (int i = 0; i < block.fields.length; i += 1) {
            String key = block.section + "|" + m + "|" + block.fields[i];
            setTemplateValue(sheet, rowIdx, firstCol + i, cells.get(rowKey(TOTAL_COST_TABLE, monthKeyMarker(), key, 1)));
        }
    }

    /** Sheet 6 사고이력 — 행 단위로 추가 */
    private void fillAccidentTemplate(Sheet sheet, int year) {
        String mk = year + "-00";
        List<Map<String, Object>> rows = tableService.listAllByMonth(CELL_TABLE, mk);
        // Sheet 6 의 헤더 위치는 원본 기준 row 3 (0-indexed 2). 데이터는 row 3 이하.
        // 사고/정비를 id 별로 그룹화: row_key prefix:N → 사고 N 번 데이터
        Map<String, Map<Integer, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String rowKey = String.valueOf(row.get("row_key"));
            if (!rowKey.startsWith(ACCIDENT_PREFIX) && !rowKey.startsWith(MAINTENANCE_PREFIX)) continue;
            int colIndex = ((Number) row.getOrDefault("col_index", 0)).intValue();
            grouped.computeIfAbsent(rowKey, k -> new LinkedHashMap<>()).put(colIndex, row.get("cell_value"));
        }
        int dataRowStart = 3;  // POI 0-indexed
        int rIdx = dataRowStart;
        for (Map.Entry<String, Map<Integer, Object>> e : grouped.entrySet()) {
            Map<Integer, Object> cols = e.getValue();
            // 컬럼: 1=일자, 2=사유, 3=조치내역, 4=운휴시간, 5=손실금액(사고만), 6=비고
            setTemplateText (sheet, rIdx, 0, String.valueOf(cols.getOrDefault(1, "")));
            setTemplateText (sheet, rIdx, 1, String.valueOf(cols.getOrDefault(2, "")));
            setTemplateText (sheet, rIdx, 2, String.valueOf(cols.getOrDefault(3, "")));
            setTemplateValue(sheet, rIdx, 3, cols.get(4));
            setTemplateValue(sheet, rIdx, 4, cols.get(5));
            setTemplateText (sheet, rIdx, 5, String.valueOf(cols.getOrDefault(6, "")));
            rIdx += 1;
        }
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
        String[] headers;
        if ("real".equals(section)) {
            headers = new String[]{"일별", "1호기 스팀량", "1호기 단가", "1호기 운영비용", "2호기 스팀량", "2호기 단가", "2호기 운영비용", "스팀량 합계", "총 운영비용"};
        } else {
            headers = new String[]{"일별", "스팀량", "운영단가", "운영비용", "운휴시간", "시간당 고정비", "운휴비용", "소계"};
        }
        for (int c = 0; c < headers.length; c += 1) {
            Cell cell = header.createCell(c);
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
        String[] hoursFields = {"u1-plan", "u1-actual", "u1-rate", "u2-plan", "u2-actual", "u2-rate"};
        String[] hoursLabels = {"1호기 계획", "1호기 실적", "1호기 달성율", "2호기 계획", "2호기 실적", "2호기 달성율"};
        for (int i = 0; i < hoursFields.length; i += 1) setString(sheet, row, i + 1, hoursLabels[i]);
        row += 1;
        for (int m = 1; m <= 12; m += 1) {
            setString(sheet, row, 0, m + "월");
            for (int i = 0; i < hoursFields.length; i += 1) {
                Object v = cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "hours|" + m + "|" + hoursFields[i], 1));
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
        String[] metricHeaders = {
                "스팀량 계획", "스팀량 실적", "스팀량 증감",
                "단가 계획", "단가 실적", "단가 증감",
                "운영비 계획", "운영비 실적", "운영비 증감"
        };
        for (int i = 0; i < metricHeaders.length; i += 1) setString(sheet, row, i + 1, metricHeaders[i]);
        row += 1;
        String[] metrics = {"steam", "rate", "cost"};
        String[] metricKinds = {"plan", "actual", "diff"};
        for (int m = 1; m <= 12; m += 1) {
            setString(sheet, row, 0, m + "월");
            int col = 1;
            for (String metric : metrics) {
                for (String kind : metricKinds) {
                    Object v = cells.get(rowKey(OPERATION_TABLE, monthKeyMarker(), "metric|" + m + "|" + metric + "-" + kind, 1));
                    if (v != null && !String.valueOf(v).isBlank()) setNumericOrText(sheet, row, col, v);
                    col += 1;
                }
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

    private int importContractSheetByLabel(Sheet sheet, int year, int month) {
        // 도급내역 시트는 선택한 월 1개월치 데이터로 간주. 시트의 월 헤더 라벨은 무시하고 사용자가 지정한 month 로 저장.
        int imported = 0;
        String currentSection = null;
        int sectionDataStartRow = -1;
        int lastRow = sheet.getLastRowNum();
        String monthKey = monthKey(year, month);
        for (int r = 0; r <= lastRow; r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String col0 = readCellValue(row.getCell(0));
            if (col0 == null) col0 = "";
            // 월 헤더는 단순 skip
            if (col0.matches(".*\\d{4}년\\s*\\d{1,2}월.*")) { currentSection = null; continue; }
            if ("1호기".equals(col0.trim())) { currentSection = "unit1"; sectionDataStartRow = r + 2; continue; }
            if ("2호기".equals(col0.trim())) { currentSection = "unit2"; sectionDataStartRow = r + 2; continue; }
            if (col0.trim().startsWith("실 스팀량")) { currentSection = "real"; sectionDataStartRow = r + 2; continue; }
            if (currentSection == null) continue;
            if (r < sectionDataStartRow) continue;
            int day = parseDay(col0);
            if (day < 1 || day > 31) continue;
            String[] fields = "real".equals(currentSection) ? REAL_FIELDS : UNIT_FIELDS;
            for (int idx = 0; idx < fields.length; idx += 1) {
                String value = readCellValue(row.getCell(idx + 1));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, CONTRACT_TABLE,
                        currentSection + "|" + day + "|" + fields[idx], 1, value);
            }
        }
        return imported;
    }

    private int importPlanVsSheet(Sheet sheet, int year, int month) {
        // 형식 감지: 우리 export 는 row 2 에 c0="구분" c1="행" 헤더가 있음. 원본 엑셀은 다른 구조.
        if (isOurExportPlanVsFormat(sheet)) {
            return importPlanVsSheetByLabel(sheet, year, month);
        }
        return importPlanVsSheetByPosition(sheet, year, month);
    }

    private boolean isOurExportPlanVsFormat(Sheet sheet) {
        Row r2 = sheet.getRow(2);
        if (r2 == null) return false;
        String c0 = readCellValue(r2.getCell(0));
        String c1 = readCellValue(r2.getCell(1));
        return "구분".equals(c0 == null ? "" : c0.trim()) && "행".equals(c1 == null ? "" : c1.trim());
    }

    private int importPlanVsSheetByLabel(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int lastRow = sheet.getLastRowNum();
        String currentBlock = null;
        // 본 표 헤더 행 찾기: col0="구분", col1="행"
        // plan groups rows: (groupLabel, kind)
        for (int r = 0; r <= lastRow; r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String c0 = readCellValue(row.getCell(0));
            String c1 = readCellValue(row.getCell(1));
            if (c0 == null) c0 = "";
            if (c1 == null) c1 = "";

            if (c0.equals("구분") && c1.equals("행")) { currentBlock = "plan"; continue; }
            if (c0.startsWith("구분 (백만원)")) { currentBlock = "break"; continue; }
            // 우리 export 형식: c0="비고", c1="호기"
            if (c0.equals("비고") && c1.equals("호기")) { currentBlock = "notes"; continue; }
            // 원본 엑셀 형식: c0 가 공백 포함 "비   고" (whitespace normalize 후 "비고")
            String c0Norm = c0.replaceAll("\\s+", "");
            if (c0Norm.equals("비고") && !c1.equals("호기")) {
                currentBlock = "notes";
                // c1 에 month label(예: "1월") 이 있으면 이 행도 데이터 행으로 fall-through 처리
                if (parseMonthLabel(c1) >= 1) {
                    // skip continue 하여 같은 행을 notes 블록 데이터 처리에 넘김
                } else {
                    continue;
                }
            }
            if ("plan".equals(currentBlock)) {
                String section = mapPlanGroup(c0);
                String kind = mapPlanKind(c1);
                if (section == null || kind == null) continue;
                // 자동계산 셀은 import 하지 않는다 (rate, diff; operation.actual, total.actual)
                if ("rate".equals(kind) || "diff".equals(kind)) continue;
                if (("operation".equals(section) || "total".equals(section)) && "actual".equals(kind)) continue;
                for (int m = 1; m <= 12; m += 1) {
                    String v = readCellValue(row.getCell(1 + m));
                    if (v == null || v.isBlank()) continue;
                    imported += upsertCell(monthKey, year, month, PLAN_VS_TABLE, section + "|" + kind + "-m" + m, 1, v);
                }
            } else if ("break".equals(currentBlock)) {
                String field = mapBreakField(c0);
                if (field == null) continue;
                // 자동 계산 셀 skip
                if (field.endsWith("-diff") || "prev-rate".equals(field)) continue;
                String v = readCellValue(row.getCell(1));
                if (v == null || v.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, PLAN_VS_TABLE, "break|" + field, 1, v);
            } else if ("notes".equals(currentBlock)) {
                // month label: c0(우리 export) 또는 c1(원본 엑셀) 위치에서 시도
                int m = parseMonthLabel(c0);
                if (m < 1) m = parseMonthLabel(c1);
                // unit label: 우리 export 는 c1, 원본 엑셀은 c2 위치
                String unit = null;
                if (c1.contains("#1")) unit = "u1";
                else if (c1.contains("#2")) unit = "u2";
                else {
                    String c2 = readCellValue(row.getCell(2));
                    if (c2 != null && c2.contains("#1")) unit = "u1";
                    else if (c2 != null && c2.contains("#2")) unit = "u2";
                }
                if (unit == null) continue;
                if (m < 1 || m > 12) continue;
                String v = readNoteValue(row);
                if (v == null || v.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, PLAN_VS_TABLE, "notes|m" + m + "-" + unit, 1, v);
            }
        }
        return imported;
    }

    private int importYoySheet(Sheet sheet, int year) {
        int labeled = importYoySheetByLabel(sheet, year);
        if (labeled > 0) return labeled;
        return importYoySheetByPosition(sheet, year);
    }

    private int importYoySheetByLabel(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = String.valueOf(year);
        int lastRow = sheet.getLastRowNum();
        String currentForm = null;
        for (int r = 0; r <= lastRow; r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String c0 = readCellValue(row.getCell(0));
            if (c0 == null) c0 = "";
            if (c0.contains("폐합성소각로 합계")) { currentForm = "cost"; continue; }
            if (c0.contains("스팀생산량")) { currentForm = "ton"; continue; }
            if (currentForm == null) continue;
            // row label
            String rk;
            if (c0.contains((year - 1) + " 실적")) rk = "prev";
            else if (c0.contains(year + " 실적")) rk = "curr";
            else if ("증감".equals(c0.trim())) rk = "diff";
            else continue;
            // 자동 계산: curr, diff
            if (!"prev".equals(rk)) continue;
            for (int m = 1; m <= 12; m += 1) {
                String v = readCellValue(row.getCell(m));
                if (v == null || v.isBlank()) continue;
                imported += upsertCell(monthKey, year, 0, YOY_TABLE, currentForm + "|" + rk + "-m" + m, 1, v);
            }
        }
        return imported;
    }

    private int importOperationSheet(Sheet sheet, int year) {
        int labeled = importOperationSheetByLabel(sheet, year);
        if (labeled > 0) return labeled;
        return importOperationSheetByPosition(sheet, year);
    }

    private int importOperationSheetByLabel(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = String.valueOf(year);
        int lastRow = sheet.getLastRowNum();
        String currentSection = null;
        String[] hoursFields = {"u1-plan", "u1-actual", "u1-rate", "u2-plan", "u2-actual", "u2-rate"};
        for (int r = 0; r <= lastRow; r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String c0 = readCellValue(row.getCell(0));
            if (c0 == null) c0 = "";
            if (c0.contains("가동시간")) { currentSection = "hours"; continue; }
            if (c0.contains("스팀생산량")) { currentSection = "metric"; continue; }
            if (currentSection == null) continue;
            int m = parseMonthLabel(c0);
            if (m < 1 || m > 12) continue;
            if ("hours".equals(currentSection)) {
                for (int i = 0; i < hoursFields.length; i += 1) {
                    if (hoursFields[i].endsWith("-rate")) continue;
                    String v = readCellValue(row.getCell(i + 1));
                    if (v == null || v.isBlank()) continue;
                    imported += upsertCell(monthKey, year, m, OPERATION_TABLE, "hours|" + m + "|" + hoursFields[i], 1, v);
                }
            } else {
                String[] metrics = {"steam", "rate", "cost"};
                String[] kinds = {"plan", "actual", "diff"};
                int col = 1;
                for (String metric : metrics) {
                    for (String kind : kinds) {
                        if ("diff".equals(kind)) { col += 1; continue; }
                        String v = readCellValue(row.getCell(col));
                        col += 1;
                        if (v == null || v.isBlank()) continue;
                        imported += upsertCell(monthKey, year, m, OPERATION_TABLE,
                                "metric|" + m + "|" + metric + "-" + kind, 1, v);
                    }
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

    private int importTotalCostSheetByLabel(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = String.valueOf(year);
        int lastRow = sheet.getLastRowNum();
        TotalCostBlock currentBlock = null;
        boolean headerSeen = false;
        for (int r = 0; r <= lastRow; r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String c0 = readCellValue(row.getCell(0));
            if (c0 == null) c0 = "";
            // header label?
            TotalCostBlock matched = null;
            for (TotalCostBlock b : TOTAL_COST_BLOCKS) {
                if (c0.equals(b.label)) { matched = b; break; }
            }
            if (matched != null) { currentBlock = matched; headerSeen = false; continue; }
            if (currentBlock == null) continue;
            if ("월".equals(c0.trim()) && !headerSeen) { headerSeen = true; continue; }
            if (!headerSeen) continue;
            int m = parseMonthLabel(c0);
            if (m < 1 || m > 12) continue;
            // 자동계산 필드 set — block-specific
            for (int i = 0; i < currentBlock.fields.length; i += 1) {
                if (isAutoTotalCostField(currentBlock.section, currentBlock.fields[i])) continue;
                String v = readCellValue(row.getCell(i + 1));
                if (v == null || v.isBlank()) continue;
                imported += upsertCell(monthKey, year, m, TOTAL_COST_TABLE,
                        currentBlock.section + "|" + m + "|" + currentBlock.fields[i], 1, v);
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
        imported += importContractSectionByPosition(sheet, mk, year, month, 6, "unit1", UNIT_FIELDS, 2);
        imported += importContractSectionByPosition(sheet, mk, year, month, 6, "unit2", UNIT_FIELDS, 9);
        imported += importContractSectionByPosition(sheet, mk, year, month, 6, "real", REAL_FIELDS, 20);
        // 월합계 표 인건비: 원본 '1. 도급내역' J42 (0-indexed row 42, col 9) → summary|0|labor.
        //  (도급내역 페이지 월합계 표의 운영비 = 총금액 − 인건비 계산 입력. export J43 도 이 값을 사용)
        Row laborRow = sheet.getRow(42);
        if (laborRow != null) {
            String labor = readCellValue(laborRow.getCell(9));
            if (labor != null && !labor.isBlank()) {
                imported += upsertCell(mk, year, month, CONTRACT_TABLE, "summary|0|labor", 1, labor);
            }
        }
        return imported;
    }

    private int importContractSectionByPosition(Sheet sheet, String monthKey, int year, int month, int firstDataRow, String section, String[] fields, int firstValueCol) {
        int imported = 0;
        for (int day = 1; day <= 31; day += 1) {
            Row row = sheet.getRow(firstDataRow + day - 1);
            if (row == null) continue;
            for (int idx = 0; idx < fields.length; idx += 1) {
                String value = readCellValue(row.getCell(firstValueCol + idx));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(monthKey, year, month, CONTRACT_TABLE, section + "|" + day + "|" + fields[idx], 1, value);
            }
        }
        return imported;
    }

    private int importPlanVsSheetByPosition(Sheet sheet, int year, int month) {
        String mk = monthKey(year, month);
        int imported = 0;
        String[][] planRows = {
                {"labor", "plan"},
                {"labor", "actual"},
                {"operation", "plan"},
                {"operation", "actual"},
                {"total", "plan"},
                {"total", "actual"},
                {"total", "rate"},
                {"total", "diff"},
        };
        for (int i = 0; i < planRows.length; i += 1) {
            String section = planRows[i][0];
            String kind = planRows[i][1];
            if ("rate".equals(kind) || "diff".equals(kind)) continue;
            Row row = sheet.getRow(4 + i);
            if (row == null) continue;
            for (int m = 1; m <= 12; m += 1) {
                String value = readCellValue(row.getCell(1 + m));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, month, PLAN_VS_TABLE, section + "|" + kind + "-m" + m, 1, value);
            }
        }

        Row breakRow = sheet.getRow(15);
        if (breakRow != null) {
            imported += upsertCellIfPresent(mk, year, month, PLAN_VS_TABLE, "break|m-plan", 1, breakRow, 1);
            imported += upsertCellIfPresent(mk, year, month, PLAN_VS_TABLE, "break|m-actual", 1, breakRow, 2);
            imported += upsertCellIfPresent(mk, year, month, PLAN_VS_TABLE, "break|y-plan", 1, breakRow, 4);
            imported += upsertCellIfPresent(mk, year, month, PLAN_VS_TABLE, "break|y-actual", 1, breakRow, 5);
            imported += upsertCellIfPresent(mk, year, month, PLAN_VS_TABLE, "break|prev-actual", 1, breakRow, 7);
            imported += upsertCellIfPresent(mk, year, month, PLAN_VS_TABLE, "break|note", 1, breakRow, 10);
        }

        int rowIndex = 17;
        for (int m = 1; m <= 12; m += 1) {
            for (String unit : new String[]{"u1", "u2"}) {
                Row row = sheet.getRow(rowIndex++);
                if (row == null) continue;
                String value = readNoteValue(row);
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, month, PLAN_VS_TABLE, "notes|m" + m + "-" + unit, 1, value);
            }
        }
        return imported;
    }

    /** 비고 셀 값 읽기 — 원본 엑셀(col 4=E + col 6=G) 우선, 없으면 우리 export(col 2)
     *  col 2 가 "#1 폐합성수지소각로" 같은 라벨이면 skip. */
    private String readNoteValue(Row row) {
        if (row == null) return "";
        String c4 = readCellValue(row.getCell(4));
        String c6 = readCellValue(row.getCell(6));
        StringBuilder sb = new StringBuilder();
        if (c4 != null && !c4.isBlank()) sb.append(c4.trim());
        if (c6 != null && !c6.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(c6.trim());
        }
        if (sb.length() > 0) return sb.toString();
        String c2 = readCellValue(row.getCell(2));
        if (c2 == null) return "";
        String c2t = c2.trim();
        if (c2t.contains("#1 폐합성수지소각로") || c2t.contains("#2 폐합성수지소각로")) return "";
        return c2t;
    }

    private int importYoySheetByPosition(Sheet sheet, int year) {
        String mk = String.valueOf(year);
        int imported = 0;
        imported += importYoyRowByPosition(sheet, mk, year, 3, "cost|prev");
        imported += importYoyRowByPosition(sheet, mk, year, 10, "ton|prev");
        return imported;
    }

    private int importYoyRowByPosition(Sheet sheet, String monthKey, int year, int rowIndex, String prefix) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) return 0;
        int imported = 0;
        for (int m = 1; m <= 12; m += 1) {
            String value = readCellValue(row.getCell(m + 1));
            if (value == null || value.isBlank()) continue;
            imported += upsertCell(monthKey, year, 0, YOY_TABLE, prefix + "-m" + m, 1, value);
        }
        return imported;
    }

    private int importOperationSheetByPosition(Sheet sheet, int year) {
        String mk = String.valueOf(year);
        int imported = 0;
        String[] hoursFields = {"u1-plan", "u1-actual", "u2-plan", "u2-actual"};
        int[] hoursCols = {19, 20, 22, 23};
        for (int m = 1; m <= 12; m += 1) {
            Row row = sheet.getRow(3 + m);
            if (row == null) continue;
            for (int i = 0; i < hoursFields.length; i += 1) {
                String value = readCellValue(row.getCell(hoursCols[i]));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, m, OPERATION_TABLE, "hours|" + m + "|" + hoursFields[i], 1, value);
            }
        }

        String[] metricFields = {"steam-plan", "steam-actual", "rate-plan", "rate-actual", "cost-plan", "cost-actual"};
        int[] metricCols = {25, 26, 28, 29, 31, 32};
        for (int m = 1; m <= 12; m += 1) {
            Row row = sheet.getRow(3 + m);
            if (row == null) continue;
            for (int i = 0; i < metricFields.length; i += 1) {
                String value = readCellValue(row.getCell(metricCols[i]));
                if (value == null || value.isBlank()) continue;
                imported += upsertCell(mk, year, m, OPERATION_TABLE, "metric|" + m + "|" + metricFields[i], 1, value);
            }
        }
        return imported;
    }

    private int importTotalCostSheetByPosition(Sheet sheet, int year) {
        String mk = String.valueOf(year);
        int imported = 0;
        for (int m = 1; m <= 12; m += 1) {
            Row row = sheet.getRow(4 + m);
            if (row == null) continue;
            imported += importTotalCostRow(mk, year, m, row, "op-burn", TOTAL_COST_BLOCKS[0].fields, 1);
            imported += importTotalCostRow(mk, year, m, row, "op-cost", TOTAL_COST_BLOCKS[1].fields, 14);
            // 주의: 과거 코드에서 총비용 시트의 월별 합계를 contract 의 day-1 값으로 저장하던 로직이 있었으나,
            // 도급내역 페이지가 "1일에 데이터가 들어가" 보이는 부작용이 있어 제거함.
            // contract 데이터는 도급내역 시트(혹은 페이지에서 직접 일별 입력)로만 채워짐.
        }
        for (int m = 1; m <= 12; m += 1) {
            Row row = sheet.getRow(26 + m - 1);
            if (row == null) continue;
            imported += importTotalCostRow(mk, year, m, row, "self-srf", TOTAL_COST_BLOCKS[2].fields, 1);
            // self-util 은 Excel 에서 비연속 컬럼: N(13)1호기,O(14)2호기,[P(15)합계 auto],Q(16)전력단가,
            //  R(17)비용소계,S(18)가성소다구입량,T(19)단가,U(20)구입비용,V(21)용수,W(22)폐기물부담금,X(23)기타.
            //  (기존 firstCol=13 순차 매핑이 P/R auto 열 때문에 전력단가·가성소다 데이터를 한 칸씩 밀어 읽던 버그 교정)
            imported += importTotalCostRow(mk, year, m, row, "self-util", TOTAL_COST_BLOCKS[3].fields,
                    new int[]{13, 14, 16, 17, 18, 19, 20, 21, 22, 23});
            imported += importTotalCostRow(mk, year, m, row, "self-total", TOTAL_COST_BLOCKS[4].fields, 25);
        }
        for (int m = 1; m <= 12; m += 1) {
            Row row = sheet.getRow(49 + m - 1);
            if (row == null) continue;
            imported += importTotalCostRow(mk, year, m, row, "mat-oil", TOTAL_COST_BLOCKS[5].fields, 1);
            imported += importTotalCostRow(mk, year, m, row, "mat-chem", TOTAL_COST_BLOCKS[6].fields, 9);
            imported += importTotalCostRow(mk, year, m, row, "mat-misc", TOTAL_COST_BLOCKS[7].fields, 15);
        }
        return imported;
    }

    private int importTotalCostRow(String monthKey, int year, int month, Row row, String section, String[] fields, int firstCol) {
        int imported = 0;
        for (int i = 0; i < fields.length; i += 1) {
            String value = readCellValue(row.getCell(firstCol + i));
            if (value == null || value.isBlank()) continue;
            imported += upsertCell(monthKey, year, month, TOTAL_COST_TABLE, section + "|" + month + "|" + fields[i], 1, value);
        }
        return imported;
    }

    // 비연속 컬럼 매핑용 오버로드 (self-util 처럼 중간에 auto 계산 열이 끼어 있는 블록).
    private int importTotalCostRow(String monthKey, int year, int month, Row row, String section, String[] fields, int[] cols) {
        int imported = 0;
        for (int i = 0; i < fields.length && i < cols.length; i += 1) {
            String value = readCellValue(row.getCell(cols[i]));
            if (value == null || value.isBlank()) continue;
            imported += upsertCell(monthKey, year, month, TOTAL_COST_TABLE, section + "|" + month + "|" + fields[i], 1, value);
        }
        return imported;
    }

    private int importContractMonthSummaryFromTotalCost(int year, int month, Row row) {
        String mk = monthKey(year, month);
        int imported = 0;
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit1|1|steam", 1, row, 8);
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit1|1|runtime", 1, row, 6);
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit1|1|oper", 1, row, 16);
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit1|1|stop", 1, row, 15);
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit2|1|steam", 1, row, 9);
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit2|1|runtime", 1, row, 7);
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit2|1|oper", 1, row, 19);
        imported += upsertCellIfPresent(mk, year, month, CONTRACT_TABLE, "unit2|1|stop", 1, row, 18);
        return imported;
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

    private int importAccidentSheet(Sheet sheet, int year) {
        int positioned = importAccidentSheetByPosition(sheet, year);
        if (positioned > 0) return positioned;

        int imported = 0;
        String monthKey = year + "-00";
        int lastRow = sheet.getLastRowNum();
        String currentKind = null; // accident or maintenance
        boolean headerSeen = false;
        for (int r = 0; r <= lastRow; r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String c0 = readCellValue(row.getCell(0));
            if (c0 == null) c0 = "";
            if ("사고이력".equals(c0.trim())) { currentKind = "accident"; headerSeen = false; continue; }
            if ("정비이력".equals(c0.trim())) { currentKind = "maintenance"; headerSeen = false; continue; }
            if (currentKind == null) continue;
            if ("ID".equals(c0.trim())) { headerSeen = true; continue; }
            if (!headerSeen) continue;
            if (c0.isBlank()) continue;
            String prefix = "accident".equals(currentKind) ? ACCIDENT_PREFIX : MAINTENANCE_PREFIX;
            String rowKey = prefix + c0.trim();
            String date = readDateCellValue(row.getCell(1));
            String reason = readCellValue(row.getCell(2));
            String action = readCellValue(row.getCell(3));
            String downtime = readCellValue(row.getCell(4));
            String lossOrNote = readCellValue(row.getCell(5));
            String noteForAcc = readCellValue(row.getCell(6));
            if (isAllBlank(date, reason, action, downtime, lossOrNote, noteForAcc)) continue;
            imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 1, date);
            imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 2, reason);
            imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 3, action);
            imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 4, downtime);
            if ("accident".equals(currentKind)) {
                imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 5, lossOrNote);
                imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 6, noteForAcc);
            } else {
                imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 6, lossOrNote);
            }
        }
        return imported;
    }

    private int importAccidentSheetByPosition(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = year + "-00";
        for (int r = 4; r <= 13; r += 1) {
            Row row = sheet.getRow(r);
            imported += importAccidentRecordRow(monthKey, year, row, ACCIDENT_PREFIX, true);
        }
        for (int r = 23; r <= 32; r += 1) {
            Row row = sheet.getRow(r);
            imported += importAccidentRecordRow(monthKey, year, row, MAINTENANCE_PREFIX, false);
        }
        return imported;
    }

    private int importAccidentRecordRow(String monthKey, int year, Row row, String prefix, boolean hasLoss) {
        if (row == null) return 0;
        String no = readCellValue(row.getCell(1));
        if (no == null || no.isBlank()) return 0;
        String rowId = no.replaceAll("\\.0$", "").trim();
        if (rowId.isBlank()) return 0;
        String rowKey = prefix + rowId;
        String date = readDateCellValue(row.getCell(2));
        String reason = readCellValue(row.getCell(3));
        String action = readCellValue(row.getCell(4));
        String downtime = readCellValue(row.getCell(5));
        String lossOrNote = readCellValue(row.getCell(6));
        String note = hasLoss ? readCellValue(row.getCell(7)) : lossOrNote;
        if (isAllBlank(date, reason, action, downtime, lossOrNote, note)) return 0;
        int imported = 0;
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 1, date);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 2, reason);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 3, action);
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 4, downtime);
        if (hasLoss) {
            imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 5, lossOrNote);
            imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 6, note);
        } else {
            imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 6, note);
        }
        imported += upsertCell(monthKey, year, 0, CELL_TABLE, rowKey, 7, rowId);
        return imported;
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
