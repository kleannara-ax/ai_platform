package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026년 에너지 회계비용 워크북에서 바뀐 표기 — 해마다 바뀌는 행 이름, 전년 12월 열이 없는 양식, 바뀐 항목 이름.
 */
class EnergySheetIndex2026LayoutTest {

    private static void put(Sheet sheet, int row, int col, String text) {
        Row r = sheet.getRow(row - 1);
        if (r == null) r = sheet.createRow(row - 1);
        r.createCell(col - 1).setCellValue(text);
    }

    @Test
    @DisplayName("1월 왼쪽 칸이 '2025년 예상' 이면 전년 12월 열이 없는 양식으로 본다")
    void noPrevDecemberColumn() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("2026년 연료비 사업계획");
            put(sheet, 2, 2, "구분");
            put(sheet, 2, 3, "2025년 예상");
            put(sheet, 2, 4, "1월");
            put(sheet, 2, 5, "2월");
            put(sheet, 3, 2, "LNG 구매");
            EnergySheetIndex index = EnergySheetIndex.build(sheet, new int[]{1, 2}, 3, 2);
            assertTrue(index.noPrevDecember());
            assertEquals(4, index.monthColumn(1, -1));
        }
    }

    @Test
    @DisplayName("1월 왼쪽 칸이 '25. 12월' 이면 전년 12월 열이다")
    void prevDecemberColumn() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("비교");
            put(sheet, 2, 3, "'25. 12월");
            put(sheet, 2, 4, "1월");
            EnergySheetIndex index = EnergySheetIndex.build(sheet, new int[]{1, 2}, 3, 2);
            assertFalse(index.noPrevDecember());
            assertEquals(3, index.monthColumn(0, -1));
        }
    }

    @Test
    @DisplayName("해마다 앞 숫자가 바뀌는 행은 정규식으로 — 첫 줄이 전년, 둘째 줄이 올해")
    void yearlyRenamedRows() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("2026 연료비 배분비율");
            put(sheet, 1, 1, "2026년 버너 LNG 비용");
            put(sheet, 2, 1, "25년 LNG 단가");
            put(sheet, 3, 1, "26년 LNG 단가");
            put(sheet, 4, 1, "증감률(%)");
            EnergySheetIndex index = EnergySheetIndex.build(sheet, new int[]{2, 1}, 1, 0);
            Pattern pattern = Pattern.compile(EnergyFuelRatioWorkbookService.YEAR_LNG_PRICE.substring(3));
            Integer first = index.rowMatchingOrNull(2, "전년 단가", pattern);
            assertEquals(2, first);
            assertEquals(3, index.rowMatchingOrNull(first + 1, "올해 단가", pattern));
            assertNull(index.rowMatchingOrNull(4, "없음", pattern));
        }
    }

    @Test
    @DisplayName("바뀐 항목 이름은 후보 여러 개 중 하나로 찾는다")
    void alternateLabels() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("회계비용 마감과 실마감 비교(2026)");
            put(sheet, 5, 2, "폐합성 /신설소각로 스팀구매");
            EnergySheetIndex index = EnergySheetIndex.build(sheet, new int[]{1, 2}, 3, 0);
            assertNull(index.rowOrNull(3, 6, "폐합성", "폐합성소각로 스팀구매"));
            assertEquals(5, index.rowOrNull(3, 6, "폐합성", "폐합성소각로 스팀구매", "폐합성 /신설소각로 스팀구매"));
        }
    }
}
