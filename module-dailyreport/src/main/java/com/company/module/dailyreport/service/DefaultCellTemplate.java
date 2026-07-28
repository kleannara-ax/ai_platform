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
     * ★ 표1/표2 "진짜 값 롤링" 기능 시작일 — 실측 데이터는 이 날짜 이후부터만
     *   존재할 수 있다 (해당 서비스가 실제로 개발/운영 시작된 날짜).
     *
     * - 롤링 대상 월의 월말(monthEnd)이 이 날짜보다 이전이면, 그 달은 실측
     *   조회 자체를 시도하지 않고 곧바로 하드코딩 샘플(anchor 매핑값)로 대체한다.
     * - 월말이 이 날짜 이후(또는 같음)면, 실측 조회 범위를
     *   [max(월초, FEATURE_CUTOFF_DATE), 월말] 로 제한해서 조회하고,
     *   실측이 없으면 마찬가지로 하드코딩 샘플로 대체한다.
     */
    public static final LocalDate FEATURE_CUTOFF_DATE = LocalDate.of(2026, 7, 22);

    /**
     * ★ 롤링 실측값 조회 콜백 — DailyReportService가 DailyReportCellRepository를
     *   이용해 구현체를 넘겨준다 (DefaultCellTemplate은 정적 유틸이라 리포지토리를
     *   직접 주입받을 수 없으므로, 호출부가 조회 로직을 람다로 넘기는 구조).
     *
     * @return 해당 (표코드, 행, 실측(라이브) 열, 대상 연월)의 월말 대표값(누적값).
     *         실측 데이터가 아직 없으면 null.
     */
    @FunctionalInterface
    public interface HistoricalValueLookup {
        String find(String tableCode, int rowIndex, int liveColIndex, YearMonth targetMonth);
    }

    /**
     * 테이블 코드에 맞는 기본 셀을 생성하여 table 엔티티에 추가한다.
     * @param table 대상 테이블 엔티티
     * @param reportDate 일보 날짜 (헤더 롤링 월 계산용)
     * @param lookup 과거 달의 실측(월말 대표값) 조회 콜백 — null이면 항상
     *               하드코딩 샘플/"일보 없음"만 사용 (실측 조회 시도 안 함)
     */
    public static void populateDefaultCells(DailyReportTable table, LocalDate reportDate,
                                             HistoricalValueLookup lookup) {
        String code = table.getTableCode();
        switch (code) {
            case "TBL_PRODUCTION_INDEX" -> addProductionIndexCells(table, reportDate, lookup);
            case "TBL_INVENTORY"        -> addInventoryCells(table, reportDate, lookup);
            case "TBL_ENERGY"           -> addEnergyCells(table, reportDate);
            case "TBL_BOILER"           -> addBoilerCells(table, reportDate, lookup);
            default -> { /* unknown table code — skip */ }
        }
    }

    /** 하위 호환용 (lookup 없이 호출 시 실측 조회 없이 하드코딩 샘플/"일보 없음"만 사용) */
    public static void populateDefaultCells(DailyReportTable table, LocalDate reportDate) {
        populateDefaultCells(table, reportDate, null);
    }

    /** 하위 호환용 (reportDate/lookup 없이 호출 시 오늘 날짜 기준, 실측 조회 없음) */
    public static void populateDefaultCells(DailyReportTable table) {
        populateDefaultCells(table, LocalDate.now(), null);
    }

    /**
     * ★ 롤링 실측값 해석 — 대상 연월의 월말이 커트오프 이후일 때만 실측 조회를
     *   시도하고, 실측이 없으면(또는 커트오프 이전 달이면) 하드코딩 샘플
     *   (해당 연월 귀속값)로 대체한다. 그마저 없으면 "-".
     */
    private static String resolveHistoricalValue(HistoricalValueLookup lookup, String tableCode,
                                                   int rowIndex, int liveColIndex, YearMonth targetMonth,
                                                   String hardcodedFallback) {
        LocalDate monthEnd = targetMonth.atEndOfMonth();
        if (lookup != null && !monthEnd.isBefore(FEATURE_CUTOFF_DATE)) {
            String realValue = lookup.find(tableCode, rowIndex, liveColIndex, targetMonth);
            if (realValue != null && !realValue.isBlank()) {
                return realValue;
            }
        }
        return hardcodedFallback != null ? hardcodedFallback : "-";
    }

    /** 엑셀 열 문자 (A=col-1 오프셋, 실제로는 col+1번째 문자가 해당 엑셀 열) */
    private static final String EXCEL_COLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * ★ 하드코딩 샘플이 처음 작성되었을 때 가정한 "기준월(anchor month)" — 2026년 7월.
     * 표1/표2의 과거 7개 컬럼 하드코딩 리터럴 값은 이 기준월의 직전 7개월
     * (2025-12 ~ 2026-06)에 대응한다. 실측 데이터가 없을 때 폴백으로 쓰기 위해
     * 값을 리터럴이 아니라 "그 값이 원래 어느 연월의 값이었는지" 캘린더 월에
     * 재귀속시켜 두어야, 롤링 윈도우가 이동해도 항상 올바른 연월의 폴백값을
     * 찾을 수 있다.
     */
    private static final YearMonth ANCHOR_MONTH = YearMonth.of(2026, 7);

    /** anchor 기준 직근 8개월 목록 (index 0=anchor-7 ~ index 7=anchor) — 캐시 */
    private static List<YearMonth> anchorRollingMonths() {
        return rollingMonths(ANCHOR_MONTH);
    }

    /** 특정 currentMonth 기준 직근 8개월 목록 계산 (index 0=current-7 ~ index 7=current) */
    private static List<YearMonth> rollingMonths(YearMonth current) {
        List<YearMonth> rolling = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            rolling.add(current.minusMonths(i));
        }
        return rolling;
    }

    /**
     * anchor 기준 과거 7개월(oldest→newest 순)에 하드코딩 값 7개를 매핑해
     * "연월 → 폴백값" 맵을 만든다.
     */
    private static Map<YearMonth, String> anchorFallbackMap(String... valuesOldestToNewest7) {
        List<YearMonth> anchorRolling = anchorRollingMonths();
        Map<YearMonth, String> map = new LinkedHashMap<>();
        for (int i = 0; i < 7 && i < valuesOldestToNewest7.length; i++) {
            map.put(anchorRolling.get(i), valuesOldestToNewest7[i]);
        }
        return map;
    }

    /**
     * ★ 롤링 과거 컬럼의 값 결정 — 표1/표2 공용.
     * 1) 대상 연월의 월말이 FEATURE_CUTOFF_DATE 이후 → 실측 조회(lookup) 시도
     *    → 있으면 실측값 사용
     * 2) 커트오프 이전이거나 실측이 없으면 → anchor 기준 하드코딩 샘플
     *    (fallbackMap)에서 해당 연월 값 사용
     * 3) 그마저 없으면 "-"
     */
    private static String rollingValue(HistoricalValueLookup lookup, String tableCode,
                                        int rowIndex, int liveColIndex, YearMonth targetMonth,
                                        Map<YearMonth, String> fallbackMap) {
        return resolveHistoricalValue(lookup, tableCode, rowIndex, liveColIndex, targetMonth,
                fallbackMap.get(targetMonth));
    }

    /**
     * ★ 롤링 과거 컬럼 한 행(row) 전체를 채우는 공용 헬퍼 — 표1/표2 공용.
     *
     * @param row 행 인덱스
     * @param startCol 과거 첫 컬럼의 colIndex (표1=6, 표2=4)
     * @param coords 과거 컬럼들의 엑셀좌표 (oldest→newest 순, 보통 7개)
     * @param rolling 전체 8개월 롤링 목록 (index 0=oldest ~ index 7=최신(라이브))
     *                — 과거 컬럼은 index 0..coords.length-1 만 사용한다
     * @param tableCode 대상 표코드
     * @param liveCol 이 행의 라이브(실측 입력) DATA 컬럼 colIndex
     * @param lookup 실측값 조회 콜백 (null 가능)
     * @param fallbackMap anchor(기준월) 기준 연월→하드코딩 샘플값 맵 —
     *        {@link #anchorFallbackMap(String...)}로 미리 만들어 전달한다.
     *        (표1의 경우 '24/'25년 월평균 계산에도 동일한 맵을 재사용한다)
     */
    private static void addHistoricalRollingRow(DailyReportTable t, int row, int startCol,
                                                  String[] coords, List<YearMonth> rolling,
                                                  String tableCode, int liveCol,
                                                  HistoricalValueLookup lookup,
                                                  Map<YearMonth, String> fallbackMap) {
        for (int i = 0; i < coords.length; i++) {
            String value = rollingValue(lookup, tableCode, row, liveCol, rolling.get(i), fallbackMap);
            ro(t, row, startCol + i, coords[i], value);
        }
    }

    /**
     * ★ 연도별 월평균 산정 — 표1 F/G열('24년/'25년 월평균) 전용.
     *
     * - targetYear가 ANCHOR_MONTH의 연도(2026)보다 이전이면, 그 해는 월별 데이터
     *   (실측도 하드코딩도) 자체가 없으므로 기존처럼 완성된 평균 리터럴값을
     *   그대로 사용한다 (변경 없음 — 예: '25년 587, '24년 583.5는 항상 고정).
     * - targetYear가 2026 이상이면, 해당 연도 1~12월 각 달의 값을 아래 순서로
     *   구해 합산한 뒤 ÷12 (소수점 1자리 반올림)한다. 하드코딩이든 실측이든
     *   구분 없이 "그 달에 저장된 값"을 그대로 더하는 방식이다:
     *     1) anchor 하드코딩 폴백맵에 있는 달(2025-12~2026-06) → 그 값 사용
     *     2) 그 외(2026-07 이후, 또는 2027년 이후 전체) → DB 실측 조회,
     *        값이 없으면 0으로 간주 (요청: "데이터 공백 시 0")
     */
    private static String yearlyAverage(HistoricalValueLookup lookup, String tableCode,
                                         int rowIndex, int liveColIndex, int targetYear,
                                         Map<YearMonth, String> fallbackMap, String literalAverage) {
        if (targetYear < ANCHOR_MONTH.getYear()) {
            return literalAverage;
        }
        double sum = 0;
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(targetYear, month);
            String raw = fallbackMap.get(ym);
            if (raw == null && lookup != null) {
                raw = lookup.find(tableCode, rowIndex, liveColIndex, ym);
            }
            sum += parseNumberOrZero(raw);
        }
        return formatOneDecimal(sum / 12.0);
    }

    /** 숫자로 해석 불가능하거나(빈 값, "-", null 등) 데이터가 없는 경우 0으로 간주 */
    private static double parseNumberOrZero(String raw) {
        if (raw == null) return 0;
        String cleaned = raw.replace(",", "").trim();
        if (cleaned.isEmpty() || "-".equals(cleaned)) return 0;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 소수점 1자리까지 반올림 후, 정수형이면 ".0" 접미사를 제거해 기존 하드코딩 값들과 표기 스타일을 맞춘다 */
    private static String formatOneDecimal(double v) {
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(v).setScale(1, java.math.RoundingMode.HALF_UP);
        String s = bd.toPlainString();
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    // ═══════════════════════════════════════════════
    //  1. 주요 생산 지표 현황  (10행 × 15열)
    // ═══════════════════════════════════════════════
    private static void addProductionIndexCells(DailyReportTable t, LocalDate reportDate,
                                                  HistoricalValueLookup lookup) {
        // ── 롤링 월 계산: reportDate 기준 직근 8개월 ──
        // 예) 2026-07 → 2025-12 ~ 2026-07
        //     2026-08 → 2026-01 ~ 2026-08
        //     2027-01 → 2026-06 ~ 2027-01
        YearMonth current = YearMonth.from(reportDate);
        List<YearMonth> rolling = rollingMonths(current);
        final String tableCode = t.getTableCode();
        final int liveCol = 13; // O열 — 실측(라이브 입력) 컬럼은 항상 col13

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

        // 과거 7개월 컬럼(colIdx 6~12)의 엑셀열 접두문자 (H~N)
        String[] histCols = {"H", "I", "J", "K", "L", "M", "N"};

        // ── Row 2: 제지3 평균선속 ──
        Map<YearMonth, String> fb7 = anchorFallbackMap("588", "597", "584", "577", "597", "597", "594");
        ro(t, 2, 0, "B7",  "제지3 평균선속(m/분)", 1, 3);
        ro(t, 2, 3, "E7",  "640");
        ro(t, 2, 4, "F7",  yearlyAverage(lookup, tableCode, 2, liveCol, prevYear2, fb7, "583.5"));
        ro(t, 2, 5, "G7",  yearlyAverage(lookup, tableCode, 2, liveCol, prevYear1, fb7, "587"));
        addHistoricalRollingRow(t, 2, 6, coordsForRow(histCols, 7), rolling, tableCode, liveCol, lookup, fb7);
        d(t,  2,13, "O7",  null, null);
        d(t,  2,14, "P7",  "daily", "매일");

        // ── Row 3: 초지5 생산량 ──
        Map<YearMonth, String> fb8 = anchorFallbackMap("83.5", "80.4", "85.6", "79.9", "83.6", "83", "79.5");
        ro(t, 3, 0, "B8",  "초지5 생산량(톤/日)", 1, 3);
        ro(t, 3, 3, "E8",  "85");
        ro(t, 3, 4, "F8",  yearlyAverage(lookup, tableCode, 3, liveCol, prevYear2, fb8, "83.8"));
        ro(t, 3, 5, "G8",  yearlyAverage(lookup, tableCode, 3, liveCol, prevYear1, fb8, "76"));
        addHistoricalRollingRow(t, 3, 6, coordsForRow(histCols, 8), rolling, tableCode, liveCol, lookup, fb8);
        d(t,  3,13, "O8",  null, null);
        d(t,  3,14, "P8",  "daily", "매일");

        // ── Row 4: 수율 - PS 완제품 ──
        Map<YearMonth, String> fb9 = anchorFallbackMap("98.6", "98.7", "97.2", "101.5", "101.8", "99.8", "98.7");
        ro(t, 4, 0, "B9",  "수율(%)", 3, 1);
        ro(t, 4, 1, "C9",  "PS",      2, 1);
        ro(t, 4, 2, "D9",  "완제품",  1, 1);
        ro(t, 4, 3, "E9",  "91");
        ro(t, 4, 4, "F9",  yearlyAverage(lookup, tableCode, 4, liveCol, prevYear2, fb9, "97.7"));
        ro(t, 4, 5, "G9",  yearlyAverage(lookup, tableCode, 4, liveCol, prevYear1, fb9, "99.2"));
        addHistoricalRollingRow(t, 4, 6, coordsForRow(histCols, 9), rolling, tableCode, liveCol, lookup, fb9);
        d(t,  4,13, "O9",  "event", "발생 시");
        d(t,  4,14, "P9",  "daily", "매일");

        // ── Row 5: 수율 - PS 코팅제외 ──
        Map<YearMonth, String> fb10 = anchorFallbackMap("84.1", "84.6", "83.5", "87.6", "88.2", "86.3", "84.7");
        ro(t, 5, 2, "D10", "코팅제외", 1, 1);
        ro(t, 5, 3, "E10", "78");
        ro(t, 5, 4, "F10", yearlyAverage(lookup, tableCode, 5, liveCol, prevYear2, fb10, "83.8"));
        ro(t, 5, 5, "G10", yearlyAverage(lookup, tableCode, 5, liveCol, prevYear1, fb10, "85.2"));
        addHistoricalRollingRow(t, 5, 6, coordsForRow(histCols, 10), rolling, tableCode, liveCol, lookup, fb10);
        d(t,  5,13, "O10", "event", "발생 시");
        d(t,  5,14, "P10", "daily", "매일");

        // ── Row 6: 수율 - 화장지 ──
        Map<YearMonth, String> fb11 = anchorFallbackMap("61.1", "63.3", "63.6", "63.6", "69.6", "74.6", "74.4");
        ro(t, 6, 1, "C11", "화장지", 1, 2);
        ro(t, 6, 3, "E11", "63.5");
        ro(t, 6, 4, "F11", yearlyAverage(lookup, tableCode, 6, liveCol, prevYear2, fb11, "63.5"));
        ro(t, 6, 5, "G11", yearlyAverage(lookup, tableCode, 6, liveCol, prevYear1, fb11, "64.6"));
        addHistoricalRollingRow(t, 6, 6, coordsForRow(histCols, 11), rolling, tableCode, liveCol, lookup, fb11);
        d(t,  6,13, "O11", "event", "발생 시");
        d(t,  6,14, "P11", "daily", "매일");

        // ── Row 7: 고지감량율 ──
        Map<YearMonth, String> fb12 = anchorFallbackMap("12.7", "11.2", "11.8", "13", "14.2", "15.9", "16");
        ro(t, 7, 0, "B12", "고지감량율(%)", 1, 3);
        ro(t, 7, 3, "E12", "-");
        ro(t, 7, 4, "F12", yearlyAverage(lookup, tableCode, 7, liveCol, prevYear2, fb12, "15.8"));
        ro(t, 7, 5, "G12", yearlyAverage(lookup, tableCode, 7, liveCol, prevYear1, fb12, "14.8"));
        addHistoricalRollingRow(t, 7, 6, coordsForRow(histCols, 12), rolling, tableCode, liveCol, lookup, fb12);
        d(t,  7,13, "O12", null, null);
        d(t,  7,14, "P12", "daily", "매일");

        // ── Row 8: 슬러지원단위 - 제지 ──
        Map<YearMonth, String> fb13 = anchorFallbackMap("94", "99", "104", "96", "84", "82", "84");
        ro(t, 8, 0, "B13", "슬러지원단위", 2, 2);
        ro(t, 8, 2, "D13", "제   지", 1, 1);
        d(t,  8, 3, "E13", "yearly", "매년");
        ro(t, 8, 4, "F13", yearlyAverage(lookup, tableCode, 8, liveCol, prevYear2, fb13, "89"));
        ro(t, 8, 5, "G13", yearlyAverage(lookup, tableCode, 8, liveCol, prevYear1, fb13, "91"));
        addHistoricalRollingRow(t, 8, 6, coordsForRow(histCols, 13), rolling, tableCode, liveCol, lookup, fb13);
        d(t,  8,13, "O13", "event", "발생 시");
        d(t,  8,14, "P13", "daily", "매일");

        // ── Row 9: 슬러지원단위 - 화장지 ──
        Map<YearMonth, String> fb14 = anchorFallbackMap("81", "58", "68", "50", "46", "53", "62");
        ro(t, 9, 2, "D14", "화장지", 1, 1);
        d(t,  9, 3, "E14", "yearly", "매년");
        ro(t, 9, 4, "F14", yearlyAverage(lookup, tableCode, 9, liveCol, prevYear2, fb14, "76"));
        ro(t, 9, 5, "G14", yearlyAverage(lookup, tableCode, 9, liveCol, prevYear1, fb14, "64"));
        addHistoricalRollingRow(t, 9, 6, coordsForRow(histCols, 14), rolling, tableCode, liveCol, lookup, fb14);
        d(t,  9,13, "O14", "event", "발생 시");
        d(t,  9,14, "P14", "daily", "매일");
    }

    /**
     * ★ 보일러(표4) 전용 2개월 anchor 폴백맵 — 표1/표2의 7개월짜리
     * {@link #anchorFallbackMap(String...)}와 달리, 보일러는 M(전전월)/N(전월)
     * 2개 컬럼만 과거값을 가지므로 ANCHOR_MONTH 기준 -2개월/-1개월에
     * 하드코딩 값 2개만 매핑한다.
     */
    private static Map<YearMonth, String> boilerAnchorFallback(String m2Value, String m1Value) {
        Map<YearMonth, String> map = new LinkedHashMap<>();
        map.put(ANCHOR_MONTH.minusMonths(2), m2Value);
        map.put(ANCHOR_MONTH.minusMonths(1), m1Value);
        return map;
    }

    /** 엑셀열 접두배열 + 행번호 → {"H7","I7",...,"N7"} 형태의 엑셀좌표 배열 생성 */
    private static String[] coordsForRow(String[] colLetters, int excelRow) {
        String[] result = new String[colLetters.length];
        for (int i = 0; i < colLetters.length; i++) {
            result[i] = colLetters[i] + excelRow;
        }
        return result;
    }

    // ═══════════════════════════════════════════════
    //  2. 제지 재공품 및 야적현황  (10행 × 13열)
    // ═══════════════════════════════════════════════
    private static void addInventoryCells(DailyReportTable t, LocalDate reportDate,
                                           HistoricalValueLookup lookup) {
        // ── 롤링 월 계산: reportDate 기준 직근 8개월 (표1과 동일한 규칙) ──
        // 과거 7개월(index 0~6, colIdx 4~10) + 라이브(현재)달 1개(index 7, colIdx 11)
        YearMonth current = YearMonth.from(reportDate);
        List<YearMonth> rolling = rollingMonths(current);
        final String tableCode = t.getTableCode();
        final int liveCol = 11; // M열 — 실측(라이브 입력) 컬럼은 항상 col11

        // 연도별 그룹핑 (Row 0 대 헤더의 colspan 계산용) — colIdx 4~11(8개월) 대상
        Map<Integer, int[]> yearGroups = new LinkedHashMap<>(); // year → [startCol, count]
        for (int i = 0; i < rolling.size(); i++) {
            int yr = rolling.get(i).getYear();
            final int col = 4 + i;
            yearGroups.computeIfAbsent(yr, k -> new int[]{col, 0});
            yearGroups.get(yr)[1]++;
        }

        // ── Row 0: 대 헤더 (고정부) ──
        h(t, 0, 0, "B19", "구 분",        2, 2);
        h(t, 0, 2, "D19", "기준",          2, 1);
        h(t, 0, 3, "E19", "적정재고",      2, 1);

        // ── Row 0: 대 헤더 (롤링 연도 그룹) ──
        for (Map.Entry<Integer, int[]> entry : yearGroups.entrySet()) {
            int yr = entry.getKey();
            int startCol = entry.getValue()[0];
            int count = entry.getValue()[1];
            String coord = String.valueOf(EXCEL_COLS.charAt(startCol + 1)) + "19";
            h(t, 0, startCol, coord, "'" + String.valueOf(yr).substring(2) + "년", 1, count);
        }
        // ★ "비고" 헤더는 아래 행(Row1)의 헤더 셀과 세로로 병합 (rowSpan=2)
        h(t, 0,12, "N19", "비 고",         2, 1);

        // ── Row 1: 소 헤더 (롤링 월) ──
        for (int i = 0; i < rolling.size(); i++) {
            int colIdx = 4 + i;
            String coord = String.valueOf(EXCEL_COLS.charAt(colIdx + 1)) + "20";
            h(t, 1, colIdx, coord, rolling.get(i).getMonthValue() + "월", 1, 1);
        }
        // ★ N20은 위 N19("비고")와 병합되어 흡수됨 — 별도 헤더 셀 생성 안 함

        // 과거 7개월 컬럼(colIdx 4~10)의 엑셀열 접두문자 (F~L)
        String[] histCols = {"F", "G", "H", "I", "J", "K", "L"};

        // ── Row 2: 밀롤창고 ──
        ro(t, 2, 0, "B21", "제지 재공품", 4, 1);
        ro(t, 2, 1, "C21", "밀롤창고",    1, 1);
        ro(t, 2, 2, "D21", "톤",           4, 1);
        d(t,  2, 3, "E21", "event", "발생 시");
        addHistoricalRollingRow(t, 2, 4, coordsForRow(histCols, 21), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("3826", "3043", "3296", "2196", "3037", "3711", "3006"));
        d(t,  2,11, "M21", null, null);
        d(t,  2,12, "N21", "daily", "매일");

        // ── Row 3: 카타대기 ──
        ro(t, 3, 1, "C22", "카타대기", 1, 1);
        d(t,  3, 3, "E22", "event", "발생 시");
        addHistoricalRollingRow(t, 3, 4, coordsForRow(histCols, 22), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("320", "315", "549", "648", "1360", "1121", "1110"));
        d(t,  3,11, "M22", null, null);
        d(t,  3,12, "N22", "daily", "매일");

        // ── Row 4: 미포장 ──
        ro(t, 4, 1, "C23", "미포장", 1, 1);
        d(t,  4, 3, "E23", "event", "발생 시");
        addHistoricalRollingRow(t, 4, 4, coordsForRow(histCols, 23), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("212", "764", "702", "149", "86", "173", "266"));
        d(t,  4,11, "M23", null, null);
        d(t,  4,12, "N23", "daily", "매일");

        // ── Row 5: 포장후 물류입고전 ──
        ro(t, 5, 1, "C24", "포장후 물류입고전", 1, 1);
        d(t,  5, 3, "E24", "event", "발생 시");
        addHistoricalRollingRow(t, 5, 4, coordsForRow(histCols, 24), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("83", "139", "151", "88", "58", "288", "423"));
        d(t,  5,11, "M24", null, null);
        d(t,  5,12, "N24", "daily", "매일");

        // ── Row 6: 장기재고 3개월 초과 ──
        ro(t, 6, 0, "B25", "장기재고",      2, 1);
        ro(t, 6, 1, "C25", "3개월 초과",    1, 1);
        ro(t, 6, 2, "D25", "톤",            1, 1);
        ro(t, 6, 3, "E25", "0");
        addHistoricalRollingRow(t, 6, 4, coordsForRow(histCols, 25), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("4354", "4372", "4005", "4236", "3761", "3404", "3120"));
        d(t,  6,11, "M25", "monthly", "매월");
        d(t,  6,12, "N25", "daily", "매일");

        // ── Row 7: 장기재고 6개월 초과 ──
        ro(t, 7, 1, "C26", "6개월 초과", 1, 1);
        ro(t, 7, 2, "D26", "톤",          1, 1);
        ro(t, 7, 3, "E26", "0");
        addHistoricalRollingRow(t, 7, 4, coordsForRow(histCols, 26), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("917", "980", "786", "915", "957", "1543", "1130"));
        d(t,  7,11, "M26", "monthly", "매월");
        d(t,  7,12, "N26", "daily", "매일");

        // ── Row 8: 야적현황 - 제지 ──
        ro(t, 8, 0, "B27", "야적현황", 2, 1);
        ro(t, 8, 1, "C27", "제지",     1, 1);
        ro(t, 8, 2, "D27", "톤",       1, 1);
        ro(t, 8, 3, "E27", "0");
        addHistoricalRollingRow(t, 8, 4, coordsForRow(histCols, 27), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("489", "239", "0", "0", "0", "0", "0"));
        d(t,  8,11, "M27", null, null);
        d(t,  8,12, "N27", "daily", "매일");

        // ── Row 9: 야적현황 - 생활 ──
        ro(t, 9, 1, "C28", "생활",     1, 1);
        ro(t, 9, 2, "D28", "팔레트",   1, 1);
        ro(t, 9, 3, "E28", "0");
        addHistoricalRollingRow(t, 9, 4, coordsForRow(histCols, 28), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("0", "0", "0", "0", "0", "0", "0"));
        d(t,  9,11, "M28", null, null);
        d(t,  9,12, "N28", "daily", "매일");
    }

    // ═══════════════════════════════════════════════
    //  3. 에너지 원단위  (8행 × 6열)
    // ═══════════════════════════════════════════════
    private static void addEnergyCells(DailyReportTable t, LocalDate reportDate) {
        // ── 헤더 월 롤링: reportDate 기준 "전월 실적" / "당월 현재" ──
        YearMonth current = YearMonth.from(reportDate);
        YearMonth prevMonth = current.minusMonths(1);

        // ── Row 0: 대 헤더 ──
        h(t, 0, 0, "B34", "구분",       2, 2);
        h(t, 0, 2, "D34", "목표",       2, 1);
        h(t, 0, 3, "E34", prevMonth.getMonthValue() + "월 실적",   2, 1);
        h(t, 0, 4, "F34", current.getMonthValue() + "월 현재",     1, 2);

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
    private static void addBoilerCells(DailyReportTable t, LocalDate reportDate,
                                        HistoricalValueLookup lookup) {
        // ── 헤더 월 롤링: reportDate 기준 "당월 단가" / "전전월 실적" / "전월 실적" / "당월" ──
        final String tableCode = t.getTableCode();
        final int liveCol = 6; // P열 — 실측(라이브 입력) 컬럼은 항상 col6
        YearMonth current = YearMonth.from(reportDate);
        YearMonth mMinus2 = current.minusMonths(2);
        YearMonth mMinus1 = current.minusMonths(1);

        // ── Row 0: 대 헤더 ──
        h(t, 0, 0, "J34", "구분",              2, 1);
        h(t, 0, 1, "K34", "목표",              2, 1);
        h(t, 0, 2, "L34", current.getMonthValue() + "월단가\n(천원/톤)", 2, 1);
        h(t, 0, 3, "M34", mMinus2.getMonthValue() + "월 실적",          2, 1);
        h(t, 0, 4, "N34", mMinus1.getMonthValue() + "월 실적",          2, 1);
        h(t, 0, 5, "O34", current.getMonthValue() + "월",               1, 2);
        h(t, 0, 7, "Q34", "비 고",             2, 1);

        // ── Row 1: 소 헤더 ──
        h(t, 1, 5, "O35", "계 획", 1, 1);
        h(t, 1, 6, "P35", "실 적", 1, 1);

        // ── Row 2: LNG보일러 ──
        Map<YearMonth, String> fbLng = boilerAnchorFallback("2.4", "0.4");
        ro(t, 2, 0, "J36", "LNG보일러", 1, 1);
        d(t,  2, 1, "K36", "yearly",  "매년");
        d(t,  2, 2, "L36", "yearly",  "매년");
        ro(t, 2, 3, "M36", rollingValue(lookup, tableCode, 2, liveCol, mMinus2, fbLng));
        ro(t, 2, 4, "N36", rollingValue(lookup, tableCode, 2, liveCol, mMinus1, fbLng));
        d(t,  2, 5, "O36", "monthly", "매월");
        d(t,  2, 6, "P36", "daily",   "매일");
        d(t,  2, 7, "Q36", "daily", "매일", 5, 1);

        // ── Row 3: 유동상소각로 ──
        Map<YearMonth, String> fbFluid = boilerAnchorFallback("15.3", "14.7");
        ro(t, 3, 0, "J37", "유동상소각로", 1, 1);
        d(t,  3, 1, "K37", "yearly",  "매년");
        d(t,  3, 2, "L37", "yearly",  "매년");
        ro(t, 3, 3, "M37", rollingValue(lookup, tableCode, 3, liveCol, mMinus2, fbFluid));
        ro(t, 3, 4, "N37", rollingValue(lookup, tableCode, 3, liveCol, mMinus1, fbFluid));
        d(t,  3, 5, "O37", "monthly", "매월");
        d(t,  3, 6, "P37", "daily",   "매일");

        // ── Row 4: 복합보일러 ──
        Map<YearMonth, String> fbComplex = boilerAnchorFallback("56.8", "52.5");
        ro(t, 4, 0, "J38", "복합보일러", 1, 1);
        d(t,  4, 1, "K38", "yearly",  "매년");
        d(t,  4, 2, "L38", "yearly",  "매년");
        ro(t, 4, 3, "M38", rollingValue(lookup, tableCode, 4, liveCol, mMinus2, fbComplex));
        ro(t, 4, 4, "N38", rollingValue(lookup, tableCode, 4, liveCol, mMinus1, fbComplex));
        d(t,  4, 5, "O38", "monthly", "매월");
        d(t,  4, 6, "P38", "daily",   "매일");

        // ── Row 5: 폐합성소각로 ──
        Map<YearMonth, String> fbWaste = boilerAnchorFallback("10.2", "11.6");
        ro(t, 5, 0, "J39", "폐합성소각로", 1, 1);
        d(t,  5, 1, "K39", "yearly",  "매년");
        d(t,  5, 2, "L39", "yearly",  "매년");
        ro(t, 5, 3, "M39", rollingValue(lookup, tableCode, 5, liveCol, mMinus2, fbWaste));
        ro(t, 5, 4, "N39", rollingValue(lookup, tableCode, 5, liveCol, mMinus1, fbWaste));
        d(t,  5, 5, "O39", "monthly", "매월");
        d(t,  5, 6, "P39", "daily",   "매일");

        // ── Row 6: 합계 ──
        Map<YearMonth, String> fbTotal = boilerAnchorFallback("84.7", "79.2");
        ro(t, 6, 0, "J40", "합  계", 1, 1);
        d(t,  6, 1, "K40", "yearly",  "매년");
        d(t,  6, 2, "L40", "yearly",  "매년");
        ro(t, 6, 3, "M40", rollingValue(lookup, tableCode, 6, liveCol, mMinus2, fbTotal));
        ro(t, 6, 4, "N40", rollingValue(lookup, tableCode, 6, liveCol, mMinus1, fbTotal));
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
