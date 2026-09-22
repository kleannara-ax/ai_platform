package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.PowerUsage;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogSheetIndex.Resolved;
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
 * 유동상 운전일지의 헤더 기반 칸 찾기({@link FluidizedDailyLogSheetIndex}) 검증.
 *
 * <p>이 시트는 양식(form)이라 칸이 (섹션, 행 라벨, 열 라벨)로 정해진다.
 * 확인하는 것은 폐합성과 같다 — 원본 그대로면 매핑표 좌표와 같은 자리를 찾고,
 * 행이 끼어들면 좌표가 아니라 라벨을 따라간다.
 */
class FluidizedDailyLogSheetIndexTest {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private static FluidizedDailyLogSheetIndex indexOf(Sheet sheet) {
        return FluidizedDailyLogSheetIndex.build(sheet, (row, col) -> {
            if (row < 1 || col < 1) return "";
            Row r = sheet.getRow(row - 1);
            if (r == null) return "";
            Cell cell = r.getCell(col - 1);
            return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
        });
    }

    @Test
    @DisplayName("원본 양식에서는 매핑표 좌표와 같은 칸을 찾는다")
    void resolvesSameCellsOnOriginalTemplate() throws Exception {
        Path template = findTemplate();
        Assumptions.assumeTrue(template != null, "원본 유동상 운전일지 파일이 없어 건너뜀");

        List<String> problems = new ArrayList<>();
        try (InputStream in = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet("1");
            Assumptions.assumeTrue(sheet != null, "1일 시트가 없어 건너뜀");

            FluidizedDailyLogSheetIndex index = indexOf(sheet);
            assertTrue(index.hasSections(), "섹션 제목을 하나도 못 읽었다");

            for (Entry entry : FluidizedDailyLogCellMap.DAY_CELLS) {
                Resolved resolved = index.resolve(entry);
                int declaredRow = FluidizedDailyLogCellMap.parseRow(entry.ref());
                int declaredCol = FluidizedDailyLogCellMap.parseCol(entry.ref());

                if (resolved.row() != declaredRow || resolved.col() != declaredCol) {
                    problems.add(entry.label() + ": 좌표 " + entry.ref()
                            + " ≠ 해석 (" + resolved.row() + "," + resolved.col() + ")");
                }
                if (entry.header() != null && !resolved.byHeader()) {
                    problems.add(entry.label() + ": 항목명을 못 찾음");
                }
            }

            // 전력 사용량(전일/금일 지침)도 같은 색인으로 찾는다
            for (PowerUsage usage : FluidizedDailyLogCellMap.POWER_USAGE) {
                Resolved prev = index.resolve(FluidizedDailyLogCellMap.POWER_SECTION, usage.rowLabel(),
                        FluidizedDailyLogCellMap.POWER_PREV_COL, usage.prevRef(), usage.label());
                Resolved today = index.resolve(FluidizedDailyLogCellMap.POWER_SECTION, usage.rowLabel(),
                        FluidizedDailyLogCellMap.POWER_TODAY_COL, usage.todayRef(), usage.label());
                if (!prev.byHeader() || prev.row() != FluidizedDailyLogCellMap.parseRow(usage.prevRef())) {
                    problems.add(usage.label() + " 전일지침: " + usage.prevRef() + " 해석 실패");
                }
                if (!today.byHeader() || today.row() != FluidizedDailyLogCellMap.parseRow(usage.todayRef())) {
                    problems.add(usage.label() + " 금일지침: " + usage.todayRef() + " 해석 실패");
                }
            }
        }
        assertEquals(List.of(), problems, "원본 양식 해석 결과가 매핑표와 달랐다");
    }

    @Test
    @DisplayName("약품 섹션에 행이 끼어들면 라벨을 따라간다")
    void followsLabelWhenRowInserted() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("1");
            // 3. 약품 사용현황 블록만 원본과 같은 배치로 만든다
            write(sheet, 10, 1, "3. 약품 사용현황 (KG)");
            write(sheet, 11, 1, "품 명");
            write(sheet, 11, 3, "전일 \n재고량");
            write(sheet, 11, 5, "입고량");
            write(sheet, 11, 7, "사용량");
            write(sheet, 12, 1, "탄산암모늄");
            write(sheet, 13, 1, "청관제");
            write(sheet, 22, 1, "5. 전력 사용량 (KWH)");   // 블록 끝을 정하는 다음 섹션

            Entry inbound = FluidizedDailyLogCellMap.DAY_CELLS.stream()
                    .filter(e -> e.label().equals("청관제 입고"))
                    .findFirst().orElseThrow();

            // 원래 자리(E13)를 찾아야 한다
            Resolved before = indexOf(sheet).resolve(inbound);
            assertEquals(13, before.row());
            assertEquals(5, before.col());

            // 탄산암모늄 위에 행이 하나 끼어들면 청관제는 14행으로 내려간다
            sheet.shiftRows(11, sheet.getLastRowNum(), 1);
            write(sheet, 12, 1, "※ 추가 안내");

            FluidizedDailyLogSheetIndex shifted = indexOf(sheet);
            Resolved after = shifted.resolve(inbound);

            assertEquals(14, after.row(), "라벨을 따라 14행을 찾아야 한다");
            assertEquals(5, after.col(), "입고량 열은 그대로 E열이다");
            assertTrue(after.byHeader(), "좌표 폴백이 아니라 라벨로 찾아야 한다");
            assertTrue(after.moved(), "양식이 바뀌었다고 표시해야 한다");
            assertTrue(shifted.moved().stream().anyMatch(m -> m.contains("청관제 입고")));
        }
    }

    @Test
    @DisplayName("좌우 블록이 같은 행을 써도 섞이지 않는다")
    void doesNotMixLeftAndRightBlocks() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("1");
            // 좌측 5. 전력 / 우측 6. 유틸리티 — 둘 다 23행이 헤더, 24행이 첫 항목
            write(sheet, 22, 1, "5. 전력 사용량 (KWH)");
            write(sheet, 23, 1, "구 분");
            write(sheet, 23, 4, "전일지침");
            write(sheet, 23, 7, "금일지침");
            write(sheet, 24, 1, "MAIN");
            write(sheet, 22, 16, "6. 유틸리티 사용현황 (㎥)");
            write(sheet, 23, 16, "구 분");
            write(sheet, 23, 19, "전일 사용량");
            write(sheet, 23, 22, "금일 사용량");
            write(sheet, 24, 16, "재이용수");

            FluidizedDailyLogSheetIndex index = indexOf(sheet);

            Entry mainPrev = FluidizedDailyLogCellMap.DAY_CELLS.stream()
                    .filter(e -> e.label().equals("MAIN 전일지침")).findFirst().orElseThrow();
            Entry waterToday = FluidizedDailyLogCellMap.DAY_CELLS.stream()
                    .filter(e -> e.label().equals("재이용수 금일")).findFirst().orElseThrow();

            Resolved left = index.resolve(mainPrev);
            Resolved right = index.resolve(waterToday);

            assertEquals(24, left.row());
            assertEquals(4, left.col(), "좌측 블록의 D열이어야 한다");
            assertEquals(24, right.row());
            assertEquals(22, right.col(), "우측 블록의 V열이어야 한다");
        }
    }

    @Test
    @DisplayName("라벨이 없는 근무자 칸은 좌표로 폴백한다")
    void fallsBackForWorkerCells() throws Exception {
        Entry worker = FluidizedDailyLogCellMap.DAY_CELLS.stream()
                .filter(e -> e.label().equals("근무자 오전"))
                .findFirst().orElseThrow();
        assertEquals(null, worker.header(), "근무자 칸은 라벨이 없어 헤더가 없어야 한다");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("1");
            Resolved resolved = indexOf(sheet).resolve(worker);
            assertEquals(2, resolved.row());
            assertEquals(2, resolved.col());
            assertTrue(!resolved.byHeader());
        }
    }

    private static void write(Sheet sheet, int rowNumber, int colNumber, String value) {
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) row = sheet.createRow(rowNumber - 1);
        Cell cell = row.getCell(colNumber - 1);
        if (cell == null) cell = row.createCell(colNumber - 1);
        cell.setCellValue(value);
    }

    /** 저장소의 {@code 원본} 폴더에서 유동상 운전일지 파일을 찾는다. 없으면 null. */
    private static Path findTemplate() throws Exception {
        Path dir = Path.of("원본");
        if (!Files.isDirectory(dir)) dir = Path.of("..", "원본");
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().contains("유동상 운전일지"))
                    .filter(p -> !p.getFileName().toString().contains("백업"))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
