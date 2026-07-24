package com.company.module.dailyreport.service;

import com.company.module.dailyreport.entity.DailyReportCell;
import com.company.module.dailyreport.entity.DailyReportTable;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기본 셀 템플릿 — 일보 자동 생성 시 4개 표의 HEADER/READONLY/DATA 셀을 구성한다.
 *
 * 셀 구조는 원본 엑셀(세부공장일보.xlsx) 기반 preview-server.js의
 * tbl1Cells ~ tbl4Cells 시드 데이터를 Java로 변환한 것이다.
 *
 * DATA 유형 셀의 cellValue는 빈 문자열("")로 초기화되며,
 * 사용자가 입력 화면에서 값을 채운다.
 */
public final class DefaultCellTemplate {

    private DefaultCellTemplate() { /* utility class */ }

    /**
     * 테이블 코드에 맞는 기본 셀을 생성하여 table 엔티티에 추가한다.
     * @param table 대상 테이블 엔티티
     * @param reportDate 일보 날짜 (헤더 롤링 월 계산용)
     */
    public static void populateDefaultCells(DailyReportTable table, LocalDate reportDate) {
        String code = table.getTableCode();
        switch (code) {
            case "TBL_PRODUCTION_INDEX" -> addProductionIndexCells(table, reportDate);
            case "TBL_INVENTORY"        -> addInventoryCells(table);
            case "TBL_ENERGY"           -> addEnergyCells(table);
            case "TBL_BOILER"           -> addBoilerCells(table);
            default -> { /* unknown table code — skip */ }
        }
    }

    /** 하위 호환용 (reportDate 없이 호출 시 오늘 날짜 기준) */
    public static void populateDefaultCells(DailyReportTable table) {
        populateDefaultCells(table, LocalDate.now());
    }

    // ═══════════════════════════════════════════════
    //  1. 주요 생산 지표 현황  (10행 × 15열)
    // ═══════════════════════════════════════════════
    private static void addProductionIndexCells(DailyReportTable t, LocalDate reportDate) {
        // ── 롤링 월 계산: reportDate 기준 직근 8개월 ──
        // 예) 2026-07 → 2025-12 ~ 2026-07
        //     2026-08 → 2026-01 ~ 2026-08
        //     2027-01 → 2026-06 ~ 2027-01
        YearMonth current = YearMonth.from(reportDate);
        List<YearMonth> rolling = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            rolling.add(current.minusMonths(i));
        }

        // 연도별 그룹핑 (Row 0 대 헤더의 colspan 계산용)
        // LinkedHashMap으로 순서 보장
        Map<Integer, int[]> yearGroups = new LinkedHashMap<>(); // year → [startCol, count]
        for (int i = 0; i < rolling.size(); i++) {
            int yr = rolling.get(i).getYear();
            final int col = 6 + i;
            yearGroups.computeIfAbsent(yr, k -> new int[]{col, 0});
            yearGroups.get(yr)[1]++;
        }

        int prevYear2 = reportDate.getYear() - 2; // '24년 월평균
        int prevYear1 = reportDate.getYear() - 1; // '25년 월평균
        String excelCols = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // ── Row 0: 대 헤더 (고정부) ──
        h(t, 0, 0, "B5",  "생산지표",       2, 3);
        h(t, 0, 3, "E5",  "최종",           1, 1);
        h(t, 0, 4, "F5",  "'" + String.valueOf(prevYear2).substring(2) + "년", 1, 1);
        h(t, 0, 5, "G5",  "'" + String.valueOf(prevYear1).substring(2) + "년", 1, 1);

        // ── Row 0: 대 헤더 (롤링 연도 그룹) ──
        for (Map.Entry<Integer, int[]> entry : yearGroups.entrySet()) {
            int yr = entry.getKey();
            int startCol = entry.getValue()[0];
            int count = entry.getValue()[1];
            String coord = String.valueOf(excelCols.charAt(startCol + 1)) + "5";
            h(t, 0, startCol, coord, "'" + String.valueOf(yr).substring(2) + "년", 1, count);
        }
        h(t, 0, 14, "P5", "비고 사항", 2, 1);

        // ── Row 1: 소 헤더 (고정부) ──
        h(t, 1, 3, "E6",  "목표",    1, 1);
        h(t, 1, 4, "F6",  "월평균",  1, 1);
        h(t, 1, 5, "G6",  "월평균",  1, 1);

        // ── Row 1: 소 헤더 (롤링 월) ──
        for (int i = 0; i < rolling.size(); i++) {
            int colIdx = 6 + i;
            String coord = String.valueOf(excelCols.charAt(colIdx + 1)) + "6";
            h(t, 1, colIdx, coord, rolling.get(i).getMonthValue() + "월", 1, 1);
        }

        // ── Row 2: 제지3 평균선속 ──
        ro(t, 2, 0, "B7",  "제지3 평균선속(m/분)", 1, 3);
        ro(t, 2, 3, "E7",  "640");
        ro(t, 2, 4, "F7",  "583.5");
        ro(t, 2, 5, "G7",  "587");
        ro(t, 2, 6, "H7",  "588");
        ro(t, 2, 7, "I7",  "597");
        ro(t, 2, 8, "J7",  "584");
        ro(t, 2, 9, "K7",  "577");
        ro(t, 2,10, "L7",  "597");
        ro(t, 2,11, "M7",  "597");
        ro(t, 2,12, "N7",  "594");
        d(t,  2,13, "O7",  null, null);
        d(t,  2,14, "P7",  "daily", "매일");

        // ── Row 3: 초지5 생산량 ──
        ro(t, 3, 0, "B8",  "초지5 생산량(톤/日)", 1, 3);
        ro(t, 3, 3, "E8",  "85");
        ro(t, 3, 4, "F8",  "83.8");
        ro(t, 3, 5, "G8",  "76");
        ro(t, 3, 6, "H8",  "83.5");
        ro(t, 3, 7, "I8",  "80.4");
        ro(t, 3, 8, "J8",  "85.6");
        ro(t, 3, 9, "K8",  "79.9");
        ro(t, 3,10, "L8",  "83.6");
        ro(t, 3,11, "M8",  "83");
        ro(t, 3,12, "N8",  "79.5");
        d(t,  3,13, "O8",  null, null);
        d(t,  3,14, "P8",  "daily", "매일");

        // ── Row 4: 수율 - PS 완제품 ──
        ro(t, 4, 0, "B9",  "수율(%)", 3, 1);
        ro(t, 4, 1, "C9",  "PS",      2, 1);
        ro(t, 4, 2, "D9",  "완제품",  1, 1);
        ro(t, 4, 3, "E9",  "91");
        ro(t, 4, 4, "F9",  "97.7");
        ro(t, 4, 5, "G9",  "99.2");
        ro(t, 4, 6, "H9",  "98.6");
        ro(t, 4, 7, "I9",  "98.7");
        ro(t, 4, 8, "J9",  "97.2");
        ro(t, 4, 9, "K9",  "101.5");
        ro(t, 4,10, "L9",  "101.8");
        ro(t, 4,11, "M9",  "99.8");
        ro(t, 4,12, "N9",  "98.7");
        d(t,  4,13, "O9",  "event", "발생 시");
        d(t,  4,14, "P9",  "daily", "매일");

        // ── Row 5: 수율 - PS 코팅제외 ──
        ro(t, 5, 2, "D10", "코팅제외", 1, 1);
        ro(t, 5, 3, "E10", "78");
        ro(t, 5, 4, "F10", "83.8");
        ro(t, 5, 5, "G10", "85.2");
        ro(t, 5, 6, "H10", "84.1");
        ro(t, 5, 7, "I10", "84.6");
        ro(t, 5, 8, "J10", "83.5");
        ro(t, 5, 9, "K10", "87.6");
        ro(t, 5,10, "L10", "88.2");
        ro(t, 5,11, "M10", "86.3");
        ro(t, 5,12, "N10", "84.7");
        d(t,  5,13, "O10", "event", "발생 시");
        d(t,  5,14, "P10", "daily", "매일");

        // ── Row 6: 수율 - 화장지 ──
        ro(t, 6, 1, "C11", "화장지", 1, 2);
        ro(t, 6, 3, "E11", "63.5");
        ro(t, 6, 4, "F11", "63.5");
        ro(t, 6, 5, "G11", "64.6");
        ro(t, 6, 6, "H11", "61.1");
        ro(t, 6, 7, "I11", "63.3");
        ro(t, 6, 8, "J11", "63.6");
        ro(t, 6, 9, "K11", "63.6");
        ro(t, 6,10, "L11", "69.6");
        ro(t, 6,11, "M11", "74.6");
        ro(t, 6,12, "N11", "74.4");
        d(t,  6,13, "O11", "event", "발생 시");
        d(t,  6,14, "P11", "daily", "매일");

        // ── Row 7: 고지감량율 ──
        ro(t, 7, 0, "B12", "고지감량율(%)", 1, 3);
        ro(t, 7, 3, "E12", "-");
        ro(t, 7, 4, "F12", "15.8");
        ro(t, 7, 5, "G12", "14.8");
        ro(t, 7, 6, "H12", "12.7");
        ro(t, 7, 7, "I12", "11.2");
        ro(t, 7, 8, "J12", "11.8");
        ro(t, 7, 9, "K12", "13");
        ro(t, 7,10, "L12", "14.2");
        ro(t, 7,11, "M12", "15.9");
        ro(t, 7,12, "N12", "16");
        d(t,  7,13, "O12", null, null);
        d(t,  7,14, "P12", "daily", "매일");

        // ── Row 8: 슬러지원단위 - 제지 ──
        ro(t, 8, 0, "B13", "슬러지원단위", 2, 2);
        ro(t, 8, 2, "D13", "제   지", 1, 1);
        d(t,  8, 3, "E13", "yearly", "매년");
        ro(t, 8, 4, "F13", "89");
        ro(t, 8, 5, "G13", "91");
        ro(t, 8, 6, "H13", "94");
        ro(t, 8, 7, "I13", "99");
        ro(t, 8, 8, "J13", "104");
        ro(t, 8, 9, "K13", "96");
        ro(t, 8,10, "L13", "84");
        ro(t, 8,11, "M13", "82");
        ro(t, 8,12, "N13", "84");
        d(t,  8,13, "O13", "event", "발생 시");
        d(t,  8,14, "P13", "daily", "매일");

        // ── Row 9: 슬러지원단위 - 화장지 ──
        ro(t, 9, 2, "D14", "화장지", 1, 1);
        d(t,  9, 3, "E14", "yearly", "매년");
        ro(t, 9, 4, "F14", "76");
        ro(t, 9, 5, "G14", "64");
        ro(t, 9, 6, "H14", "81");
        ro(t, 9, 7, "I14", "58");
        ro(t, 9, 8, "J14", "68");
        ro(t, 9, 9, "K14", "50");
        ro(t, 9,10, "L14", "46");
        ro(t, 9,11, "M14", "53");
        ro(t, 9,12, "N14", "62");
        d(t,  9,13, "O14", "event", "발생 시");
        d(t,  9,14, "P14", "daily", "매일");
    }

    // ═══════════════════════════════════════════════
    //  2. 제지 재공품 및 야적현황  (10행 × 13열)
    // ═══════════════════════════════════════════════
    private static void addInventoryCells(DailyReportTable t) {
        // ── Row 0: 대 헤더 ──
        h(t, 0, 0, "B19", "구 분",        2, 2);
        h(t, 0, 2, "D19", "기준",          2, 1);
        h(t, 0, 3, "E19", "적정재고",      2, 1);
        h(t, 0, 4, "F19", "'25년",         1, 1);
        h(t, 0, 5, "G19", "'26년",         1, 7);
        h(t, 0,12, "N19", "비 고",         1, 1);

        // ── Row 1: 소 헤더 ──
        h(t, 1, 4, "F20", "12월", 1, 1);
        h(t, 1, 5, "G20", "1월",  1, 1);
        h(t, 1, 6, "H20", "2월",  1, 1);
        h(t, 1, 7, "I20", "3월",  1, 1);
        h(t, 1, 8, "J20", "4월",  1, 1);
        h(t, 1, 9, "K20", "5월",  1, 1);
        h(t, 1,10, "L20", "6월",  1, 1);
        h(t, 1,11, "M20", "7월",  1, 1);
        h(t, 1,12, "N20", null,   1, 1);

        // ── Row 2: 밀롤창고 ──
        ro(t, 2, 0, "B21", "제지 재공품", 4, 1);
        ro(t, 2, 1, "C21", "밀롤창고",    1, 1);
        ro(t, 2, 2, "D21", "톤",           4, 1);
        d(t,  2, 3, "E21", "event", "발생 시");
        ro(t, 2, 4, "F21", "3826");
        ro(t, 2, 5, "G21", "3043");
        ro(t, 2, 6, "H21", "3296");
        ro(t, 2, 7, "I21", "2196");
        ro(t, 2, 8, "J21", "3037");
        ro(t, 2, 9, "K21", "3711");
        ro(t, 2,10, "L21", "3006");
        d(t,  2,11, "M21", null, null);
        d(t,  2,12, "N21", "daily", "매일");

        // ── Row 3: 카타대기 ──
        ro(t, 3, 1, "C22", "카타대기", 1, 1);
        d(t,  3, 3, "E22", "event", "발생 시");
        ro(t, 3, 4, "F22", "320");
        ro(t, 3, 5, "G22", "315");
        ro(t, 3, 6, "H22", "549");
        ro(t, 3, 7, "I22", "648");
        ro(t, 3, 8, "J22", "1360");
        ro(t, 3, 9, "K22", "1121");
        ro(t, 3,10, "L22", "1110");
        d(t,  3,11, "M22", null, null);
        d(t,  3,12, "N22", "daily", "매일");

        // ── Row 4: 미포장 ──
        ro(t, 4, 1, "C23", "미포장", 1, 1);
        d(t,  4, 3, "E23", "event", "발생 시");
        ro(t, 4, 4, "F23", "212");
        ro(t, 4, 5, "G23", "764");
        ro(t, 4, 6, "H23", "702");
        ro(t, 4, 7, "I23", "149");
        ro(t, 4, 8, "J23", "86");
        ro(t, 4, 9, "K23", "173");
        ro(t, 4,10, "L23", "266");
        d(t,  4,11, "M23", null, null);
        d(t,  4,12, "N23", "daily", "매일");

        // ── Row 5: 포장후 물류입고전 ──
        ro(t, 5, 1, "C24", "포장후 물류입고전", 1, 1);
        d(t,  5, 3, "E24", "event", "발생 시");
        ro(t, 5, 4, "F24", "83");
        ro(t, 5, 5, "G24", "139");
        ro(t, 5, 6, "H24", "151");
        ro(t, 5, 7, "I24", "88");
        ro(t, 5, 8, "J24", "58");
        ro(t, 5, 9, "K24", "288");
        ro(t, 5,10, "L24", "423");
        d(t,  5,11, "M24", null, null);
        d(t,  5,12, "N24", "daily", "매일");

        // ── Row 6: 장기재고 3개월 초과 ──
        ro(t, 6, 0, "B25", "장기재고",      2, 1);
        ro(t, 6, 1, "C25", "3개월 초과",    1, 1);
        ro(t, 6, 2, "D25", "톤",            1, 1);
        ro(t, 6, 3, "E25", "0");
        ro(t, 6, 4, "F25", "4354");
        ro(t, 6, 5, "G25", "4372");
        ro(t, 6, 6, "H25", "4005");
        ro(t, 6, 7, "I25", "4236");
        ro(t, 6, 8, "J25", "3761");
        ro(t, 6, 9, "K25", "3404");
        ro(t, 6,10, "L25", "3120");
        d(t,  6,11, "M25", "monthly", "매월");
        d(t,  6,12, "N25", "daily", "매일");

        // ── Row 7: 장기재고 6개월 초과 ──
        ro(t, 7, 1, "C26", "6개월 초과", 1, 1);
        ro(t, 7, 2, "D26", "톤",          1, 1);
        ro(t, 7, 3, "E26", "0");
        ro(t, 7, 4, "F26", "917");
        ro(t, 7, 5, "G26", "980");
        ro(t, 7, 6, "H26", "786");
        ro(t, 7, 7, "I26", "915");
        ro(t, 7, 8, "J26", "957");
        ro(t, 7, 9, "K26", "1543");
        ro(t, 7,10, "L26", "1130");
        d(t,  7,11, "M26", "monthly", "매월");
        d(t,  7,12, "N26", "daily", "매일");

        // ── Row 8: 야적현황 - 제지 ──
        ro(t, 8, 0, "B27", "야적현황", 2, 1);
        ro(t, 8, 1, "C27", "제지",     1, 1);
        ro(t, 8, 2, "D27", "톤",       1, 1);
        ro(t, 8, 3, "E27", "0");
        ro(t, 8, 4, "F27", "489");
        ro(t, 8, 5, "G27", "239");
        ro(t, 8, 6, "H27", "0");
        ro(t, 8, 7, "I27", "0");
        ro(t, 8, 8, "J27", "0");
        ro(t, 8, 9, "K27", "0");
        ro(t, 8,10, "L27", "0");
        d(t,  8,11, "M27", null, null);
        d(t,  8,12, "N27", "daily", "매일");

        // ── Row 9: 야적현황 - 생활 ──
        ro(t, 9, 1, "C28", "생활",     1, 1);
        ro(t, 9, 2, "D28", "팔레트",   1, 1);
        ro(t, 9, 3, "E28", "0");
        ro(t, 9, 4, "F28", "0");
        ro(t, 9, 5, "G28", "0");
        ro(t, 9, 6, "H28", "0");
        ro(t, 9, 7, "I28", "0");
        ro(t, 9, 8, "J28", "0");
        ro(t, 9, 9, "K28", "0");
        ro(t, 9,10, "L28", "0");
        d(t,  9,11, "M28", null, null);
        d(t,  9,12, "N28", "daily", "매일");
    }

    // ═══════════════════════════════════════════════
    //  3. 에너지 원단위  (8행 × 6열)
    // ═══════════════════════════════════════════════
    private static void addEnergyCells(DailyReportTable t) {
        // ── Row 0: 대 헤더 ──
        h(t, 0, 0, "B34", "구분",       2, 2);
        h(t, 0, 2, "D34", "목표",       2, 1);
        h(t, 0, 3, "E34", "6월 실적",   2, 1);
        h(t, 0, 4, "F34", "7월 현재",   1, 2);

        // ── Row 1: 소 헤더 ──
        h(t, 1, 4, "F35", "계 획", 1, 1);
        h(t, 1, 5, "G35", "실 적", 1, 1);

        // ── Row 2: 전력 - 제지 ──
        ro(t, 2, 0, "B36", "전력",    3, 1);
        ro(t, 2, 1, "C36", "제   지", 1, 1);
        d(t,  2, 2, "D36", "yearly", "매년");
        d(t,  2, 3, "E36", null, null);
        d(t,  2, 4, "F36", "monthly", "매월");
        d(t,  2, 5, "G36", null, null);

        // ── Row 3: 전력 - 화장지 ──
        ro(t, 3, 1, "C37", "화장지", 1, 1);
        d(t,  3, 2, "D37", "yearly", "매년");
        d(t,  3, 3, "E37", null, null);
        d(t,  3, 4, "F37", "monthly", "매월");
        d(t,  3, 5, "G37", null, null);

        // ── Row 4: 전력 - 화)초지5 ──
        ro(t, 4, 1, "C38", "화)초지5", 1, 1);
        d(t,  4, 2, "D38", "yearly", "매년");
        d(t,  4, 3, "E38", null, null);
        d(t,  4, 4, "F38", "monthly", "매월");
        d(t,  4, 5, "G38", null, null);

        // ── Row 5: 연료 - 제지 ──
        ro(t, 5, 0, "B39", "연료",    3, 1);
        ro(t, 5, 1, "C39", "제   지", 1, 1);
        d(t,  5, 2, "D39", "yearly", "매년");
        d(t,  5, 3, "E39", null, null);
        d(t,  5, 4, "F39", "monthly", "매월");
        d(t,  5, 5, "G39", null, null);

        // ── Row 6: 연료 - 화장지 ──
        ro(t, 6, 1, "C40", "화장지", 1, 1);
        d(t,  6, 2, "D40", "yearly", "매년");
        d(t,  6, 3, "E40", null, null);
        d(t,  6, 4, "F40", "monthly", "매월");
        d(t,  6, 5, "G40", null, null);

        // ── Row 7: 연료 - 화)초지5 ──
        ro(t, 7, 1, "C41", "화)초지5", 1, 1);
        d(t,  7, 2, "D41", "yearly", "매년");
        d(t,  7, 3, "E41", null, null);
        d(t,  7, 4, "F41", "monthly", "매월");
        d(t,  7, 5, "G41", null, null);
    }

    // ═══════════════════════════════════════════════
    //  4. 보일러 운영 현황  (7행 × 8열)
    // ═══════════════════════════════════════════════
    private static void addBoilerCells(DailyReportTable t) {
        // ── Row 0: 대 헤더 ──
        h(t, 0, 0, "J34", "구분",              2, 1);
        h(t, 0, 1, "K34", "목표",              2, 1);
        h(t, 0, 2, "L34", "7월단가\n(천원/톤)", 2, 1);
        h(t, 0, 3, "M34", "5월 실적",          2, 1);
        h(t, 0, 4, "N34", "6월 실적",          2, 1);
        h(t, 0, 5, "O34", "7월",               1, 2);
        h(t, 0, 7, "Q34", "비 고",             2, 1);

        // ── Row 1: 소 헤더 ──
        h(t, 1, 5, "O35", "계 획", 1, 1);
        h(t, 1, 6, "P35", "실 적", 1, 1);

        // ── Row 2: LNG보일러 ──
        ro(t, 2, 0, "J36", "LNG보일러", 1, 1);
        d(t,  2, 1, "K36", "yearly",  "매년");
        d(t,  2, 2, "L36", "yearly",  "매년");
        ro(t, 2, 3, "M36", "2.4");
        ro(t, 2, 4, "N36", "0.4");
        d(t,  2, 5, "O36", "monthly", "매월");
        d(t,  2, 6, "P36", "daily",   "매일");
        d(t,  2, 7, "Q36", "daily", "매일", 5, 1);

        // ── Row 3: 유동상소각로 ──
        ro(t, 3, 0, "J37", "유동상소각로", 1, 1);
        d(t,  3, 1, "K37", "yearly",  "매년");
        d(t,  3, 2, "L37", "yearly",  "매년");
        ro(t, 3, 3, "M37", "15.3");
        ro(t, 3, 4, "N37", "14.7");
        d(t,  3, 5, "O37", "monthly", "매월");
        d(t,  3, 6, "P37", "daily",   "매일");

        // ── Row 4: 복합보일러 ──
        ro(t, 4, 0, "J38", "복합보일러", 1, 1);
        d(t,  4, 1, "K38", "yearly",  "매년");
        d(t,  4, 2, "L38", "yearly",  "매년");
        ro(t, 4, 3, "M38", "56.8");
        ro(t, 4, 4, "N38", "52.5");
        d(t,  4, 5, "O38", "monthly", "매월");
        d(t,  4, 6, "P38", "daily",   "매일");

        // ── Row 5: 폐합성소각로 ──
        ro(t, 5, 0, "J39", "폐합성소각로", 1, 1);
        d(t,  5, 1, "K39", "yearly",  "매년");
        d(t,  5, 2, "L39", "yearly",  "매년");
        ro(t, 5, 3, "M39", "10.2");
        ro(t, 5, 4, "N39", "11.6");
        d(t,  5, 5, "O39", "monthly", "매월");
        d(t,  5, 6, "P39", "daily",   "매일");

        // ── Row 6: 합계 ──
        ro(t, 6, 0, "J40", "합  계", 1, 1);
        d(t,  6, 1, "K40", "yearly",  "매년");
        d(t,  6, 2, "L40", "yearly",  "매년");
        ro(t, 6, 3, "M40", "84.7");
        ro(t, 6, 4, "N40", "79.2");
        d(t,  6, 5, "O40", "monthly", "매월");
        d(t,  6, 6, "P40", "daily",   "매일");
    }

    // ═══════════════════════════════════════════════
    //  셀 생성 헬퍼 (단축 메서드)
    // ═══════════════════════════════════════════════

    /** HEADER 셀 (rowSpan/colSpan 지정) */
    private static void h(DailyReportTable t, int row, int col, String coord,
                           String value, int rs, int cs) {
        t.addCell(DailyReportCell.builder()
                .rowIndex(row).colIndex(col).excelCoord(coord)
                .cellValue(value).cellType("HEADER")
                .isLocked(true).rowSpan(rs).colSpan(cs)
                .build());
    }

    /** READONLY 셀 (1×1) */
    private static void ro(DailyReportTable t, int row, int col, String coord, String value) {
        t.addCell(DailyReportCell.builder()
                .rowIndex(row).colIndex(col).excelCoord(coord)
                .cellValue(value).cellType("READONLY")
                .isLocked(true).rowSpan(1).colSpan(1)
                .build());
    }

    /** READONLY 셀 (rowSpan/colSpan 지정) */
    private static void ro(DailyReportTable t, int row, int col, String coord,
                            String value, int rs, int cs) {
        t.addCell(DailyReportCell.builder()
                .rowIndex(row).colIndex(col).excelCoord(coord)
                .cellValue(value).cellType("READONLY")
                .isLocked(true).rowSpan(rs).colSpan(cs)
                .build());
    }

    /**
     * DATA 셀 (입력 가능, 빈 값으로 초기화)
     *
     * ★ 담당자(OWNER_IDS/OWNER_NAMES)는 여기서 절대 하드코딩하지 않는다.
     *   모든 DATA 셀은 항상 OWNER_IDS=NULL 상태로 생성되며, 담당자 배정은
     *   전적으로 관리자가 '컬럼관리(CellAuth)' 화면에서 설정한다.
     *   설정 즉시 {@link CellOwnershipSyncService}가 daily_report_cell_auth →
     *   daily_report_cell.OWNER_IDS/OWNER_NAMES 로 동기화한다.
     *
     * ★ freqCode/freqLabel도 null로 호출 가능하다 — 아직 관리자가 컬럼관리에서
     *   주기(CellAuth)를 지정하지 않은 신규 DATA 셀은 담당자와 마찬가지로
     *   주기 미지정(NULL) 상태로 생성하고, 관리자가 배정하는 즉시 CellAuth
     *   기준으로 반영된다 (CellService.isCellEditableForUser()가 CellAuth를
     *   단일 소스로 판단하므로, 이 셀 자체의 freqCode는 표시용일 뿐이다).
     */
    private static void d(DailyReportTable t, int row, int col, String coord,
                           String freqCode, String freqLabel) {
        t.addCell(DailyReportCell.builder()
                .rowIndex(row).colIndex(col).excelCoord(coord)
                .cellValue("").cellType("DATA")
                .isLocked(false).rowSpan(1).colSpan(1)
                .freqCode(freqCode).freqLabel(freqLabel)
                .ownerIds(null).ownerNames(null)
                .build());
    }

    /**
     * DATA 셀 (입력 가능, rowSpan/colSpan 지정)
     *
     * ★ 담당자는 하드코딩하지 않는다 — 위 1-argument 버전 주석 참조.
     */
    private static void d(DailyReportTable t, int row, int col, String coord,
                           String freqCode, String freqLabel,
                           int rs, int cs) {
        t.addCell(DailyReportCell.builder()
                .rowIndex(row).colIndex(col).excelCoord(coord)
                .cellValue("").cellType("DATA")
                .isLocked(false).rowSpan(rs).colSpan(cs)
                .freqCode(freqCode).freqLabel(freqLabel)
                .ownerIds(null).ownerNames(null)
                .build());
    }
}
