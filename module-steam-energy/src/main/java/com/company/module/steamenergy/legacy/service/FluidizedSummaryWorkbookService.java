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

    private static final String[] PLAN_ROW_KEYS = {"operating", "power", "waste", "srf_income"};

    private static final int MONTHLY_NOTE_COL = 3;


    /** 세부 운영내역에서 훑을 서식 기준 범위 — 줄은 1~102, 칸은 A~AK. */
    private static final int DETAIL_LAST_ROW = 102;
    private static final int DETAIL_LAST_COL = 37;

    /** 서식에만 있고 파일에는 빠져 있기도 한 "KNE지급비용 / 추가비용" 줄(서식 기준). */
    private static final int DETAIL_EXTRA_COST_ROW = 16;

    /** 일별 도급비용: 머리글 키 → {@code steam_cell_value.col_index}. 순서가 화면 열 순서다. */
    private static final Map<String, Integer> CONTRACT_COL_INDEX = new LinkedHashMap<>(Map.of(
            "steam", 0, "price", 2, "stopHours", 4, "fixedCost", 5
    ));

    /** 지급현황: 머리글 키 → {@code steam_cell_value.col_index}. */
    private static final Map<String, Integer> COST_COL_INDEX = Map.of(
            "labor", 0, "operating", 1, "improvement", 2
    );

    private final TableService tableService;
    private final FluidizedDetailWorkbookService fluidizedDetailWorkbookService;
    private final SheetMetadataLoader sheetMetadataLoader;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);
    private volatile Path cachedWorkbookPath;

    public FluidizedSummaryWorkbookService(TableService tableService,
                                           FluidizedDetailWorkbookService fluidizedDetailWorkbookService,
                                           SheetMetadataLoader sheetMetadataLoader) {
        this.tableService = tableService;
        this.fluidizedDetailWorkbookService = fluidizedDetailWorkbookService;
        this.sheetMetadataLoader = sheetMetadataLoader;
    }

    /** 세부 운영내역 시트에서 항목 이름으로 줄·칸을 잇는 색인. */
    private FluidizedDetailSheetIndex detailIndex(Sheet sheet) {
        return FluidizedDetailSheetIndex.of(sheet, sheetMetadataLoader.load(FluidizedDetailSheetIndex.METADATA_NAME));
    }

    public byte[] exportWorkbook(int year, int month) throws IOException {
        Path workbookPath = resolveWorkbookPath();
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            workbook.setForceFormulaRecalculation(true);

            // 내려받기도 업로드와 같은 방식으로 시트를 찾는다.
            Sheet annualSheet = FluidizedSummaryLayout.sheetByName(workbook, 0, "1.");
            Sheet monthlySheet = FluidizedSummaryLayout.sheetByName(workbook, 1, "2.");
            Sheet detailSheet = FluidizedSummaryLayout.sheetByName(workbook, 2, "3. 세부");
            Sheet monthlySummarySheet = FluidizedSummaryLayout.sheetByName(workbook, 3, "3-1.");
            String monthKey = monthKey(year, month);
            Map<String, Object> annualCells = buildCellMap(loadCellsForYear(year));
            Map<String, Object> monthlyCells = buildCellMap(tableService.listAllByMonth("steam_cell_value", monthKey));
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
    // 셀 ↔ DB 매핑 사양 — 칸은 좌표가 아니라 머리글 글자로 찾는다(FluidizedSummaryLayout).
    //   [실적비교]
    //     "월 / 특이사항" 머리글 아래 "N월" 줄 -> fluidized_year_note row_key="note-01".."note-12", col_index=0
    //   [도급비용]
    //     일별표("일별" 칸의 "N일" 줄):
    //       스팀 생산량        -> fluidized_contract_daily row="DD" col_index=0
    //       스팀단가           -> col_index=2
    //       운휴시간           -> col_index=4
    //       운휴시간당 고정비   -> col_index=5
    //     지급현황표("설비명" 머리글 다음 줄):
    //       인건비             -> fluidized_contract_cost row="cost" col_index=0
    //       운영비             -> col_index=1
    //       설비개선비용        -> col_index=2
    // ========================================================================

    public Map<String, Object> importCompareWorkbook(MultipartFile file, int year) throws IOException {
        int noteRows = 0;
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 0, "실적비교");
            // 월별 특이사항: "월 / 특이사항" 머리글을 찾아, 그 아래 "N월" 줄에서 읽는다.
            // (예전에는 B10..B21 로 못 박아 둬서 줄을 하나만 지워도 달이 어긋났다)
            String anchorMonth = yearAnchorKey(year);
            int headerRow = FluidizedSummaryLayout.findRowByLabel(sheet, 0, 60, 1, "특이사항");
            if (headerRow >= 0) {
                Integer noteCol = FluidizedSummaryLayout.columnInRow(sheet, headerRow, "특이사항");
                Integer monthCol = FluidizedSummaryLayout.columnInRow(sheet, headerRow, "월");
                if (noteCol != null && monthCol != null) {
                    Map<Integer, Integer> monthRows =
                            FluidizedSummaryLayout.monthRowsBelow(sheet, headerRow, monthCol);
                    for (int month = 1; month <= 12; month += 1) {
                        Integer row = monthRows.get(month);
                        if (row == null) continue;
                        noteRows += upsertCell(
                                anchorMonth,
                                year,
                                1,
                                NOTE_TABLE,
                                "note-" + String.format("%02d", month),
                                0,
                                readCellValue(getCell(sheet, row, noteCol))
                        );
                    }
                }
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
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 0, "도급비용");
            // 내려받은 파일도 좌표가 아니라 머리글로 읽는다 — 줄·칸을 지운 채 올려도 밀리지 않는다.
            FluidizedSummaryLayout.Contract contract = FluidizedSummaryLayout.contractOf(sheet);

            for (Map.Entry<Integer, Integer> day : contract.dayRows().entrySet()) {
                if (day.getKey() > monthDays) continue;
                String rowKey = String.format("%02d", day.getKey());
                for (Map.Entry<String, Integer> field : CONTRACT_COL_INDEX.entrySet()) {
                    Integer column = contract.columns().get(field.getKey());
                    if (column == null) continue;
                    contractRows += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, field.getValue(),
                            readCellValue(getCell(sheet, day.getValue(), column)));
                }
            }
            // 지급현황: 인건비 / 운영비 / 설비개선비용
            if (contract.costRow() >= 0) {
                for (Map.Entry<String, Integer> field : COST_COL_INDEX.entrySet()) {
                    Integer column = contract.costColumns().get(field.getKey());
                    if (column == null) continue;
                    costRows += upsertCell(monthKey, year, month, COST_TABLE, "cost", field.getValue(),
                            readCellValue(getCell(sheet, contract.costRow(), column)));
                }
            }
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
            for (Map<String, Object> row : tableService.listAllByMonth("steam_cell_value", anchorMonth)) {
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
        Map<String, Object> monthlyCells = buildCellMap(tableService.listAllByMonth("steam_cell_value", monthKey));
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

            // 시트는 순번이 아니라 이름으로 찾는다 — 원본에 시트가 끼어들어도 엉뚱한 시트를 읽지 않는다.
            Sheet annualSheet = FluidizedSummaryLayout.sheetByName(workbook, 0, "1.");
            Sheet monthlySheet = FluidizedSummaryLayout.sheetByName(workbook, 1, "2.");
            Sheet detailSheet = FluidizedSummaryLayout.sheetByName(workbook, 2, "3. 세부");
            Sheet srfSheet = FluidizedSummaryLayout.sheetByName(workbook, 4, "4.");
            Sheet improvementSheet = resolveImprovementSheet(workbook, year);
            Sheet accidentSheet = FluidizedSummaryLayout.sheetByName(workbook, 7, "6.");
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
        // 업로드와 같은 색인을 쓴다 — 읽는 자리와 쓰는 자리가 어긋나지 않게.
        FluidizedSummaryLayout.Annual annual = FluidizedSummaryLayout.annualOf(sheet);

        for (int month = 1; month <= 12; month += 1) {
            String targetMonth = monthKey(year, month);
            Map<String, String> targetDetailCells = annualDetailCells.getOrDefault(targetMonth, Map.of());
            Integer planCol = annual.planCols().get(month);
            for (String rowKey : PLAN_ROW_KEYS) {
                Integer row = annual.planRows().get(rowKey);
                if (row == null || planCol == null) continue;
                Object plan = annualCells.get(cellKey(PLAN_TABLE, targetMonth, rowKey, 0));
                Object actual = calculatePlanActual(rowKey, targetDetailCells);
                setCellValue(sheet, row, planCol, plan);
                setCellValue(sheet, row, planCol + 1, actual);   // 계획 오른쪽이 실적
            }

            Integer noteRow = annual.noteRows().get(month);
            if (noteRow == null) continue;
            Object note = annualCells.get(cellKey(
                    NOTE_TABLE,
                    yearAnchorKey(year),
                    "note-" + String.format("%02d", month),
                    0
            ));
            setCellValue(sheet, noteRow, MONTHLY_NOTE_COL, note);
        }

        if (annual.summaryRow() >= 0 && annual.summaryCol() >= 0) {
            Object summaryNote = annualCells.get(cellKey(SUMMARY_TABLE, selectedMonthKey, "monthly-summary", 0));
            setCellValue(sheet, annual.summaryRow(), annual.summaryCol(), summaryNote);
        }
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
        // 업로드와 같은 색인을 쓴다 — 읽는 자리와 쓰는 자리가 어긋나지 않게.
        FluidizedSummaryLayout.Contract contract = FluidizedSummaryLayout.contractOf(sheet);

        for (Map.Entry<Integer, Integer> day : contract.dayRows().entrySet()) {
            for (Map.Entry<String, Integer> field : CONTRACT_COL_INDEX.entrySet()) {
                Integer column = contract.columns().get(field.getKey());
                if (column == null) continue;
                clearCell(sheet, day.getValue(), column);
                if (day.getKey() > monthDays) continue;
                String rowKey = String.format("%02d", day.getKey());
                setCellValue(sheet, day.getValue(), column,
                        monthlyCells.get(cellKey(CONTRACT_TABLE, monthKey, rowKey, field.getValue())));
            }
        }

        if (contract.costRow() < 0) return;
        for (String field : new String[]{"labor", "improvement"}) {
            Integer column = contract.costColumns().get(field);
            if (column == null) continue;
            setCellValue(sheet, contract.costRow(), column,
                    monthlyCells.get(cellKey(COST_TABLE, monthKey, "cost", COST_COL_INDEX.get(field))));
        }
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

    /** 내려받기도 업로드와 같은 색인을 거친다 — 읽는 자리와 쓰는 자리가 어긋나지 않게. */
    private void fillDetailSheet(Sheet sheet, Map<String, String> detailCells) {
        FluidizedDetailSheetIndex index = detailIndex(sheet);
        detailCells.forEach((cellRef, value) -> {
            org.apache.poi.ss.util.CellAddress address = new org.apache.poi.ss.util.CellAddress(cellRef);
            Integer sheetRow = index.rowFor(address.getRow() + 1);
            Integer sheetCol = index.columnFor(columnLabel(address.getColumn() + 1));
            if (sheetRow == null || sheetCol == null) return;
            setCellValue(sheet, sheetRow, sheetCol, value);
        });
    }

    /**
     * 추가 항목을 detail 시트의 insertAfter 위치 바로 아래에 inline 삽입.
     * row_key 패턴: custom|{section}|{rowId}|label / d{N} / insertAfter
     * 컬럼: B=항목명, C..AG=1..31일, AK=비용(합계). A 는 섹션 rowspan 으로 점유.
     * 업로드(import)는 main grid 의 cellRef 기반이라 inline 행은 자동 무시.
     */
    private void appendDetailCustomRows(Sheet sheet, int year, int month, String monthKey) {
        List<Map<String, Object>> rows = tableService.listAllByMonth("steam_cell_value", monthKey);
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
        FluidizedSummaryLayout.Annual annual = FluidizedSummaryLayout.annualOf(sheet);

        for (int month = 1; month <= 12; month += 1) {
            Integer planCol = annual.planCols().get(month);
            if (planCol == null) continue;
            String targetMonth = monthKey(year, month);
            for (String rowKey : PLAN_ROW_KEYS) {
                Integer row = annual.planRows().get(rowKey);
                if (row == null) continue;
                imported += upsertCell(targetMonth, year, month, PLAN_TABLE, rowKey, 0,
                        readCellValue(getCell(sheet, row, planCol)));
            }
        }
        return imported;
    }

    private int importNotes(Sheet sheet, int year, int month) {
        int imported = 0;
        String anchorMonth = yearAnchorKey(year);
        String selectedMonthKey = monthKey(year, month);
        FluidizedSummaryLayout.Annual annual = FluidizedSummaryLayout.annualOf(sheet);

        for (int noteMonth = 1; noteMonth <= 12; noteMonth += 1) {
            Integer row = annual.noteRows().get(noteMonth);
            if (row == null) continue;
            imported += upsertCell(
                    anchorMonth,
                    year,
                    1,
                    NOTE_TABLE,
                    "note-" + String.format("%02d", noteMonth),
                    0,
                    readCellValue(getCell(sheet, row, MONTHLY_NOTE_COL))
            );
        }

        if (annual.summaryRow() >= 0 && annual.summaryCol() >= 0) {
            imported += upsertCell(selectedMonthKey, year, month, SUMMARY_TABLE, "monthly-summary", 0,
                    readCellValue(getCell(sheet, annual.summaryRow(), annual.summaryCol())));
        }
        return imported;
    }

    private int importContractSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        FluidizedSummaryLayout.Contract contract = FluidizedSummaryLayout.contractOf(sheet);

        for (Map.Entry<Integer, Integer> day : contract.dayRows().entrySet()) {
            if (day.getKey() > monthDays) continue;
            String rowKey = String.format("%02d", day.getKey());
            for (Map.Entry<String, Integer> field : CONTRACT_COL_INDEX.entrySet()) {
                Integer column = contract.columns().get(field.getKey());
                if (column == null) continue;
                imported += upsertCell(monthKey, year, month, CONTRACT_TABLE, rowKey, field.getValue(),
                        readCellValue(getCell(sheet, day.getValue(), column)));
            }
        }
        return imported;
    }

    private int importCostSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        FluidizedSummaryLayout.Contract contract = FluidizedSummaryLayout.contractOf(sheet);
        if (contract.costRow() < 0) return imported;

        // 운영비(col_index 1)는 화면에서 계산하므로 예전처럼 읽지 않는다.
        for (String field : new String[]{"labor", "improvement"}) {
            Integer column = contract.costColumns().get(field);
            if (column == null) continue;
            imported += upsertCell(monthKey, year, month, COST_TABLE, "cost", COST_COL_INDEX.get(field),
                    readCellValue(getCell(sheet, contract.costRow(), column)));
        }
        return imported;
    }

    /**
     * 세부 운영내역을 읽는다.
     *
     * <p>DB 키는 서식 기준 셀 주소({@code "AK16"})를 그대로 쓰되, 올라온 파일에서 그 자리는
     * <b>항목 이름으로</b> 찾는다({@link FluidizedDetailSheetIndex}). 사용자가 줄을 지웠거나
     * 내려받기가 추가 항목을 끼워 넣은 파일을 다시 올려도 값이 밀리지 않는다.
     */
    private int importDetailSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        FluidizedDetailSheetIndex index = detailIndex(sheet);
        if (!index.usable()) return imported;

        if (index.rowFor(DETAIL_EXTRA_COST_ROW) == null) {
            deleteDetailExtraCost(monthKey);
        }

        for (int canonicalRow = 1; canonicalRow <= DETAIL_LAST_ROW; canonicalRow += 1) {
            Integer sheetRow = index.rowFor(canonicalRow);
            if (sheetRow == null) continue;
            for (int col = 1; col <= DETAIL_LAST_COL; col += 1) {
                String colLabel = columnLabel(col);
                if (!isEditableDetailCell(canonicalRow, colLabel)) continue;
                if (isDayColumn(colLabel) && dayNumber(colLabel) > monthDays) continue;
                Integer sheetCol = index.columnFor(colLabel);
                if (sheetCol == null) continue;
                String cellRef = colLabel + canonicalRow;
                String value = readCellValue(getCell(sheet, sheetRow, sheetCol));
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

    /**
     * 서식에는 있는 "추가비용" 줄이 빠진 파일이면 그 자리에 끼워 넣는다.
     *
     * <p>자리는 바로 윗줄("설비개선비용")을 이름으로 찾아 정한다.
     */
    private void ensureDetailExtraCostRow(Sheet sheet) {
        FluidizedDetailSheetIndex index = detailIndex(sheet);
        if (index.rowFor(DETAIL_EXTRA_COST_ROW) != null) return;
        Integer above = index.rowFor(DETAIL_EXTRA_COST_ROW - 1);
        if (above == null) return;

        int insertRowIndex = above + 1;
        int lastRow = Math.max(sheet.getLastRowNum(), insertRowIndex);
        sheet.shiftRows(insertRowIndex, lastRow, 1, true, false);
        Row row = sheet.getRow(insertRowIndex);
        if (row == null) row = sheet.createRow(insertRowIndex);
        setCellValue(sheet, insertRowIndex, 1, "추가비용");
        clearCell(sheet, insertRowIndex, 35);
        clearCell(sheet, insertRowIndex, 36);
    }

    private int importSrfInboundSheet(Sheet sheet, int year, int month) {
        int imported = 0;
        String monthKey = monthKey(year, month);
        int monthDays = YearMonth.of(year, month).lengthOfMonth();
        FluidizedSummaryLayout.SrfInbound srf = FluidizedSummaryLayout.srfInboundOf(sheet);
        List<Integer> vendorCols = srf.vendorCols();

        for (Map.Entry<Integer, Integer> day : srf.dayRows().entrySet()) {
            if (day.getKey() > monthDays) continue;
            String rowKey = "entry:" + String.format("%02d", day.getKey());
            for (int vendorIndex = 0; vendorIndex < vendorCols.size(); vendorIndex += 1) {
                imported += upsertCell(monthKey, year, month, SRF_INBOUND_TABLE, rowKey, vendorIndex + 1,
                        readCellValue(getCell(sheet, day.getValue(), vendorCols.get(vendorIndex))));
            }
        }
        if (srf.rateRow() >= 0) {
            for (int vendorIndex = 0; vendorIndex < vendorCols.size(); vendorIndex += 1) {
                imported += upsertCell(monthKey, year, month, SRF_INBOUND_TABLE, "rate", vendorIndex + 1,
                        readCellValue(getCell(sheet, srf.rateRow(), vendorCols.get(vendorIndex))));
            }
        }
        return imported;
    }

    private int importImprovementSheet(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = yearAnchorKey(year).replace("-01", "-00");

        // 좌표가 아니라 머리글로 칸을 찾는다 — 왼쪽은 "월/지급액/사용액/업체명/사용처",
        // 오른쪽은 "구 분/No./Item/계획비용/실 비용/완료여부". 줄 범위도 합계 줄 앞까지 스스로 잡는다.
        FluidizedSummaryLayout.LabeledTable monthly = FluidizedSummaryLayout.improvementMonthlyOf(sheet);
        String currentMonth = "";
        int monthlySort = 1;
        for (int row = monthly.firstRow(); monthly.usable() && row <= monthly.lastRow(); row += 1) {
            String monthValue = cellText(sheet, row, monthly, "month");
            if (!monthValue.isBlank()) currentMonth = normalizeMonthText(monthValue);
            String payment = cellText(sheet, row, monthly, "payment");
            String spent = cellText(sheet, row, monthly, "spent");
            String vendor = cellText(sheet, row, monthly, "vendor");
            String usage = cellText(sheet, row, monthly, "usage");
            if (currentMonth.isBlank() && payment.isBlank() && spent.isBlank() && vendor.isBlank() && usage.isBlank()) continue;
            String rowKey = IMPROVEMENT_MONTHLY_PREFIX + "xlsx-r" + (row + 1);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 1, currentMonth.isBlank() ? "01" : currentMonth);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 2, payment);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 3, spent);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 4, vendor);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 5, usage);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 6, String.valueOf(monthlySort++));
        }

        FluidizedSummaryLayout.LabeledTable items = FluidizedSummaryLayout.improvementItemOf(sheet);
        String currentCategory = "";
        int itemSort = 1;
        for (int row = items.firstRow(); items.usable() && row <= items.lastRow(); row += 1) {
            String category = cellText(sheet, row, items, "category");
            if (!category.isBlank()) currentCategory = normalizeImprovementCategory(category);
            String no = cellText(sheet, row, items, "no");
            String item = cellText(sheet, row, items, "item");
            String planCost = cellText(sheet, row, items, "planCost");
            String actualCost = cellText(sheet, row, items, "actualCost");
            String status = cellText(sheet, row, items, "status");
            String note = cellText(sheet, row, items, "note");
            if (currentCategory.isBlank() && no.isBlank() && item.isBlank() && planCost.isBlank() && actualCost.isBlank() && status.isBlank() && note.isBlank()) continue;
            if (isImprovementSummaryRow(currentCategory)) continue;
            String rowKey = IMPROVEMENT_ITEM_PREFIX + "xlsx-r" + (row + 1);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 1, currentCategory.isBlank() ? "설비개선" : currentCategory);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 2, no);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 3, item);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 4, planCost);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 5, actualCost);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 6, status);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 7, note);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 8, String.valueOf(itemSort++));
        }
        return imported;
    }

    /** 머리글로 찾은 칸의 글자. 그 머리글이 없으면 빈 글자를 준다. */
    private String cellText(Sheet sheet, int row, FluidizedSummaryLayout.LabeledTable table, String key) {
        Integer column = table.columns().get(key);
        return column == null ? "" : readCellValue(getCell(sheet, row, column));
    }

    private int importAccidentSheet(Sheet sheet, int year) {
        int imported = 0;
        String monthKey = yearAnchorKey(year).replace("-01", "-00");
        // "일 자 / 조치내역" 머리글이 있는 표를 위에서부터 찾는다 — 첫 표가 사고, 둘째가 정기 정비다.
        // (예전에는 5~11행 / 20~29행으로 못 박아 둬서 줄을 하나만 넣어도 어긋났다)
        List<FluidizedSummaryLayout.LabeledTable> blocks = FluidizedSummaryLayout.accidentBlocksOf(sheet);
        String[] kinds = {"accident", "maintenance"};
        String[] prefixes = {ACCIDENT_PREFIX, MAINTENANCE_PREFIX};
        for (int index = 0; index < blocks.size() && index < kinds.length; index += 1) {
            imported += importAccidentRows(sheet, year, monthKey, kinds[index], prefixes[index], blocks.get(index));
        }
        return imported;
    }

    /**
     * 사고·정비 이력 한 표를 읽는다.
     *
     * <p>정비 표에는 "손실금액" 칸이 없다 — 머리글로 찾으므로 없으면 저절로 비고만 담긴다.
     */
    private int importAccidentRows(Sheet sheet, int year, String monthKey, String kind, String prefix,
                                   FluidizedSummaryLayout.LabeledTable table) {
        if (!table.usable()) return 0;
        int imported = 0;
        int sortOrder = 1;
        for (int row = table.firstRow(); row <= table.lastRow(); row += 1) {
            Integer dateColumn = table.columns().get("date");
            String date = dateColumn == null ? "" : readDateCellValue(getCell(sheet, row, dateColumn));
            String reason = cellText(sheet, row, table, "reason");
            String action = cellText(sheet, row, table, "action");
            String downtime = cellText(sheet, row, table, "downtime");
            String loss = cellText(sheet, row, table, "loss");
            String note = cellText(sheet, row, table, "note");
            if (date.isBlank() && reason.isBlank() && action.isBlank() && downtime.isBlank() && loss.isBlank() && note.isBlank()) continue;
            String rowKey = prefix + "xlsx-r" + (row + 1);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 1, date);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 2, reason);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 3, action);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 4, downtime);
            if ("accident".equals(kind)) {
                imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 5, loss);
            }
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 6, note);
            imported += upsertCell(monthKey, year, 0, "steam_cell_value", rowKey, 7, String.valueOf(sortOrder++));
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
            rows.addAll(tableService.listAllByMonth("steam_cell_value", monthKey(year, month)));
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
        tableService.upsert("steam_cell_value", payload);
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
        // 작업 디렉터리에 기대지 않고 '원본' 폴더를 찾아 올라간다 (OriginalWorkbookFiles 주석 참고)
        Path resolved = OriginalWorkbookFiles.find(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return name.startsWith("2.")
                    && (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".xls"));
        }, "유동상 운영내역");
        cachedWorkbookPath = resolved;
        return resolved;
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
