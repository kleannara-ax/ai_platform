package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 복합보일러 "스팀생산실적" 일별 표 — 2026년 10·11월에 바뀐 양식과, 열 자리가 바뀐 경우.
 *
 * <p>머리글 글자는 실제 파일(복합보일러(2026년) 스팀구매 현황.xlsx)의 10월·11월 시트에서 옮겼다.
 */
class ComboBoilerLayout2026Test {

    /** 한 열의 머리글 세 줄 — {설비(병합 첫 칸만), 항목, 단위·세부}. */
    private static final String[][] NOVEMBER = {
            {"복합보일러", "스팀구매량", "(T/D)"}, {"", "", "(T/H)"},
            {"", "응축수판매량", "(㎥/D)"}, {"", "보충수판매량", "(㎥/D)"}, {"", "탈기기급수량", "(㎥/D)"},
            {"", "공정수판매량", "(㎥/D)"}, {"", "희석수판매량", "(㎥/D)"},
            {"폐합성소각로 기관실유량계", "2호 스팀량", "(T/D)"}, {"", "", "편차"},
            {"폐합성소각로                     깨끗한나라 보일러", "2호 스팀 구입량", "(T/D) "}, {"", "", "(T/H)"},
            {"깨끗한나라 보일러", "스팀생산량(T/D)", "20톤(#2)"}, {"", "", "50톤(#3)"},
            {"유동상 보일러", "총스팀량", "FI-301"}, {"", "응축수", "FI-303"},
            {"", "스팀 생산량", "FI-304"}, {"", "응축수", "FI-07"},
            {"스팀총계                  (복합+소각로+기관)", "", "(T/D)"}, {"", "", "(T/H)"},
            {"당사 보일러 가동시간(H/D)", "", "20톤(#2)"}, {"", "", "50톤(#3)"}, {"", "", "유동상"},
            {"", "", ""},
            {"", "", "변동사항(복합보일러)"}, {"", "", ""}, {"", "", ""}, {"", "", ""},
            {"", "", "변동사항(제스코,유동상 보일러)"}, {"", "", ""}, {"", "", ""},
            {"", "복합보일러", ""}, {"", "", "#1호기"}, {"", "운휴시간(분)", "#2호기"},
            {"", "디스퍼져", "스팀량"}, {"", "시간당", "스팀량"}, {"", "", "가동시간(분)"}
    };

    private static final String[][] OCTOBER = {
            {"복합보일러", "스팀구매량", "(T/D)"}, {"", "", "(T/H)"},
            {"", "응축수판매량", "(㎥/D)"}, {"", "보충수판매량", "(㎥/D)"}, {"", "탈기기급수량", "(㎥/D)"},
            {"", "공정수판매량", "(㎥/D)"}, {"", "희석수판매량", "(㎥/D)"},
            {"외부보일러", "스팀구매량", "(T/D)"}, {"", "", "(T/H)"},
            {"", "응축수판매량", "(㎥/D)"}, {"", "보충수판매량", "(㎥/D)"},
            {"폐합성소각로                     깨끗한나라 보일러", "2호 스팀 구입량", "(T/D) "}, {"", "", "(T/H)"},
            {"깨끗한나라 보일러", "스팀생산량(T/D)", "20톤(#2)"}, {"", "", "50톤(#3)"},
            {"유동상 보일러", "총스팀량", "FI-301"}, {"", "응축수", "FI-303"},
            {"", "스팀 생산량", "FI-304"}, {"", "응축수", "FI-07"},
            {"스팀총계                  (복합+외부+소각로+기관)", "", "(T/D)"}, {"", "", "(T/H)"},
            {"당사 보일러 가동시간(H/D)", "", "20톤(#2)"}, {"", "", "50톤(#3)"}, {"", "", "유동상"}
    };

    /** 머리글을 {@code headerRow}(0-based 설비 줄)부터 세 줄로 적고 그 아래 1~31일을 둔다. */
    private static Sheet sheetOf(Workbook workbook, String[][] columns, int headerRow) {
        Sheet sheet = workbook.createSheet("스팀생산실적(" + (workbook.getNumberOfSheets() + 1) + "월)");
        for (int line = 0; line < 3; line += 1) {
            Row row = sheet.createRow(headerRow + line);
            if (line == 1) row.createCell(0).setCellValue("일");
            if (line == 2) row.createCell(0).setCellValue("자");
            for (int i = 0; i < columns.length; i += 1) {
                if (!columns[i][line].isEmpty()) row.createCell(i + 1).setCellValue(columns[i][line]);
            }
        }
        for (int day = 1; day <= 31; day += 1) {
            sheet.createRow(headerRow + 2 + day).createCell(0).setCellValue(day);
        }
        return sheet;
    }

    @Test
    @DisplayName("11월 양식 — 외부보일러·1호가 없고 기관실유량계·유동상 총스팀량이 있다")
    void novemberLayout() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            ComboBoilerLayout layout = ComboBoilerLayout.of(sheetOf(workbook, NOVEMBER, 3));
            Map<String, Integer> expected = new LinkedHashMap<>();
            expected.put("comboSteam", 1);
            expected.put("comboCondensate", 3);
            expected.put("comboMakeup", 4);
            expected.put("comboDeaerator", 5);
            expected.put("comboProcess", 6);
            expected.put("comboDilution", 7);
            expected.put("wasteRoomSteam2", 8);
            expected.put("wasteSteam2", 10);
            expected.put("kn20", 12);
            expected.put("kn50", 13);
            expected.put("fluidizedTotalSteam", 14);
            expected.put("fluidizedTotalCondensate", 15);
            expected.put("fluidizedSteam", 16);
            expected.put("fluidizedCondensate", 17);
            expected.put("runtime20", 20);
            expected.put("runtime50", 21);
            expected.put("runtimeFluidized", 22);
            expected.put("changeCombo", 24);
            expected.put("changeJesco", 28);
            expected.put("down1", 32);
            expected.put("down2", 33);
            expected.put("disperserSteam", 34);
            expected.put("runtimeMin", 36);
            assertEquals(expected, layout.dayColumns());
            assertEquals(7, layout.dayRows().get(1));
            assertEquals(37, layout.dayRows().get(31));
        }
    }

    @Test
    @DisplayName("10월 양식 — 외부보일러가 남아 있고 폐합성은 2호만, 유동상은 네 칸")
    void octoberLayout() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Map<String, Integer> cols = ComboBoilerLayout.of(sheetOf(workbook, OCTOBER, 3)).dayColumns();
            assertEquals(8, cols.get("externalSteam"));
            assertEquals(10, cols.get("externalCondensate"));
            assertEquals(11, cols.get("externalMakeup"));
            assertNull(cols.get("wasteSteam1"));
            assertEquals(12, cols.get("wasteSteam2"));
            assertNull(cols.get("wasteRoomSteam2"));
            assertEquals(14, cols.get("kn20"));
            assertEquals(15, cols.get("kn50"));
            assertEquals(16, cols.get("fluidizedTotalSteam"));
            assertEquals(17, cols.get("fluidizedTotalCondensate"));
            assertEquals(18, cols.get("fluidizedSteam"));
            assertEquals(19, cols.get("fluidizedCondensate"));
            assertEquals(22, cols.get("runtime20"));
            assertEquals(24, cols.get("runtimeFluidized"));
        }
    }

    @Test
    @DisplayName("설비 묶음의 순서를 뒤집고 머리글을 두 줄 내려도 같은 항목을 찾는다")
    void reorderedColumns() throws Exception {
        // 11월 양식을 묶음 단위로 재배열: 가동시간 → 유동상(생산량 쌍이 총스팀량 쌍보다 먼저) → 깨끗한나라(50톤이 먼저)
        //  → 폐합성 → 기관실유량계 → 복합보일러(희석수가 맨 앞)
        String[][] columns = {
                {"당사 보일러 가동시간(H/D)", "", "유동상"}, {"", "", "50톤(#3)"}, {"", "", "20톤(#2)"},
                {"유동상 보일러", "스팀 생산량", "FI-304"}, {"", "응축수", "FI-07"},
                {"", "총스팀량", "FI-301"}, {"", "응축수", "FI-303"},
                {"깨끗한나라 보일러", "스팀생산량(T/D)", "50톤(#3)"}, {"", "", "20톤(#2)"},
                {"폐합성소각로", "2호 스팀 구입량", "(T/D)"}, {"", "", "(T/H)"},
                {"폐합성소각로 기관실유량계", "2호 스팀량", "(T/D)"}, {"", "", "편차"},
                {"복합보일러", "희석수판매량", "(㎥/D)"}, {"", "공정수판매량", "(㎥/D)"},
                {"", "스팀구매량", "(T/D)"}, {"", "", "(T/H)"}, {"", "응축수판매량", "(㎥/D)"}
        };
        try (Workbook workbook = new XSSFWorkbook()) {
            ComboBoilerLayout layout = ComboBoilerLayout.of(sheetOf(workbook, columns, 5));
            Map<String, Integer> expected = new LinkedHashMap<>();
            expected.put("runtimeFluidized", 1);
            expected.put("runtime50", 2);
            expected.put("runtime20", 3);
            expected.put("fluidizedSteam", 4);
            expected.put("fluidizedCondensate", 5);
            expected.put("fluidizedTotalSteam", 6);
            expected.put("fluidizedTotalCondensate", 7);
            expected.put("kn50", 8);
            expected.put("kn20", 9);
            expected.put("wasteSteam2", 10);
            expected.put("wasteRoomSteam2", 12);
            expected.put("comboDilution", 14);
            expected.put("comboProcess", 15);
            expected.put("comboSteam", 16);
            expected.put("comboCondensate", 18);
            assertEquals(expected, layout.dayColumns());
            assertEquals(9, layout.dayRows().get(1), "머리글이 두 줄 내려가면 1일도 두 줄 내려간다");
            assertFalse(layout.notFound().isEmpty(), "없는 항목(외부보일러 등)은 건너뛴 목록에 남는다");
        }
    }
}
