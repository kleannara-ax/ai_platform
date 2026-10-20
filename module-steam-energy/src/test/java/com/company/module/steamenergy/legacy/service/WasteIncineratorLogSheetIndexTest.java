package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.Layout;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogCellMap.SheetMap;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogSheetIndex.Resolved;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 헤더 글자 기반 위치 해석({@link WasteIncineratorLogSheetIndex}) 검증.
 *
 * <p>확인하는 것은 두 가지다.
 * <ol>
 *   <li>원본 양식 그대로면 <b>매핑표 좌표와 똑같은 자리</b>를 찾아낸다 (기존 동작과 동일)</li>
 *   <li>행이 끼어들면 <b>좌표가 아니라 항목명을 따라</b> 움직인다 (예전엔 말없이 밀렸다)</li>
 * </ol>
 */
class WasteIncineratorLogSheetIndexTest {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private static WasteIncineratorLogSheetIndex indexOf(Sheet sheet, SheetMap sheetMap) {
        return WasteIncineratorLogSheetIndex.build(sheet, sheetMap, (row, col) -> {
            if (row < 1 || col < 1) return "";
            Row r = sheet.getRow(row - 1);
            if (r == null) return "";
            Cell cell = r.getCell(col - 1);
            return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
        });
    }

    @Test
    @DisplayName("원본 양식에서는 매핑표 좌표와 같은 자리를 찾는다")
    void resolvesSamePositionsOnOriginalTemplate() throws Exception {
        Path template = findTemplate();
        Assumptions.assumeTrue(template != null, "원본 운전일지 파일이 없어 건너뜀");

        List<String> problems = new ArrayList<>();
        try (InputStream in = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {

            for (SheetMap sheetMap : WasteIncineratorLogCellMap.SHEETS) {
                Sheet sheet = workbook.getSheet(sheetMap.sheetName());
                if (sheet == null) continue;

                WasteIncineratorLogSheetIndex index = indexOf(sheet, sheetMap);
                assertTrue(index.hasDayHeader(), sheetMap.sheetName() + ": 일자 헤더를 못 읽었다");

                // 일자 칸이 매핑표의 "시작 + (일-1)" 과 같아야 한다
                for (int day = 1; day <= 31; day += 1) {
                    int expected = sheetMap.layout() == Layout.DAYS_IN_COLUMNS
                            ? sheetMap.firstDayCol() + day - 1
                            : sheetMap.firstDayRow() + day - 1;
                    if (index.dayLine(day) != expected) {
                        problems.add(sheetMap.sheetName() + " " + day + "일: " + expected + " → " + index.dayLine(day));
                    }
                }

                // 항목 줄이 매핑표 좌표와 같아야 하고, 헤더가 있는 항목은 헤더로 찾아야 한다
                for (Entry entry : sheetMap.entries()) {
                    Resolved resolved = index.resolve(entry);
                    int declared = sheetMap.layout() == Layout.DAYS_IN_COLUMNS
                            ? WasteIncineratorLogCellMap.parseRow(entry.line())
                            : WasteIncineratorLogCellMap.parseCol(entry.line());
                    if (resolved.line() != declared) {
                        problems.add(sheetMap.sheetName() + " " + entry.label()
                                + ": 좌표 " + declared + " ≠ 해석 " + resolved.line());
                    }
                    if (entry.header() != null && !resolved.byHeader()) {
                        problems.add(sheetMap.sheetName() + " " + entry.label() + ": 항목명을 못 찾음");
                    }
                }
            }
        }
        assertEquals(List.of(), problems, "원본 양식 해석 결과가 매핑표와 달랐다");
    }

    @Test
    @DisplayName("행이 끼어들면 좌표가 아니라 항목명을 따라간다")
    void followsLabelWhenRowInserted() throws Exception {
        SheetMap sheetMap = WasteIncineratorLogCellMap.SHEETS.stream()
                .filter(s -> s.sheetName().equals("소각량"))
                .findFirst().orElseThrow();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("소각량");
            // 원본과 같은 배치: 3행 = 일자 헤더, A = 상위구분, B = 항목, C 부터 일자
            write(sheet, 3, 1, "구분");
            for (int day = 1; day <= 31; day += 1) write(sheet, 3, 2 + day, String.valueOf(day));
            write(sheet, 4, 1, "폐1");
            write(sheet, 4, 2, "자폐");
            write(sheet, 5, 2, "재활용");

            // 원래 자리에서는 좌표와 같아야 한다
            Entry jape = sheetMap.entries().get(0);          // 폐1 자폐 = 4행
            assertEquals(4, indexOf(sheet, sheetMap).resolve(jape).line());

            // 항목 위에 행이 하나 끼어들면(설명 행 추가 등) 한 칸씩 밀린다
            sheet.shiftRows(3, sheet.getLastRowNum(), 1);    // 4행부터 아래로 한 칸
            write(sheet, 4, 1, "※ 비고");                     // 끼어든 행

            WasteIncineratorLogSheetIndex shifted = indexOf(sheet, sheetMap);
            Resolved resolved = shifted.resolve(jape);

            assertEquals(5, resolved.line(), "항목명을 따라 5행을 찾아야 한다");
            assertTrue(resolved.byHeader(), "좌표 폴백이 아니라 항목명으로 찾아야 한다");
            assertTrue(resolved.moved(), "양식이 바뀌었다고 표시해야 한다");
            assertTrue(shifted.moved().stream().anyMatch(m -> m.contains("폐1 자폐")),
                    "바뀐 항목을 경고 목록에 남겨야 한다");
        }
    }

    @Test
    @DisplayName("라벨 칸이 없는 항목은 좌표로 폴백한다")
    void fallsBackToCoordinateWhenNoHeader() {
        SheetMap steam = WasteIncineratorLogCellMap.SHEETS.stream()
                .filter(s -> s.sheetName().equals("스팀량"))
                .findFirst().orElseThrow();
        Entry lowHigh = steam.entries().stream()
                .filter(e -> e.label().equals("저/고"))
                .findFirst().orElseThrow();

        assertEquals(null, lowHigh.header(), "저/고 행은 시트에 라벨이 없어 헤더가 없어야 한다");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("스팀량");
            write(sheet, 3, 1, "구분");
            Resolved resolved = indexOf(sheet, steam).resolve(lowHigh);
            assertEquals(10, resolved.line(), "매핑표 좌표(10행)를 써야 한다");
            assertTrue(!resolved.byHeader());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void write(Sheet sheet, int rowNumber, int colNumber, String value) {
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) row = sheet.createRow(rowNumber - 1);
        Cell cell = row.getCell(colNumber - 1);
        if (cell == null) cell = row.createCell(colNumber - 1);
        cell.setCellValue(value);
    }

    /** 저장소의 {@code 원본} 폴더에서 운전일지 파일을 찾는다. 없으면 null. */
    private static Path findTemplate() throws Exception {
        Path dir = Path.of("원본");                                  // 모듈 폴더에서 실행할 때
        if (!Files.isDirectory(dir)) dir = Path.of("..", "module-steam-energy", "원본");
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().contains("폐합성소각로운전일지"))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
