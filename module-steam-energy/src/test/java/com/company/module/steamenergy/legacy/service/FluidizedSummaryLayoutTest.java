package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 유동상 운영내역 워크북의 시트 이름·머리글 기반 찾기 검증.
 *
 * <p>예전에는 시트를 순번({@code getSheetAt(0/1/4)})으로, 칸을 좌표(5~8행, C열, 43행)로 잡았다.
 * 이름·머리글로 찾은 결과가 그 번호와 같은지 확인한다. 모든 좌표는 0-based 다.
 */
class FluidizedSummaryLayoutTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("시트를 순번이 아니라 이름 앞머리로 찾는다")
    void resolvesSheetsByName() throws Exception {
        withWorkbook(workbook -> {
            assertEquals("1. 운영실적", FluidizedSummaryLayout.sheetByName(workbook, 0, "1.").getSheetName());
            assertEquals("2. 운영실적", FluidizedSummaryLayout.sheetByName(workbook, 1, "2.").getSheetName());
            assertEquals("3. 세부 운영내역", FluidizedSummaryLayout.sheetByName(workbook, 2, "3. 세부").getSheetName());
            assertEquals("3-1. 월별 운영내역", FluidizedSummaryLayout.sheetByName(workbook, 3, "3-1.").getSheetName());
            assertEquals("4. SRF입고내역", FluidizedSummaryLayout.sheetByName(workbook, 4, "4.").getSheetName());
            assertEquals("6. 25년 사고이력", FluidizedSummaryLayout.sheetByName(workbook, 7, "6.").getSheetName());
        });
    }

    @Test
    @DisplayName("'1. 운영실적' 의 사업계획·특이사항이 기존 좌표와 같다")
    void resolvesAnnualSheet() throws Exception {
        withWorkbook(workbook -> {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 0, "1.");
            FluidizedSummaryLayout.Annual annual = FluidizedSummaryLayout.annualOf(sheet);

            Map<String, Integer> planRows = new LinkedHashMap<>();
            planRows.put("operating", 4);     // 5행
            planRows.put("power", 5);         // 6행
            planRows.put("waste", 6);         // 7행
            planRows.put("srf_income", 7);    // 8행
            assertEquals(planRows, annual.planRows(), "사업계획 행이 다르다");

            // 예전 MONTH_PLAN_COLS: 1월 계획 = B, 이후 세 칸씩
            for (int month = 1; month <= 12; month += 1) {
                assertEquals(1 + 3 * (month - 1), annual.planCols().get(month),
                        month + "월 계획 열이 다르다");
            }

            // 예전 MONTHLY_NOTE_START_ROW = 23행(0-based 22)
            for (int month = 1; month <= 12; month += 1) {
                assertEquals(21 + month, annual.noteRows().get(month), month + "월 특이사항 줄이 다르다");
            }

            assertEquals(13, annual.summaryRow(), "이달 총평 줄이 다르다");   // 14행
            assertEquals(10, annual.summaryCol(), "이달 총평 열이 다르다");   // K
        });
    }

    @Test
    @DisplayName("'2. 운영실적' 의 일별 도급비용·지급현황이 기존 좌표와 같다")
    void resolvesContractSheet() throws Exception {
        withWorkbook(workbook -> {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 1, "2.");
            FluidizedSummaryLayout.Contract contract = FluidizedSummaryLayout.contractOf(sheet);

            Map<String, Integer> columns = new LinkedHashMap<>();
            columns.put("steam", 2);       // C
            columns.put("price", 4);       // E
            columns.put("stopHours", 6);   // G
            columns.put("fixedCost", 7);   // H
            assertEquals(columns, contract.columns(), "일별 표 열이 다르다");

            for (int day = 1; day <= 31; day += 1) {
                assertEquals(day + 5, contract.dayRows().get(day), day + "일 줄이 다르다");  // 7행부터
            }

            assertEquals(42, contract.costRow(), "지급현황 줄이 다르다");   // 43행
            Map<String, Integer> costColumns = new LinkedHashMap<>();
            costColumns.put("labor", 4);         // E
            costColumns.put("operating", 5);     // F
            costColumns.put("improvement", 6);   // G
            assertEquals(costColumns, contract.costColumns(), "지급현황 열이 다르다");
        });
    }

    @Test
    @DisplayName("'4. SRF입고내역' 의 업체 열·일자 줄이 기존 좌표와 같다")
    void resolvesSrfSheet() throws Exception {
        withWorkbook(workbook -> {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 4, "4.");
            FluidizedSummaryLayout.SrfInbound srf = FluidizedSummaryLayout.srfInboundOf(sheet);

            assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9, 10, 11), srf.vendorCols(), "업체 열이 다르다");  // C~L
            assertEquals(36, srf.rateRow(), "처리단가 줄이 다르다");   // 37행
            for (int day = 1; day <= 31; day += 1) {
                assertEquals(day + 3, srf.dayRows().get(day), day + "일 줄이 다르다");   // 5행부터
            }
        });
    }

    @Test
    @DisplayName("'5. …설비개선리스트' 의 좌우 두 표가 기존 좌표와 같다")
    void resolvesImprovementSheet() throws Exception {
        withWorkbook(workbook -> {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 6, "5. 25년", "5. ");

            FluidizedSummaryLayout.LabeledTable monthly = FluidizedSummaryLayout.improvementMonthlyOf(sheet);
            Map<String, Integer> monthlyColumns = new LinkedHashMap<>();
            monthlyColumns.put("month", 1);      // B
            monthlyColumns.put("payment", 2);    // C
            monthlyColumns.put("spent", 3);      // D
            monthlyColumns.put("vendor", 5);     // F
            monthlyColumns.put("usage", 6);      // G
            assertEquals(monthlyColumns, monthly.columns(), "월별 집행 열이 다르다");
            assertEquals(5, monthly.firstRow(), "월별 집행 첫 줄이 다르다");   // 6행
            assertEquals(24, monthly.lastRow(), "월별 집행 끝 줄이 다르다");   // 25행("총 액" 앞)

            FluidizedSummaryLayout.LabeledTable items = FluidizedSummaryLayout.improvementItemOf(sheet);
            Map<String, Integer> itemColumns = new LinkedHashMap<>();
            itemColumns.put("category", 8);      // I
            itemColumns.put("no", 9);            // J
            itemColumns.put("item", 10);         // K
            itemColumns.put("planCost", 11);     // L
            itemColumns.put("actualCost", 12);   // M
            itemColumns.put("status", 13);       // N
            itemColumns.put("note", 14);         // O — 머리글이 없어 완료여부 오른쪽
            assertEquals(itemColumns, items.columns(), "개선 항목 열이 다르다");
            assertEquals(5, items.firstRow(), "개선 항목 첫 줄이 다르다");     // 6행
            assertEquals(25, items.lastRow(), "개선 항목 끝 줄이 다르다");     // 26행("추가건수" 앞)
        });
    }

    @Test
    @DisplayName("'6. …사고이력' 의 사고·정비 두 표가 기존 좌표와 같다")
    void resolvesAccidentSheet() throws Exception {
        withWorkbook(workbook -> {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 7, "6.");
            List<FluidizedSummaryLayout.LabeledTable> blocks = FluidizedSummaryLayout.accidentBlocksOf(sheet);
            assertEquals(2, blocks.size(), "사고/정비 표를 둘 다 찾지 못했다");

            FluidizedSummaryLayout.LabeledTable accident = blocks.get(0);
            Map<String, Integer> accidentColumns = new LinkedHashMap<>();
            accidentColumns.put("date", 2);       // C
            accidentColumns.put("reason", 3);     // D
            accidentColumns.put("action", 4);     // E
            accidentColumns.put("downtime", 5);   // F
            accidentColumns.put("loss", 6);       // G
            accidentColumns.put("note", 7);       // H
            assertEquals(accidentColumns, accident.columns(), "사고 이력 열이 다르다");
            assertEquals(4, accident.firstRow(), "사고 이력 첫 줄이 다르다");   // 5행
            assertEquals(10, accident.lastRow(), "사고 이력 끝 줄이 다르다");   // 11행("발생횟수" 앞)

            FluidizedSummaryLayout.LabeledTable maintenance = blocks.get(1);
            Map<String, Integer> maintenanceColumns = new LinkedHashMap<>();
            maintenanceColumns.put("date", 2);       // C
            maintenanceColumns.put("reason", 3);     // D
            maintenanceColumns.put("action", 4);     // E
            maintenanceColumns.put("downtime", 5);   // F
            maintenanceColumns.put("note", 6);       // G — 정비 표에는 손실금액 칸이 없다
            assertEquals(maintenanceColumns, maintenance.columns(), "정비 이력 열이 다르다");
            assertEquals(19, maintenance.firstRow(), "정비 이력 첫 줄이 다르다");   // 20행
            assertEquals(28, maintenance.lastRow(), "정비 이력 끝 줄이 다르다");    // 29행("정비횟수" 앞)
        });
    }

    @Test
    @DisplayName("시트 이름을 못 찾으면 예전 순번으로 돌아간다")
    void fallsBackToSheetIndex() throws Exception {
        withWorkbook(workbook -> {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 2, "없는이름");
            assertNotNull(sheet, "폴백 시트를 찾지 못했다");
            assertEquals(workbook.getSheetAt(2).getSheetName(), sheet.getSheetName(), "폴백 순번이 다르다");
        });
    }

    @Test
    @DisplayName("내려받은 도급비용 파일에서 줄을 지워도 일자가 밀리지 않는다")
    void resolvesDownloadedContractFileAfterDeletedRow() {
        Sheet sheet = downloadedContractSheet();
        FluidizedSummaryLayout.Contract before = FluidizedSummaryLayout.contractOf(sheet);

        Map<String, Integer> columns = new LinkedHashMap<>();
        columns.put("steam", 1);       // B
        columns.put("price", 3);       // D
        columns.put("stopHours", 5);   // F
        columns.put("fixedCost", 6);   // G
        assertEquals(columns, before.columns(), "내려받은 파일의 열이 다르다");
        assertEquals(6, before.dayRows().get(1), "1일 줄이 다르다");
        assertEquals(36, before.dayRows().get(31), "31일 줄이 다르다");
        assertEquals(42, before.costRow(), "지급현황 줄이 다르다");

        // 사용자가 10일 줄을 통째로 지운 상황
        sheet.removeRow(sheet.getRow(15));
        sheet.shiftRows(16, sheet.getLastRowNum(), -1);

        FluidizedSummaryLayout.Contract after = FluidizedSummaryLayout.contractOf(sheet);
        assertEquals(columns, after.columns(), "줄을 지운 뒤 열이 달라졌다");
        assertEquals(6, after.dayRows().get(1), "줄을 지운 뒤 1일이 움직였다");
        assertEquals(15, after.dayRows().get(11), "줄을 지운 뒤 11일이 따라오지 않았다");
        assertEquals(35, after.dayRows().get(31), "줄을 지운 뒤 31일이 따라오지 않았다");
        assertEquals(41, after.costRow(), "줄을 지운 뒤 지급현황이 따라오지 않았다");
        assertEquals(null, after.dayRows().get(10), "지워진 10일은 건너뛰어야 한다");
    }

    @Test
    @DisplayName("내려받은 실적비교 파일의 월별 특이사항 줄을 머리글로 찾는다")
    void resolvesDownloadedCompareFile() {
        Sheet sheet = new XSSFWorkbook().createSheet("실적비교");
        set(sheet, 8, 0, "월");
        set(sheet, 8, 1, "특이사항");
        for (int month = 1; month <= 12; month += 1) {
            set(sheet, 8 + month, 0, month + "월");
        }

        int headerRow = FluidizedSummaryLayout.findRowByLabel(sheet, 0, 60, 1, "특이사항");
        assertEquals(8, headerRow, "머리글 줄이 다르다");
        assertEquals(1, FluidizedSummaryLayout.columnInRow(sheet, headerRow, "특이사항"), "특이사항 열이 다르다");

        Map<Integer, Integer> rows = FluidizedSummaryLayout.monthRowsBelow(sheet, headerRow, 0);
        for (int month = 1; month <= 12; month += 1) {
            assertEquals(8 + month, rows.get(month), month + "월 줄이 다르다");
        }
    }

    /** {@code exportContractWorkbook} 이 만드는 것과 같은 모양의 시트. */
    private static Sheet downloadedContractSheet() {
        Sheet sheet = new XSSFWorkbook().createSheet("도급비용");
        set(sheet, 0, 0, "2026년 7월 소각로 도급비용");
        set(sheet, 2, 0, "일별");
        set(sheet, 2, 1, "유동상 소각로");
        set(sheet, 2, 8, "총 운영비용(원)");
        set(sheet, 3, 1, "스팀 구매량 / 운휴시간");
        set(sheet, 4, 1, "스팀 생산량");
        set(sheet, 4, 3, "스팀단가(원/톤)");
        set(sheet, 4, 4, "운영비용(원)");
        set(sheet, 4, 5, "운휴시간(Hr)");
        set(sheet, 4, 6, "운휴시간당 고정비(원/시간)");
        set(sheet, 4, 7, "운영비용(원)");
        set(sheet, 5, 1, "(T/D)");
        set(sheet, 5, 2, "(T/hr)");
        for (int day = 1; day <= 31; day += 1) set(sheet, 5 + day, 0, day + "일");
        set(sheet, 37, 0, "합 계");
        set(sheet, 38, 0, "평 균");
        set(sheet, 40, 0, "도급 비용 지급현황 [단위 : 원]");
        set(sheet, 41, 0, "설비명");
        set(sheet, 41, 1, "인건비");
        set(sheet, 41, 2, "운영비");
        set(sheet, 41, 3, "설비개선비용");
        set(sheet, 41, 4, "총 지급비용");
        set(sheet, 42, 0, "유동상소각로");
        return sheet;
    }

    private static void set(Sheet sheet, int rowIndex, int colIndex, String value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        row.createCell(colIndex, CellType.STRING).setCellValue(value);
    }

    private interface WorkbookCheck {
        void run(Workbook workbook);
    }

    private static void withWorkbook(WorkbookCheck check) throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "유동상 운영내역 원본이 없어 건너뜀");
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {
            check.run(workbook);
        }
    }

    private static Path findWorkbook() throws Exception {
        Path dir = Path.of("원본");
        if (!Files.isDirectory(dir)) dir = Path.of("..", "원본");
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("2."))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
