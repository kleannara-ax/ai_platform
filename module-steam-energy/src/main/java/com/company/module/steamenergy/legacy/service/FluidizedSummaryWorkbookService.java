package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

@Service
public class FluidizedSummaryWorkbookService {
    private static final String ORIGINAL_DIR = "\uC6D0\uBCF8";

    private static final String PLAN_TABLE = "fluidized_plan_actual";
    private static final String SUMMARY_TABLE = "fluidized_plan_summary";
    private static final String NOTE_TABLE = "fluidized_year_note";
    private static final String DETAIL_MAIN_TABLE = "fluidized_detail_main";
    private static final String KNE_EXTRA_COST_KEY = "KNE_EXTRA_COST";
    private static final String SRF_INBOUND_TABLE = "fluidized_srf_inbound";
    private static final String CONTRACT_TABLE = "fluidized_contract_daily";
    private static final String COST_TABLE = "fluidized_contract_cost";
    private static final String IMPROVEMENT_MONTHLY_PREFIX = "improvement-monthly:";
    private static final String IMPROVEMENT_ITEM_PREFIX = "improvement-item:";
    private static final String ACCIDENT_PREFIX = "accident-record:";
    private static final String MAINTENANCE_PREFIX = "maintenance-record:";

    private static final int[] PLAN_INPUT_ROWS = {5, 6, 7, 8};
    private static final String[] PLAN_ROW_KEYS = {"operating", "power", "waste", "srf_income"};
    private static final int[][] MONTH_PLAN_COLS = {
            {2, 3}, {5, 6}, {8, 9}, {11, 12}, {14, 15}, {17, 18},
            {20, 21}, {23, 24}, {26, 27}, {29, 30}, {32, 33}, {35, 36}
    };

    private static final int SUMMARY_NOTE_ROW = 14;
    private static final int SUMMARY_NOTE_COL = 10;
    private static final int MONTHLY_NOTE_START_ROW = 23;
    private static final int MONTHLY_NOTE_COL = 3;

    private static final int CONTRACT_START_ROW = 7;
    private static final int CONTRACT_END_ROW = 37;
    private static final int COST_ROW = 43;

    private final TableService tableService;
    private final FluidizedDetailWorkbookService fluidizedDetailWorkbookService;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);
    private volatile Path cachedWorkbookPath;

    public FluidizedSummaryWorkbookService(TableService tableService, FluidizedDetailWorkbookService fluidizedDetailWorkbookService) {
        this.tableService = tableService;
        this.fluidizedDetailWorkbookService = fluidizedDetailWorkbookService;
    }

    public byte[] exportWorkbook(int year, int month) throws IOException {
        Path workbookPath = resolveWorkbookPath();
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            workbook.setForceFormulaRecalculation(true);

            Sheet annualSheet = workbook.getSheetAt(0);
            Sheet monthlySheet = workbook.getSheetAt(1);
            Sheet detailSheet = workbook.getSheetAt(2);
            Sheet monthlySummarySheet = workbook.getSheetAt(3);
            String monthKey = monthKey(year, month);
            Map<String, Object> annualCells = buildCellMap(loadCellsForYear(year));
            Map<String, Object> monthlyCells = buildCellMap(tableService.listAllByMonth("table_cell_value", monthKey));
            Map<String, String> detailCells = renderDetailCells(monthKey);
            Map<String, Map<String, String>> annualDetailCells = renderAnnualDetailCells(year);

            fillAnnualSheet(annualSheet, year, monthKey, annualCells, annualDetailCells);
            fillMonthlyCostSheet(monthlySheet, year, month, monthKey, monthlyCells);
            ensureDetailExtraCostRow(detailSheet);
            fillDetailSheet(detailSheet, detailCells);
            appendDetailCustomRows(detailSheet, year, month, monthKey);
            fillMonthlySummarySheet(monthlySummarySheet, year, month, monthKey, detailCells, annualCells);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    // ========================================================================
    // 탭별 부분 import/export
    //  - 실적비교: 시트1 단일. 입력 대상은 월별 특이사항만 (compare 본문은 계획대비실적의 실적 합산이므로 별도 저장 X).
    //  - 도급비용: 시트1 단일. 일별 contract 입력 셀 + 지급현황 입력 셀.
    // 셀 ↔ DB 매핑 사양:
    //   [실적비교]
    //     B10..B21  -> fluidized_year_note  row_key="note-01".."note-12", col_index=0
    //   [도급비용]
    //     일별표(7-37행):
    //       B(col1) T/D       -> fluidized_contract_daily row="DD" col_index=0
    //       D(col3) 스팀단가   -> col_index=2
    //       F(col5) 운휴시간   -> col_index=4
    //       G(col6) 고정비     -> col_index=5
    //     지급현황표(43행):
    //       B(col1) 인건비     -> fluidized_contract_cost  row="cost" col_index=0
    //       C(col2) 운영비     -> col_index=1
    //       D(col3) 설비개선비용-> col_index=2
    // ========================================================================

    public Map<String, Object> importCompareWorkbook(MultipartFile file, int year) throws IOException {
        int noteRows = 0;
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            // 월별 특이사항: B10..B21 (0-based row 9..20, col 1)
            String anchorMonth = yearAnchorKey(year);
            for (int monthIndex = 0; monthIndex < 12; monthIndex += 1) {
                Cell noteCell = getCell(sheet, 9 + monthIndex, 1);
                noteRows += upsertCell(
                        anchorMonth,
                        year,
                        1,
                        NOTE_TABLE,
                        "note-" + String.format("%02d", monthIndex + 1),
                        0,
                        readCellValue(noteCell)
                );
            }
        }
        return Map.of(
                "year", year,
                "note_rows", noteRows
        );
    }

    public Map<String, Object> importContractWorkbook(MultipartFile file, int year, int month) throws IOException {
        int contractRows = 0;
        int costRows = 0;
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            // 일별 도급비용: 7..37 행, B/D/F/G 컬럼 (0-based: 1, 3, 5, 6)
            for (int day = 1; day <= 31; day += 1) {
                if (day > monthDays) continue;
                int rowIndex = 6 + day - 1; // 0-based row index for day d
                String rowKey = String.format("%02d", day);
                contractRows += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 0,
                        readCellValue(getCell(sheet, rowIndex, 1))); // B
                contractRows += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 2,
                        readCellValue(getCell(sheet, rowIndex, 3))); // D
                contractRows += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 4,
                        readCellValue(getCell(sheet, rowIndex, 5))); // F
                contractRows += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 5,
                        readCellValue(getCell(sheet, rowIndex, 6))); // G
            }
            // 지급현황: 43 행, B/C/D 컬럼 (0-based: 1, 2, 3) → 인건비/운영비/설비개선비용
            int costRowIndex = 42; // 0-based row 42 = 43 행
            costRows += upsertCell(monthKey, year, month, COST_TABLE, "cost", 0,
                    readCellValue(getCell(sheet, costRowIndex, 1)));
            costRows += upsertCell(monthKey, year, month, COST_TABLE, "cost", 1,
                    readCellValue(getCell(sheet, costRowIndex, 2)));
            costRows += upsertCell(monthKey, year, month, COST_TABLE, "cost", 2,
                    readCellValue(getCell(sheet, costRowIndex, 3)));
        }
        return Map.of(
                "year", year,
                "month", String.format("%02d", month),
                "contract_rows", contractRows,
                "cost_rows", costRows
        );
    }

    public byte[] exportCompareWorkbook(int year) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("실적비교");
            // 1행: 제목
            setString(sheet, 0, 0, year + "년 유동상 소각로 위탁 운영비용 전년도 실적 및 비교 (단위: 백만원)");
            // 3행 헤더 (0-based 2)
            setString(sheet, 2, 0, "구 분");
            setString(sheet, 2, 1, "구 분");
            for (int m = 0; m < 12; m += 1) setString(sheet, 2, 2 + m, (m + 1) + "월");
            // 4..6행: compare 본문 (계산값)
            String anchorMonth = yearAnchorKey(year);
            // 실적값 계산은 클라이언트 로직과 동일: getPlanValueForYear("total", m, 1, year)
            // 서버에선 detail_main + srf_inbound 계산이 필요하므로 detailActuals 결과를 활용
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> currentActualCells = (Map<String, Map<String, String>>) detailActuals(year).get("months");
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> prevActualCells = (Map<String, Map<String, String>>) detailActuals(year - 1).get("months");
            for (int m = 0; m < 12; m += 1) {
                double prev = computeMonthlyActualTotal(prevActualCells, year - 1, m + 1);
                double curr = computeMonthlyActualTotal(currentActualCells, year, m + 1);
                if (m == 0) {
                    setString(sheet, 3, 0, "유동상 소각로 (합계)");
                    setString(sheet, 3, 1, (year - 1) + "실적");
                    setString(sheet, 4, 1, year + "실적");
                    setString(sheet, 5, 1, "증감");
                }
                setNumeric(sheet, 3, 2 + m, prev);
                setNumeric(sheet, 4, 2 + m, curr);
                setNumeric(sheet, 5, 2 + m, curr - prev);
            }
            // 9행: 월별 특이사항 헤더 (0-based 8)
            setString(sheet, 8, 0, "월");
            setString(sheet, 8, 1, "특이사항");
            // 10..21행: 1~12월 노트
            Map<String, String> notes = new LinkedHashMap<>();
            for (Map<String, Object> row : tableService.listAllByMonth("table_cell_value", anchorMonth)) {
                if (!NOTE_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
                if (toInt(row.get("col_index")) != 0) continue;
                notes.put(String.valueOf(row.get("row_key")), String.valueOf(row.get("cell_value")));
            }
            for (int m = 0; m < 12; m += 1) {
                setString(sheet, 9 + m, 0, (m + 1) + "월");
                String key = "note-" + String.format("%02d", m + 1);
                String note = notes.getOrDefault(key, "");
                if (note != null && !note.isBlank() && !"null".equals(note)) {
                    setString(sheet, 9 + m, 1, note);
                }
            }
            // 가로폭 적당히
            sheet.setColumnWidth(0, 6000);
            sheet.setColumnWidth(1, 5000);
            for (int c = 2; c < 14; c += 1) sheet.setColumnWidth(c, 3600);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportContractWorkbook(int year, int month) throws IOException {
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        Map<String, Object> monthlyCells = buildCellMap(tableService.listAllByMonth("table_cell_value", monthKey));
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("도급비용");
            // 1행: 제목
            setString(sheet, 0, 0, year + "년 " + month + "월 소각로 도급비용");
            // 헤더 4단 (0-based 2..5)
            setString(sheet, 2, 0, "일별");
            setString(sheet, 2, 1, "유동상 소각로");
            setString(sheet, 2, 8, "총 운영비용(원)");
            setString(sheet, 3, 1, "스팀 구매량 / 운휴시간");
            setString(sheet, 4, 1, "스팀 생산량");
            setString(sheet, 4, 3, "스팀단가(원/톤)");
            setString(sheet, 4, 4, "운영비용(원)");
            setString(sheet, 4, 5, "운휴시간(Hr)");
            setString(sheet, 4, 6, "운휴시간당 고정비(원/시간)");
            setString(sheet, 4, 7, "운영비용(원)");
            setString(sheet, 5, 1, "(T/D)");
            setString(sheet, 5, 2, "(T/hr)");
            // 7..37행 (0-based 6..36) — 31일
            double sumTd = 0, sumStopHr = 0, sumOpCost = 0, sumStopCost = 0, sumTotal = 0;
            int count = 0;
            for (int day = 1; day <= 31; day += 1) {
                int r = 6 + day - 1;
                setString(sheet, r, 0, day + "일");
                if (day > monthDays) continue;
                String dayKey = String.format("%02d", day);
                double td = toDouble(monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, dayKey, 0)));
                double price = toDouble(monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, dayKey, 2)));
                double stopHr = toDouble(monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, dayKey, 4)));
                double fixed = toDouble(monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, dayKey, 5)));
                double tphr = td / 24d;
                double opCost = td * price;
                double stopCost = stopHr * fixed;
                double total = opCost + stopCost;
                setNumeric(sheet, r, 1, td);
                setNumeric(sheet, r, 2, tphr);
                setNumeric(sheet, r, 3, price);
                setNumeric(sheet, r, 4, opCost);
                setNumeric(sheet, r, 5, stopHr);
                setNumeric(sheet, r, 6, fixed);
                setNumeric(sheet, r, 7, stopCost);
                setNumeric(sheet, r, 8, total);
                sumTd += td;
                sumStopHr += stopHr;
                sumOpCost += opCost;
                sumStopCost += stopCost;
                sumTotal += total;
                count += 1;
            }
            // 38행 합계 (0-based 37)
            setString(sheet, 37, 0, "합 계");
            setNumeric(sheet, 37, 1, sumTd);
            setNumeric(sheet, 37, 4, sumOpCost);
            setNumeric(sheet, 37, 5, sumStopHr);
            setNumeric(sheet, 37, 7, sumStopCost);
            setNumeric(sheet, 37, 8, sumTotal);
            // 39행 평균 (0-based 38)
            setString(sheet, 38, 0, "평 균");
            if (count > 0) {
                setNumeric(sheet, 38, 1, sumTd / count);
                setNumeric(sheet, 38, 2, sumTd / count / 24d);
                setNumeric(sheet, 38, 4, sumOpCost / count);
                setNumeric(sheet, 38, 5, sumStopHr / count);
                setNumeric(sheet, 38, 7, sumStopCost / count);
                setNumeric(sheet, 38, 8, sumTotal / count);
            }
            // 41행 지급현황 제목 (0-based 40)
            setString(sheet, 40, 0, "도급 비용 지급현황 [단위 : 원]");
            // 42행 헤더 (0-based 41)
            setString(sheet, 41, 0, "설비명");
            setString(sheet, 41, 1, "인건비");
            setString(sheet, 41, 2, "운영비");
            setString(sheet, 41, 3, "설비개선비용");
            setString(sheet, 41, 4, "총 지급비용");
            // 43행 데이터 (0-based 42)
            double labor = toDouble(monthlyCells.get(cellKey(COST_TABLE, monthKey, "cost", 0)));
            double operating = toDouble(monthlyCells.get(cellKey(COST_TABLE, monthKey, "cost", 1)));
            double improvement = toDouble(monthlyCells.get(cellKey(COST_TABLE, monthKey, "cost", 2)));
            setString(sheet, 42, 0, "유동상소각로");
            setNumeric(sheet, 42, 1, labor);
            setNumeric(sheet, 42, 2, operating);
            setNumeric(sheet, 42, 3, improvement);
            setNumeric(sheet, 42, 4, labor + operating + improvement);
            // 폭
            sheet.setColumnWidth(0, 3000);
            for (int c = 1; c < 9; c += 1) sheet.setColumnWidth(c, 4200);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private double computeMonthlyActualTotal(Map<String, Map<String, String>> actualCells,
                                             int year, int month) {
        String mk = monthKey(year, month);
        Map<String, String> ak = actualCells.getOrDefault(mk, Map.of());
        double operating = parseDouble(ak.get("AK16")) / 1_000_000d;
        double power = parseDouble(ak.get("AK65")) / 1_000_000d;
        double waste = 0;
        for (int row = 49; row <= 62; row += 1) waste += parseDouble(ak.get("AK" + row));
        waste /= 1_000_000d;
        double srfIncome = parseDouble(ak.get("AK46")) / 1_000_000d;
        return operating + power + waste + srfIncome;
    }

    private double toDouble(Object v) {
        if (v == null) return 0d;
        try { return Double.parseDouble(String.valueOf(v).replace(",", "").trim()); }
        catch (NumberFormatException e) { return 0d; }
    }

    private void setNumeric(Sheet sheet, int row, int col, double value) {
        Row r = sheet.getRow(row);
        if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col);
        if (c == null) c = r.createCell(col);
        c.setCellValue(value);
    }

    public Map<String, Object> importWorkbook(MultipartFile file, int year, int month) throws IOException {
        int importedPlanRows = 0;
        int importedNoteRows = 0;
        int importedContractRows = 0;
        int importedCostRows = 0;
        int importedDetailRows = 0;
        int importedSrfRows = 0;
        int importedImprovementRows = 0;
        int importedAccidentRows = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet annualSheet = workbook.getSheetAt(0);
            Sheet monthlySheet = workbook.getSheetAt(1);
            Sheet detailSheet = workbook.getSheetAt(2);
            Sheet srfSheet = workbook.getSheetAt(4);
            Sheet improvementSheet = resolveImprovementSheet(workbook, year);
            Sheet accidentSheet = workbook.getSheetAt(7);
            clearImportedWorkbookTargets(year, month);
            importedPlanRows += importAnnualPlanSheet(annualSheet, year);
            importedNoteRows += importNotes(annualSheet, year, month);
            importedContractRows += importContractSheet(monthlySheet, year, month);
            importedCostRows += importCostSheet(monthlySheet, year, month);
            importedDetailRows += importDetailSheet(detailSheet, year, month);
            importedSrfRows += importSrfInboundSheet(srfSheet, year, month);
            importedImprovementRows += importImprovementSheet(improvementSheet, year);
            importedAccidentRows += importAccidentSheet(accidentSheet, year);
        }

        return Map.of(
                "year", year,
                "month", String.format("%02d", month),
                "plan_rows", importedPlanRows,
                "note_rows", importedNoteRows,
                "contract_rows", importedContractRows,
                "cost_rows", importedCostRows,
                "detail_rows", importedDetailRows,
                "srf_rows", importedSrfRows,
                "improvement_rows", importedImprovementRows,
                "accident_rows", importedAccidentRows
        );
    }

    private void clearImportedWorkbookTargets(int year, int month) {
        String selectedMonth = monthKey(year, month);
        for (int monthNo = 1; monthNo <= 12; monthNo += 1) {
            tableService.deleteTableCellValuesByTables(monthKey(year, monthNo), List.of(
                    PLAN_TABLE,
                    NOTE_TABLE
            ));
        }
        tableService.deleteTableCellValuesByTables(selectedMonth, List.of(
                SUMMARY_TABLE,
                CONTRACT_TABLE,
                COST_TABLE,
                SRF_INBOUND_TABLE
        ));
        // DETAIL_MAIN_TABLE 은 사용자 정의 추가 행(row_key="custom|...") 을 보존한다.
        tableService.deleteTableCellValuesByTableExcludingRowKeyPrefixes(
                selectedMonth, DETAIL_MAIN_TABLE, List.of("custom|"));
        String yearMonth = yearAnchorKey(year).replace("-01", "-00");
        tableService.deleteTableCellRowsByMonthAndPrefixes(yearMonth, List.of(
                IMPROVEMENT_MONTHLY_PREFIX,
                IMPROVEMENT_ITEM_PREFIX,
                ACCIDENT_PREFIX,
                MAINTENANCE_PREFIX
        ));
    }

    public Map<String, Object> detailActuals(int year) {
        String fromMonth = monthKey(year - 1, 1);
        String toMonth = monthKey(year, 12);
        List<Map<String, Object>> detailRows = tableService.listCellRowsByTableNameBetweenMonths(DETAIL_MAIN_TABLE, fromMonth, toMonth);
        List<Map<String, Object>> srfRows = tableService.listCellRowsByTableNameBetweenMonths(SRF_INBOUND_TABLE, fromMonth, toMonth);

        Map<String, Map<String, String>> detailByMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : detailRows) {
            if (toInt(row.get("col_index")) != 0) continue;
            String month = String.valueOf(row.get("month"));
            String cellRef = String.valueOf(row.get("row_key"));
            String value = String.valueOf(row.get("cell_value"));
            detailByMonth.computeIfAbsent(month, key -> new LinkedHashMap<>()).put(cellRef, value);
        }

        Map<String, List<Map<String, Object>>> srfByMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : srfRows) {
            String month = String.valueOf(row.get("month"));
            srfByMonth.computeIfAbsent(month, key -> new ArrayList<>()).add(row);
        }

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (int targetYear = year - 1; targetYear <= year; targetYear += 1) {
            for (int month = 1; month <= 12; month += 1) {
                String targetMonth = monthKey(targetYear, month);
                result.put(targetMonth, calculateDetailActualCells(
                        detailByMonth.getOrDefault(targetMonth, Map.of()),
                        srfByMonth.getOrDefault(targetMonth, List.of())
                ));
            }
        }
        return Map.of("months", result);
    }

    private Map<String, String> calculateDetailActualCells(Map<String, String> cells, List<Map<String, Object>> srfInboundRows) {
        Map<String, String> result = new LinkedHashMap<>();
        double steamPurchase = sumDailyProduct(cells, 6, 11);
        double fixedCost = sumFixedCost(cells);
        double improvementCost = parseDouble(cells.get("AK15"));
        double extraCost = parseDouble(cells.get("AK16"));
        if (extraCost == 0d) {
            extraCost = parseDouble(cells.get(KNE_EXTRA_COST_KEY));
        }
        double operating = steamPurchase + fixedCost + improvementCost + extraCost;
        // 전력비: 세부 운영내역 row 65 (전력량(KW) Main) 의 일별 합계 × AJ65 단가(원/kW).
        // 기존엔 row 64(SRF/폐기물 소계)를 잘못 보고 있어 0이 나오던 부분 교정.
        double power = sumDaily(cells, 65) * parseDouble(cells.get("AJ65"));
        // SRF 수입금: SRF입고내역(fluidized_srf_inbound) 데이터에서 거래처별 월합계×단가/1000(원)을 합산한 뒤,
        // 비용을 수입(차감) 방향으로 부호 반전. 음수(반품/조정)도 그대로 보존된다.
        // 백만원 변환은 클라이언트에서 수행.
        double srfIncome = -calculateSrfInboundCost(srfInboundRows);

        result.put("AK16", toPlainString(operating));
        result.put("AK65", toPlainString(power));
        result.put("AK46", toPlainString(srfIncome));
        // 폐기물처리비 행: Excel '1.운영실적'!AD7 = SUM('3.세부'!AK49:AK62). 재고(AK48) 제외, 바닥재반출-삼영(AK62) 포함.
        for (int row = 49; row <= 62; row += 1) {
            result.put("AK" + row, toPlainString(sumDaily(cells, row) * parseDouble(cells.get("AJ" + row))));
        }
        return result;
    }

    /**
     * fluidized_srf_inbound 테이블의 거래처별 일자 반입량(row_key="entry:DD", col_index=1..10) 합계에
     * 거래처 단가(row_key="rate", col_index=1..10)를 곱하고 1000으로 나누어 거래처별 비용(원)을 구한 뒤
     * 전체 합계를 반환한다. unit-fluidized-srf-inbound 페이지의 처리비용 합계 계산과 동일 산식.
     */
    private double calculateSrfInboundCost(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return 0d;
        double[] vendorQty = new double[11];
        double[] vendorRate = new double[11];
        for (Map<String, Object> row : rows) {
            int colIndex = toInt(row.get("col_index"));
            if (colIndex < 1 || colIndex > 10) continue;
            String rowKey = String.valueOf(row.get("row_key"));
            double value = parseDouble(String.valueOf(row.get("cell_value")));
            if ("rate".equals(rowKey)) {
                vendorRate[colIndex] = value;
            } else if (rowKey != null && rowKey.startsWith("entry:")) {
                vendorQty[colIndex] += value;
            }
        }
        double total = 0d;
        for (int vendor = 1; vendor <= 10; vendor += 1) {
            total += vendorQty[vendor] * vendorRate[vendor] / 1000d;
        }
        return total;
    }

    private double sumDailyProduct(Map<String, String> cells, int rowA, int rowB) {
        double total = 0d;
        for (int day = 1; day <= 31; day += 1) {
            String colLabel = columnLabel(day + 2);
            total += parseDouble(cells.get(colLabel + rowA)) * parseDouble(cells.get(colLabel + rowB));
        }
        return total;
    }

    private double sumFixedCost(Map<String, String> cells) {
        double total = 0d;
        for (int day = 1; day <= 31; day += 1) {
            String colLabel = columnLabel(day + 2);
            String runText = cells.get(colLabel + "4");
            if (runText == null || runText.isBlank()) continue;
            double stoppedHours = 24d - parseDouble(runText);
            total += stoppedHours * parseDouble(cells.get(colLabel + "13"));
        }
        return total;
    }

    private double sumDaily(Map<String, String> cells, int row) {
        double total = 0d;
        for (int day = 1; day <= 31; day += 1) {
            total += parseDouble(cells.get(columnLabel(day + 2) + row));
        }
        return total;
    }

    private void fillAnnualSheet(
            Sheet sheet,
            int year,
            String selectedMonthKey,
            Map<String, Object> annualCells,
            Map<String, Map<String, String>> annualDetailCells
    ) {
        setString(sheet, 0, 0, "1. 운영실적");

        for (int monthIndex = 0; monthIndex < 12; monthIndex += 1) {
            String targetMonth = monthKey(year, monthIndex + 1);
            Map<String, String> targetDetailCells = annualDetailCells.getOrDefault(targetMonth, Map.of());
            for (int rowIndex = 0; rowIndex < PLAN_ROW_KEYS.length; rowIndex += 1) {
                Object plan = annualCells.get(cellKey(PLAN_TABLE, targetMonth, PLAN_ROW_KEYS[rowIndex], 0));
                Object actual = calculatePlanActual(PLAN_ROW_KEYS[rowIndex], targetDetailCells);
                setCellValue(sheet, PLAN_INPUT_ROWS[rowIndex] - 1, MONTH_PLAN_COLS[monthIndex][0] - 1, plan);
                setCellValue(sheet, PLAN_INPUT_ROWS[rowIndex] - 1, MONTH_PLAN_COLS[monthIndex][1] - 1, actual);
            }

            Object note = annualCells.get(cellKey(
                    NOTE_TABLE,
                    yearAnchorKey(year),
                    "note-" + String.format("%02d", monthIndex + 1),
                    0
            ));
            setCellValue(sheet, MONTHLY_NOTE_START_ROW + monthIndex - 1, MONTHLY_NOTE_COL, note);
        }

        Object summaryNote = annualCells.get(cellKey(SUMMARY_TABLE, selectedMonthKey, "monthly-summary", 0));
        setCellValue(sheet, SUMMARY_NOTE_ROW - 1, SUMMARY_NOTE_COL, summaryNote);
    }

    private Map<String, Map<String, String>> renderAnnualDetailCells(int year) throws IOException {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month += 1) {
            String targetMonth = monthKey(year, month);
            result.put(targetMonth, renderDetailCells(targetMonth));
        }
        return result;
    }

    private Object calculatePlanActual(String rowKey, Map<String, String> detailCells) {
        return switch (rowKey) {
            case "operating" -> divideText(detailCells.get("AK16"), 1000000d);
            case "power" -> divideText(detailCells.get("AK65"), 1000000d);
            case "waste" -> sumDetailCosts(detailCells, 49, 62) / 1000000d;
            case "srf_income" -> divideText(detailCells.get("AK46"), 1000000d);
            default -> "";
        };
    }

    private double sumDetailCosts(Map<String, String> detailCells, int startRow, int endRow) {
        double total = 0d;
        for (int row = startRow; row <= endRow; row += 1) {
            total += parseDouble(detailCells.get("AK" + row));
        }
        return total;
    }

    private void fillMonthlyCostSheet(Sheet sheet, int year, int month, String monthKey, Map<String, Object> monthlyCells) {
        setString(sheet, 0, 1, String.format("1. %d년 %d월 소각로 도급 비용", year, month));

        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        for (int day = 1; day <= 31; day += 1) {
            int rowIndex = CONTRACT_START_ROW + day - 1;
            clearCell(sheet, rowIndex - 1, 2);
            clearCell(sheet, rowIndex - 1, 4);
            clearCell(sheet, rowIndex - 1, 6);
            clearCell(sheet, rowIndex - 1, 7);

            if (day > monthDays) {
                continue;
            }

            String rowKey = String.format("%02d", day);
            setCellValue(sheet, rowIndex - 1, 2, monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, rowKey, 0)));
            setCellValue(sheet, rowIndex - 1, 4, monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, rowKey, 2)));
            setCellValue(sheet, rowIndex - 1, 6, monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, rowKey, 4)));
            setCellValue(sheet, rowIndex - 1, 7, monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, rowKey, 5)));
        }

        setCellValue(sheet, COST_ROW - 1, 4, monthlyCells.get(cellKey(COST_TABLE, monthKey, "cost", 0)));
        setCellValue(sheet, COST_ROW - 1, 6, monthlyCells.get(cellKey(COST_TABLE, monthKey, "cost", 2)));
    }

    private Map<String, String> renderDetailCells(String monthKey) throws IOException {
        Map<String, Object> rendered = fluidizedDetailWorkbookService.renderMonth(monthKey, "all");
        Map<String, String> cells = new LinkedHashMap<>();
        Object main = rendered.get("main");
        if (main instanceof Map<?, ?> mainMap) {
            Object mainCells = mainMap.get("cells");
            if (mainCells instanceof Map<?, ?> cellMap) {
                cellMap.forEach((key, value) -> cells.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
            }
        }
        Object summary = rendered.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            Object summaryCells = summaryMap.get("cells");
            if (summaryCells instanceof Map<?, ?> cellMap) {
                cellMap.forEach((key, value) -> cells.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
            }
        }
        return cells;
    }

    private void fillDetailSheet(Sheet sheet, Map<String, String> detailCells) {
        detailCells.forEach((cellRef, value) -> setCellValue(sheet, cellRef, value));
    }

    /**
     * 추가 항목을 detail 시트의 insertAfter 위치 바로 아래에 inline 삽입.
     * row_key 패턴: custom|{section}|{rowId}|label / d{N} / insertAfter
     * 컬럼: B=항목명, C..AG=1..31일, AK=비용(합계). A 는 섹션 rowspan 으로 점유.
     * 업로드(import)는 main grid 의 cellRef 기반이라 inline 행은 자동 무시.
     */
    private void appendDetailCustomRows(Sheet sheet, int year, int month, String monthKey) {
        List<Map<String, Object>> rows = tableService.listAllByMonth("table_cell_value", monthKey);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        for (Map<String, Object> row : rows) {
            if (!DETAIL_MAIN_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            String rowKey = String.valueOf(row.get("row_key"));
            if (rowKey == null || !rowKey.startsWith("custom|")) continue;
            String[] parts = rowKey.split("\\|");
            if (parts.length != 4) continue;
            String section = parts[1];
            String rowId = parts[2];
            String suffix = parts[3];
            String key = section + "|" + rowId;
            Map<String, Object> entry = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("section", section);
                m.put("rowId", rowId);
                m.put("label", "");
                m.put("insertAfter", 0);
                m.put("days", new LinkedHashMap<Integer, Double>());
                return m;
            });
            String value = String.valueOf(row.get("cell_value"));
            if ("label".equals(suffix)) {
                entry.put("label", value);
            } else if ("insertAfter".equals(suffix)) {
                Double n = parseNumeric(value);
                entry.put("insertAfter", n == null ? 0 : n.intValue());
            } else if (suffix.startsWith("d")) {
                try {
                    int day = Integer.parseInt(suffix.substring(1));
                    Double n = parseNumeric(value);
                    @SuppressWarnings("unchecked")
                    Map<Integer, Double> days = (Map<Integer, Double>) entry.get("days");
                    days.put(day, n == null ? 0d : n);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (grouped.isEmpty()) return;

        // insertAfter 별로 그룹화 → 같은 위치는 rowId 순으로 정렬
        Map<Integer, List<Map<String, Object>>> byInsertAfter = new TreeMap<>(Comparator.reverseOrder());
        for (Map<String, Object> entry : grouped.values()) {
            int ia = ((Number) entry.get("insertAfter")).intValue();
            if (ia <= 0) continue;
            byInsertAfter.computeIfAbsent(ia, k -> new ArrayList<>()).add(entry);
        }
        if (byInsertAfter.isEmpty()) return;

        // insertAfter 가 큰 것부터 처리 → 작은 것의 행 번호 영향 최소화 (rowspan 보정 후처리)
        for (Map.Entry<Integer, List<Map<String, Object>>> e : byInsertAfter.entrySet()) {
            int insertAfter = e.getKey();  // 1-based main grid row 번호
            List<Map<String, Object>> entries = e.getValue();
            entries.sort(Comparator.comparing(o -> String.valueOf(o.get("rowId"))));
            int n = entries.size();
            int poiInsertAt = insertAfter; // POI 0-based; main row N = POI N-1; 다음 위치 = POI N
            int lastRow = sheet.getLastRowNum();
            if (poiInsertAt <= lastRow) {
                sheet.shiftRows(poiInsertAt, lastRow, n);
            }
            for (int i = 0; i < n; i += 1) {
                Map<String, Object> entry = entries.get(i);
                int writeRow = poiInsertAt + i;
                sheet.createRow(writeRow);
                // B(1): 항목명
                setString(sheet, writeRow, 1, String.valueOf(entry.get("label")));
                @SuppressWarnings("unchecked")
                Map<Integer, Double> days = (Map<Integer, Double>) entry.get("days");
                double sum = 0d;
                for (int d = 1; d <= 31; d += 1) {
                    Double v = days.get(d);
                    if (v == null) continue;
                    if (d > monthDays) continue;
                    setNumeric(sheet, writeRow, 1 + d, v); // C(2)..AG(32)
                    sum += v;
                }
                setNumeric(sheet, writeRow, 36, sum); // AK
            }
            // 섹션 A 컬럼의 rowspan(merged region) 자동 확장 — POI shiftRows 가 merged region 도 함께 이동시키지만,
            // 삽입 위치가 merged region 내부면 region 의 lastRow 가 자동으로 늘어남 (POI 동작). 추가 보정 불필요.
        }
    }

    private int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i += 1) if (arr[i].equals(v)) return i;
        return Integer.MAX_VALUE;
    }

    private Double parseNumeric(String value) {
        if (value == null) return null;
        String raw = value.replace(",", "").trim();
        if (raw.isEmpty()) return null;
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException ignored) { return null; }
    }

    private void fillMonthlySummarySheet(
            Sheet sheet,
            int year,
            int month,
            String selectedMonthKey,
            Map<String, String> detailCells,
            Map<String, Object> annualCells
    ) {
        int rowIndex = month + 3;
        setCellValue(sheet, rowIndex - 1, 1, detailCells.get("AH8"));
        setCellValue(sheet, rowIndex - 1, 2, detailCells.get("AH9"));
        setCellValue(sheet, rowIndex - 1, 4, detailCells.get("AH45"));
        setCellValue(sheet, rowIndex - 1, 5, detailCells.get("AH6"));
        setCellValue(sheet, rowIndex - 1, 6, detailCells.get("AH7"));
        setCellValue(sheet, rowIndex - 1, 7, detailCells.get("AH4"));
        setCellValue(sheet, rowIndex - 1, 8, detailCells.get("AH5"));
        setCellValue(sheet, rowIndex - 1, 17, divideText(detailCells.get("AK95"), 1000d));

        Object improvementCost = annualCells.get(cellKey(COST_TABLE, selectedMonthKey, "cost", 2));
        if (improvementCost != null && !String.valueOf(improvementCost).isBlank()) {
            setCellValue(sheet, rowIndex - 1, 11, divideText(String.valueOf(improvementCost), 1000d));
        }

        setString(sheet, 2, 0, year + "\uB144\uB3C4");
    }

    private int importAnnualPlanSheet(Sheet sheet, int year) {
        int imported = 0;

        for (int monthIndex = 0; monthIndex < 12; monthIndex += 1) {
            String targetMonth = monthKey(year, monthIndex + 1);

            for (int rowIndex = 0; rowIndex < PLAN_ROW_KEYS.length; rowIndex += 1) {
                Cell planCell = getCell(sheet, PLAN_INPUT_ROWS[rowIndex] - 1, MONTH_PLAN_COLS[monthIndex][0] - 1);
                String planValue = readCellValue(planCell);

                imported += upsertCell(targetMonth, year, monthIndex + 1, PLAN_TABLE, PLAN_ROW_KEYS[rowIndex], 0, planValue);
            }
        }

        return imported;
    }

    private int importNotes(Sheet sheet, int year, int month) {
        int imported = 0;
        String anchorMonth = yearAnchorKey(year);
        String selectedMonthKey = monthKey(year, month);

        for (int monthIndex = 0; monthIndex < 12; monthIndex += 1) {
            Cell noteCell = getCell(sheet, MONTHLY_NOTE_START_ROW + monthIndex - 1, MONTHLY_NOTE_COL);
            imported += upsertCell(
                    anchorMonth,
                    year,
                    1,
                    NOTE_TABLE,
                    "note-" + String.format("%02d", monthIndex + 1),
                    0,
                    readCellValue(noteCell)
            );
        }

        Cell summaryNoteCell = getCell(sheet, SUMMARY_NOTE_ROW - 1, SUMMARY_NOTE_COL);
        imported += upsertCell(selectedMonthKey, year, month, SUMMARY_TABLE, "monthly-summary", 0, readCellValue(summaryNoteCell));

        return imported;
    }

    private int importContractSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();

        for (int day = 1; day <= 31; day += 1) {
            if (day > monthDays) {
                continue;
            }

            int rowIndex = CONTRACT_START_ROW + day - 1;
            String rowKey = String.format("%02d", day);
            imported += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 0, readCellValue(getCell(sheet, rowIndex - 1, 2)));
            imported += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 2, readCellValue(getCell(sheet, rowIndex - 1, 4)));
            imported += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 4, readCellValue(getCell(sheet, rowIndex - 1, 6)));
            imported += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, 5, readCellValue(getCell(sheet, rowIndex - 1, 7)));
        }

        return imported;
    }

    private int importCostSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);

        String laborValue = readCellValue(getCell(sheet, COST_ROW - 1, 4));
        String improvementValue = readCellValue(getCell(sheet, COST_ROW - 1, 6));

        imported += upsertCell(monthKey, year, month, COST_TABLE, "cost", 0, laborValue);
        imported += upsertCell(monthKey, year, month, COST_TABLE, "cost", 2, improvementValue);

        return imported;
    }

    private int importDetailSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        boolean hasExtraCostRow = hasDetailExtraCostRow(sheet);
        if (!hasExtraCostRow) {
            deleteDetailExtraCost(monthKey);
        }

        for (int row = 1; row <= 102; row += 1) {
            int targetRow = mapDetailImportRow(row, hasExtraCostRow);
            if (targetRow < 1 || targetRow > 102) continue;
            for (int col = 1; col <= 37; col += 1) {
                String colLabel = columnLabel(col);
                if (!isEditableDetailCell(targetRow, colLabel)) continue;
                if (isDayColumn(colLabel) && dayNumber(colLabel) > monthDays) continue;
                String cellRef = colLabel + targetRow;
                String value = readCellValue(getCell(sheet, row - 1, col - 1));
                if ("AK16".equals(cellRef) && value.isBlank()) {
                    deleteDetailExtraCost(monthKey);
                    continue;
                }
                imported += upsertCell(monthKey, year, month, DETAIL_MAIN_TABLE, cellRef, 0, value);
            }
        }
        return imported;
    }

    private void deleteDetailExtraCost(String monthKey) {
        tableService.deleteTableCellValue(monthKey, DETAIL_MAIN_TABLE, "AK16", 0);
        tableService.deleteTableCellValue(monthKey, DETAIL_MAIN_TABLE, KNE_EXTRA_COST_KEY, 0);
    }

    private boolean hasDetailExtraCostRow(Sheet sheet) {
        String rowLabel = (readCellValue(getCell(sheet, 15, 0)) + " " + readCellValue(getCell(sheet, 15, 1))).replaceAll("\\s+", "");
        return rowLabel.contains("\uCD94\uAC00\uBE44\uC6A9");
    }

    private void ensureDetailExtraCostRow(Sheet sheet) {
        if (hasDetailExtraCostRow(sheet)) return;
        int insertRowIndex = 15;
        int lastRow = Math.max(sheet.getLastRowNum(), 101);
        sheet.shiftRows(insertRowIndex, lastRow, 1, true, false);
        Row row = sheet.getRow(insertRowIndex);
        if (row == null) row = sheet.createRow(insertRowIndex);
        setCellValue(sheet, insertRowIndex, 1, "\uCD94\uAC00\uBE44\uC6A9");
        clearCell(sheet, insertRowIndex, 35);
        clearCell(sheet, insertRowIndex, 36);
    }

    private int mapDetailImportRow(int sourceRow, boolean hasExtraCostRow) {
        if (hasExtraCostRow) return sourceRow;
        if (sourceRow == 16) return -1;
        if (sourceRow >= 17) return sourceRow + 1;
        return sourceRow;
    }

    private int importSrfInboundSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();

        for (int day = 1; day <= monthDays; day += 1) {
            int rowIndex = 5 + day - 1;
            String rowKey = "entry:" + String.format("%02d", day);
            for (int vendorIndex = 0; vendorIndex < 10; vendorIndex += 1) {
                imported += upsertCell(monthKey, year, month, SRF_INBOUND_TABLE, rowKey, vendorIndex + 1, readCellValue(getCell(sheet, rowIndex - 1, 2 + vendorIndex)));
            }
        }
        for (int vendorIndex = 0; vendorIndex < 10; vendorIndex += 1) {
            imported += upsertCell(monthKey, year, month, SRF_INBOUND_TABLE, "rate", vendorIndex + 1, readCellValue(getCell(sheet, 36, 2 + vendorIndex)));
        }
        return imported;
    }

    private int importImprovementSheet(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = yearAnchorKey(year).replace("-01", "-00");

        String currentMonth = "";
        int monthlySort = 1;
        for (int row = 6; row <= 25; row += 1) {
            String monthValue = readCellValue(getCell(sheet, row - 1, 1));
            if (!monthValue.isBlank()) currentMonth = normalizeMonthText(monthValue);
            String payment = readCellValue(getCell(sheet, row - 1, 2));
            String spent = readCellValue(getCell(sheet, row - 1, 3));
            String vendor = readCellValue(getCell(sheet, row - 1, 5));
            String usage = readCellValue(getCell(sheet, row - 1, 6));
            if (currentMonth.isBlank() && payment.isBlank() && spent.isBlank() && vendor.isBlank() && usage.isBlank()) continue;
            String rowKey = IMPROVEMENT_MONTHLY_PREFIX + "xlsx-r" + row;
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 1, currentMonth.isBlank() ? "01" : currentMonth);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 2, payment);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 3, spent);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 4, vendor);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 5, usage);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 6, String.valueOf(monthlySort++));
        }

        String currentCategory = "";
        int itemSort = 1;
        for (int row = 6; row <= 26; row += 1) {
            String category = readCellValue(getCell(sheet, row - 1, 8));
            if (!category.isBlank()) currentCategory = normalizeImprovementCategory(category);
            String no = readCellValue(getCell(sheet, row - 1, 9));
            String item = readCellValue(getCell(sheet, row - 1, 10));
            String planCost = readCellValue(getCell(sheet, row - 1, 11));
            String actualCost = readCellValue(getCell(sheet, row - 1, 12));
            String status = readCellValue(getCell(sheet, row - 1, 13));
            String note = readCellValue(getCell(sheet, row - 1, 14));
            if (currentCategory.isBlank() && no.isBlank() && item.isBlank() && planCost.isBlank() && actualCost.isBlank() && status.isBlank() && note.isBlank()) continue;
            if (isImprovementSummaryRow(currentCategory)) continue;
            String rowKey = IMPROVEMENT_ITEM_PREFIX + "xlsx-r" + row;
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 1, currentCategory.isBlank() ? "설비개선" : currentCategory);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 2, no);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 3, item);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 4, planCost);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 5, actualCost);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 6, status);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 7, note);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 8, String.valueOf(itemSort++));
        }
        return imported;
    }

    private int importAccidentSheet(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = yearAnchorKey(year).replace("-01", "-00");
        imported += importAccidentRows(sheet, year, monthKey, "accident", ACCIDENT_PREFIX, 5, 11);
        imported += importAccidentRows(sheet, year, monthKey, "maintenance", MAINTENANCE_PREFIX, 20, 29);
        return imported;
    }

    private int importAccidentRows(Sheet sheet, int year, String monthKey, String kind, String prefix, int startRow, int endRow) {
        int imported = 0;
        int sortOrder = 1;
        for (int row = startRow; row <= endRow; row += 1) {
            String date = readDateCellValue(getCell(sheet, row - 1, 2));
            String reason = readCellValue(getCell(sheet, row - 1, 3));
            String action = readCellValue(getCell(sheet, row - 1, 4));
            String downtime = readCellValue(getCell(sheet, row - 1, 5));
            String lossOrNote = readCellValue(getCell(sheet, row - 1, 6));
            String note = "accident".equals(kind) ? readCellValue(getCell(sheet, row - 1, 7)) : lossOrNote;
            if (date.isBlank() && reason.isBlank() && action.isBlank() && downtime.isBlank() && lossOrNote.isBlank() && note.isBlank()) continue;
            String rowKey = prefix + "xlsx-r" + row;
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 1, date);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 2, reason);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 3, action);
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 4, downtime);
            if ("accident".equals(kind)) {
                imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 5, lossOrNote);
                imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 6, note);
            } else {
                imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 6, note);
            }
            imported += upsertCell(monthKey, year, 0, "table_cell_value", rowKey, 7, String.valueOf(sortOrder++));
        }
        return imported;
    }

    private Sheet resolveImprovementSheet(Workbook workbook, int year) {
        String yearToken = "'" + String.format("%02d", year % 100) + "년";
        for (int index = 0; index < workbook.getNumberOfSheets(); index += 1) {
            Sheet sheet = workbook.getSheetAt(index);
            String name = sheet.getSheetName();
            if (name.contains(yearToken) && name.contains("설비개선")) {
                return sheet;
            }
        }
        return workbook.getSheetAt(6);
    }

    private boolean isEditableDetailCell(int row, String colLabel) {
        if (row == 2) return false;
        if (row == 1 || row == 3 || row == 100 || row == 101) return false;
        if (row == 10) return false;
        if (row == 43 || row == 44) return false;
        if (row == 16) return "AK".equals(colLabel);
        if ("AJ".equals(colLabel)) return isDirectUnitPriceRow(row);
        if ("AK".equals(colLabel)) return row == 15 || row == 16;
        if (colToNumber(colLabel) >= colToNumber("AH")) return false;
        if (row >= 102) return false;
        if ("A".equals(colLabel) || "B".equals(colLabel)) return false;
        if (row == 5 || row == 7) return false;
        if (row == 12 || row == 14) return false;
        if (row == 20 || row == 23 || row == 26 || row == 29 || row == 32 || row == 35 || row == 38 || row == 45 || row == 47 || row == 48) return false;
        return row != 63 && row != 64 && row != 73 && row != 80 && row != 93 && row != 94 && row != 95 && row != 96;
    }

    private boolean isDirectUnitPriceRow(int row) {
        return List.of(
                6, 18, 21, 22, 24, 25, 27, 28, 30, 33, 36, 40, 46,
                49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62,
                65, 71, 72, 74, 76, 77, 78, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92
        ).contains(row);
    }

    private boolean isDayColumn(String colLabel) {
        int col = colToNumber(colLabel);
        return col >= colToNumber("C") && col <= colToNumber("AG");
    }

    private int dayNumber(String colLabel) {
        return colToNumber(colLabel) - colToNumber("C") + 1;
    }

    private int colToNumber(String colLabel) {
        int result = 0;
        for (int index = 0; index < colLabel.length(); index += 1) {
            result = result * 26 + (colLabel.charAt(index) - 'A' + 1);
        }
        return result;
    }

    private String columnLabel(int col) {
        StringBuilder label = new StringBuilder();
        int value = col;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            label.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return label.toString();
    }

    private String normalizeMonthText(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return "";
        int month = Integer.parseInt(digits);
        if (month < 1 || month > 12) return "";
        return String.format("%02d", month);
    }

    private String normalizeImprovementCategory(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "");
        if (normalized.contains("추가")) return "추가 작업사항";
        if (normalized.contains("유지")) return "유지보수";
        if (normalized.contains("추진") || normalized.contains("완료") || normalized.contains("합계") || normalized.contains("총")) return normalized;
        return "설비개선";
    }

    private boolean isImprovementSummaryRow(String category) {
        if (category == null) return false;
        return category.contains("추진건수") || category.contains("추가건수") || category.contains("합계") || category.contains("총");
    }

    private List<Map<String, Object>> loadCellsForYear(int year) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int month = 1; month <= 12; month += 1) {
            rows.addAll(tableService.listAllByMonth("table_cell_value", monthKey(year, month)));
        }
        return rows;
    }

    private Map<String, Object> buildCellMap(List<Map<String, Object>> rows) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            mapped.put(
                    cellKey(
                            String.valueOf(row.get("table_name")),
                            String.valueOf(row.get("month")),
                            String.valueOf(row.get("row_key")),
                            toInt(row.get("col_index"))
                    ),
                    row.get("cell_value")
            );
        }
        return mapped;
    }

    private String cellKey(String tableName, String month, String rowKey, int colIndex) {
        return tableName + "|" + month + "|" + rowKey + "|" + colIndex;
    }

    private int upsertCell(String month, int year, int monthNo, String tableName, String rowKey, int colIndex, String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        String normalizedValue = normalizeUploadedValue(value);
        payload.put("month", month);
        payload.put("year_no", year);
        payload.put("month_no", monthNo);
        payload.put("table_name", tableName);
        payload.put("row_key", rowKey);
        payload.put("col_index", colIndex);
        payload.put("cell_value", normalizedValue);
        tableService.upsert("table_cell_value", payload);
        return 1;
    }

    private String normalizeUploadedValue(String value) {
        if (value == null) return "";
        return "-".equals(value.trim()) ? "0" : value;
    }

    private Path resolveWorkbookPath() throws IOException {
        Path cached = cachedWorkbookPath;
        if (cached != null && Files.isRegularFile(cached)) {
            return cached;
        }
        Path originalDir = Path.of("").toAbsolutePath().resolve(ORIGINAL_DIR);
        try (Stream<Path> stream = Files.list(originalDir)) {
            Path resolved = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        String lower = name.toLowerCase(Locale.ROOT);
                        return name.startsWith("2.") && (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".xls"));
                    })
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new IOException("Fluidized summary workbook not found in original folder"));
            cachedWorkbookPath = resolved;
            return resolved;
        }
    }

    private void setString(Sheet sheet, int rowIndex, int colIndex, String value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        cell.setCellValue(value);
    }

    private void setCellValue(Sheet sheet, int rowIndex, int colIndex, Object value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);

        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }

        String text = String.valueOf(value).replace(",", "").trim();
        try {
            cell.setCellValue(new BigDecimal(text).doubleValue());
        } catch (NumberFormatException exception) {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void setCellValue(Sheet sheet, String cellRef, Object value) {
        org.apache.poi.ss.util.CellAddress address = new org.apache.poi.ss.util.CellAddress(cellRef);
        setCellValue(sheet, address.getRow(), address.getColumn(), value);
    }

    private String divideText(String value, double divisor) {
        if (value == null || value.isBlank()) return "";
        try {
            double parsed = new BigDecimal(value.replace(",", "").trim()).doubleValue();
            return BigDecimal.valueOf(parsed / divisor).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    private void clearCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) return;
        Cell cell = row.getCell(colIndex);
        if (cell != null) {
            cell.setBlank();
        }
    }

    private Cell getCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(colIndex);
    }

    private String readCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            // 캐시된 수식 결과를 그대로 사용. 원본 엑셀이 저장될 때 함께
            // 직렬화되므로 별도 재평가 없이 일치하는 값을 얻을 수 있다.
            return switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> numberString(cell.getNumericCellValue());
                case STRING -> {
                    String text = cell.getStringCellValue();
                    yield text == null ? "" : text.trim();
                }
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                default -> "";
            };
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return numberString(cell.getNumericCellValue());
        }
        String text = dataFormatter.formatCellValue(cell);
        return text == null ? "" : text.trim();
    }

    private String readDateCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK || cell.getCellType() == CellType.FORMULA) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double numericValue = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell) || (numericValue >= 30000 && numericValue <= 60000)) {
                LocalDate date = DateUtil.getJavaDate(numericValue)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                return date.toString();
            }
        }
        return readCellValue(cell);
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0d;
        try {
            return new BigDecimal(value.replace(",", "").trim()).doubleValue();
        } catch (NumberFormatException ignored) {
            return 0d;
        }
    }

    private String toPlainString(double value) {
        return numberString(value);
    }

    /**
     * 엑셀 double 값을 DB 문자열로 직렬화한다.
     * BigDecimal.valueOf(double)는 Double.toString을 그대로 따르기 때문에
     * 원본 셀이 "64.260000000000005" 같이 IEEE 754 잡티를 직접 적어 둔 경우
     * 인접한 다른 double로 인식되어 노이즈가 그대로 저장될 수 있다.
     * 따라서 10자리 소수로 반올림한 뒤 trailing zero 정리를 한다.
     * 산업 데이터는 10자리 소수 이상의 의미가 거의 없으므로 안전.
     */
    private String numberString(double value) {
        if (!Double.isFinite(value)) return "";
        BigDecimal bd = BigDecimal.valueOf(value);
        if (bd.scale() > 10) {
            bd = bd.setScale(10, RoundingMode.HALF_UP);
        }
        bd = bd.stripTrailingZeros();
        if (bd.scale() < 0) {
            bd = bd.setScale(0);
        }
        return bd.toPlainString();
    }

    private String monthKey(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }

    private String yearAnchorKey(int year) {
        return String.format("%04d-01", year);
    }
}
