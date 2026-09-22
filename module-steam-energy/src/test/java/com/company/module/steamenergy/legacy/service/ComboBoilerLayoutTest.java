package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 복합보일러 "스팀생산실적" 시트의 머리글 기반 열/행 찾기 검증.
 *
 * <p>예전에는 열을 {@code comboSteam=B, kn20=Q, runtimeMin=AM} 처럼, 행을
 * {@code 7행=1일, 42행=LNG 연료단가, 58행=회수량 첫 줄} 처럼 코드에 박아 뒀다.
 * 머리글로 찾은 결과가 그 번호와 같은지 확인한다.
 *
 * <p>월 시트마다 표기가 달라서("디스파샤"↔"디스퍼져", "시간"↔"가동시간(분)")
 * <b>모든 월 시트</b>에서 같은 자리가 나오는지도 본다.
 */
class ComboBoilerLayoutTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    /** A=0 … AM=38 기준의 기대 열. */
    private static Map<String, Integer> expectedDayColumns() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("comboSteam", 1);            // B
        expected.put("comboCondensate", 3);       // D
        expected.put("comboMakeup", 4);           // E
        expected.put("comboDeaerator", 5);        // F
        expected.put("comboProcess", 6);          // G
        expected.put("comboDilution", 7);         // H
        expected.put("externalSteam", 8);         // I
        expected.put("externalCondensate", 10);   // K
        expected.put("externalMakeup", 11);       // L
        expected.put("wasteSteam1", 12);          // M
        expected.put("wasteSteam2", 14);          // O
        expected.put("kn20", 16);                 // Q
        expected.put("kn50", 17);                 // R
        expected.put("fluidizedSteam", 18);       // S
        expected.put("fluidizedCondensate", 19);  // T
        expected.put("runtime20", 22);            // W
        expected.put("runtime50", 23);            // X
        expected.put("runtimeFluidized", 24);     // Y
        expected.put("changeCombo", 26);          // AA
        expected.put("changeJesco", 30);          // AE
        expected.put("down1", 34);                // AI
        expected.put("down2", 35);                // AJ
        expected.put("disperserSteam", 36);       // AK
        expected.put("runtimeMin", 38);           // AM
        return expected;
    }

    @Test
    @DisplayName("원본 복합보일러에서 머리글로 찾은 일별 표의 열·행이 기존 번호와 같다")
    void resolvesDayTable() throws Exception {
        forEachMonthSheet((name, sheet) -> {
            ComboBoilerLayout layout = ComboBoilerLayout.of(sheet);

            assertEquals(expectedDayColumns(), layout.dayColumns(), name + " 일별 표 열이 다르다");
            for (int day = 1; day <= 31; day += 1) {
                assertEquals(day + 6, layout.dayRows().get(day), name + " " + day + "일 행이 다르다");
            }
        });
    }

    @Test
    @DisplayName("연료단가·벤트 손실·스팀 회수량 표가 기존 좌표와 같다")
    void resolvesSideTables() throws Exception {
        forEachMonthSheet((name, sheet) -> {
            ComboBoilerLayout layout = ComboBoilerLayout.of(sheet);

            assertEquals(5, layout.fuelPriceColumn(), name + " 연료단가 열이 다르다");
            Map<String, Integer> fuel = new LinkedHashMap<>();
            fuel.put("fuelPrice-lngBoiler", 42);
            fuel.put("fuelPrice-fluidized", 43);
            fuel.put("fuelPrice-combo", 44);
            fuel.put("fuelPrice-waste", 45);
            fuel.put("fuelPrice-external", 46);
            assertEquals(fuel, layout.fuelPriceRows(), name + " 연료단가 행이 다르다");

            Map<String, Integer> vent = new LinkedHashMap<>();
            vent.put("ventProduction", 50);
            vent.put("ventInternal", 51);
            vent.put("ventUsage", 52);
            vent.put("ventLossUnitPrice", 54);
            assertEquals(vent, layout.ventRows(), name + " 벤트 손실 행이 다르다");
            assertEquals(3, layout.ventUnitPriceColumn(), name + " 벤트 단가 열이 다르다");
            for (int day = 1; day <= 31; day += 1) {
                assertEquals(day + 3, layout.ventDayColumns().get(day), name + " 벤트 " + day + "일 열이 다르다");
            }

            assertEquals(58, layout.recoveryFirstRow(), name + " 회수량 첫 줄이 다르다");
            Map<String, Integer> recovery = new LinkedHashMap<>();
            recovery.put("lossDate", 0);        // A
            recovery.put("lossTime", 1);        // B
            recovery.put("lossNote", 3);        // D
            recovery.put("lossSteamRate", 9);   // J
            recovery.put("lossBaseRate", 10);   // K
            recovery.put("lossMinutes", 11);    // L
            assertEquals(recovery, layout.recoveryColumns(), name + " 회수량 열이 다르다");
        });
    }

    @Test
    @DisplayName("연간 계획 표(복합보일러·제스코·LNG)가 기존 좌표와 같다")
    void resolvesPlanBlocks() throws Exception {
        forEachMonthSheet((name, sheet) -> {
            ComboBoilerLayout layout = ComboBoilerLayout.of(sheet);

            ComboBoilerLayout.PlanBlock combo = layout.planBlock("combo");
            assertEquals(18, combo.firstMonthCol(), name + " 복합 계획 첫 월 열이 다르다");   // S
            assertEquals(32, combo.lastMonthCol(), name + " 복합 계획 끝 월 열이 다르다");    // AG
            Map<String, Integer> comboRows = new LinkedHashMap<>();
            comboRows.put("combo-plan-1-", 58);
            comboRows.put("combo-plan-2-", 59);
            comboRows.put("combo-plan-3-", 60);
            comboRows.put("combo-plan-4-", 61);
            comboRows.put("combo-plan-5-", 62);
            assertEquals(comboRows, combo.rows(), name + " 복합 계획 행이 다르다");

            ComboBoilerLayout.PlanBlock jesco = layout.planBlock("jesco");
            assertEquals(17, jesco.firstMonthCol(), name + " 제스코 첫 월 열이 다르다");      // R
            assertEquals(29, jesco.lastMonthCol(), name + " 제스코 끝 월 열이 다르다");       // AD
            Map<String, Integer> jescoRows = new LinkedHashMap<>();
            jescoRows.put("jesco-plan-0-", 67);
            jescoRows.put("jesco-plan-1-", 68);
            jescoRows.put("jesco-plan-2-", 69);
            assertEquals(jescoRows, jesco.rows(), name + " 제스코 행이 다르다");

            ComboBoilerLayout.PlanBlock lng = layout.planBlock("lng");
            assertEquals(17, lng.firstMonthCol(), name + " LNG 첫 월 열이 다르다");           // R
            assertEquals(28, lng.lastMonthCol(), name + " LNG 끝 월 열이 다르다");            // AC
            Map<String, Integer> lngRows = new LinkedHashMap<>();
            lngRows.put("lngBoiler-", 73);
            lngRows.put("lngCombo-", 74);
            assertEquals(lngRows, lng.rows(), name + " LNG 행이 다르다");
        });
    }

    @Test
    @DisplayName("원본에서 열을 하나 지워도 항목이 밀리지 않는다")
    void survivesDeletedColumn() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "복합보일러 원본이 없어 건너뜀");

        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {

            // 원본 시트에는 배열 수식이 있어 칸을 지울 수 없다 — 글자만 옮겨 담은 사본을 쓴다.
            Sheet sheet = textCopyOf(workbook.getSheetAt(0));
            Map<String, Integer> before = new LinkedHashMap<>(ComboBoilerLayout.of(sheet).dayColumns());

            // "복합보일러 응축수판매량"(D) 을 지운 것처럼 D 오른쪽을 한 칸씩 당긴다.
            deleteColumn(sheet, 3);

            Map<String, Integer> after = ComboBoilerLayout.of(sheet).dayColumns();
            assertEquals(before.get("comboMakeup") - 1, after.get("comboMakeup"),
                    "열을 지운 뒤 보충수판매량이 따라 움직이지 않았다");
            assertEquals(before.get("kn20") - 1, after.get("kn20"),
                    "열을 지운 뒤 깨끗한나라 20톤이 따라 움직이지 않았다");
            assertEquals(before.get("runtimeMin") - 1, after.get("runtimeMin"),
                    "열을 지운 뒤 가동시간이 따라 움직이지 않았다");
            assertTrue(after.get("comboCondensate") == null,
                    "지워진 응축수판매량은 건너뛰어야 한다");
        }
    }

    /** 보이는 글자만 옮겨 담은 사본. 배열 수식이 걸린 원본과 달리 칸을 마음대로 고칠 수 있다. */
    private static Sheet textCopyOf(Sheet source) {
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        Sheet copy = new XSSFWorkbook().createSheet(source.getSheetName());
        for (int r = source.getFirstRowNum(); r <= source.getLastRowNum(); r += 1) {
            Row sourceRow = source.getRow(r);
            if (sourceRow == null) continue;
            Row copyRow = copy.createRow(r);
            for (int c = 0; c < sourceRow.getLastCellNum(); c += 1) {
                Cell cell = sourceRow.getCell(c);
                if (cell == null) continue;
                String value = formatter.formatCellValue(cell).trim();
                if (value.isEmpty()) continue;
                copyRow.createCell(c, CellType.STRING).setCellValue(value);
            }
        }
        return copy;
    }

    /** POI 에는 열 삭제가 없다 — 오른쪽 칸을 한 칸씩 왼쪽으로 옮겨 흉내 낸다. */
    private static void deleteColumn(Sheet sheet, int column) {
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int last = row.getLastCellNum();
            List<String> shifted = new ArrayList<>();
            for (int c = column + 1; c <= last; c += 1) {
                Cell source = row.getCell(c);
                shifted.add(source == null ? "" : source.getStringCellValue());
            }
            for (int c = column; c < last; c += 1) {
                Cell target = row.getCell(c);
                if (target != null) row.removeCell(target);
                String value = shifted.get(c - column);
                if (value.isEmpty()) continue;
                row.createCell(c, CellType.STRING).setCellValue(value);
            }
        }
    }

    private interface SheetCheck {
        void run(String sheetName, Sheet sheet);
    }

    /** 월 시트 12장 + 양식 시트를 모두 본다. */
    private static void forEachMonthSheet(SheetCheck check) throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "복합보일러 원본이 없어 건너뜀");

        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {
            int checked = 0;
            for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
                Sheet sheet = workbook.getSheetAt(i);
                String name = sheet.getSheetName();
                if (!name.startsWith("스팀생산실적")) continue;
                check.run(name, sheet);
                checked += 1;
            }
            assertTrue(checked >= 12, "스팀생산실적 시트를 " + checked + "장밖에 못 봤다");
        }
    }

    private static Path findWorkbook() throws Exception {
        Path dir = Path.of("원본");
        if (!Files.isDirectory(dir)) dir = Path.of("..", "원본");
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("3."))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
