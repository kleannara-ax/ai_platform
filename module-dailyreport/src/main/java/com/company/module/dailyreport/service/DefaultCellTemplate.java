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
     * ★ 표7(연도별 추이) 전용 롤링 실측값 조회 콜백 — 위 {@link HistoricalValueLookup}과
     *   원리는 같지만 조회 단위가 "연도"이다 (표7의 각 컬럼은 1개월이 아니라 1개 연도를
     *   대표하는 값이므로, 그 연도 전체 기간(1/1~12/31) 안에서 가장 최근 값을 찾는다).
     *
     * @return 해당 (표코드, 행, 실측(라이브) 열, 대상 연도)의 연말 대표값(연간 누적값).
     *         실측 데이터가 아직 없으면 null.
     */
    @FunctionalInterface
    public interface HistoricalYearlyValueLookup {
        String find(String tableCode, int rowIndex, int liveColIndex, int targetYear);
    }

    /**
     * 테이블 코드에 맞는 기본 셀을 생성하여 table 엔티티에 추가한다.
     * @param table 대상 테이블 엔티티
     * @param reportDate 일보 날짜 (헤더 롤링 월/연도 계산용)
     * @param lookup 과거 달의 실측(월말 대표값) 조회 콜백 — null이면 항상
     *               하드코딩 샘플/"일보 없음"만 사용 (실측 조회 시도 안 함)
     * @param yearlyLookup 표7(연도별 추이) 전용 — 과거 연도의 실측(연말 대표값)
     *               조회 콜백. null이면 항상 "-"만 사용 (실측 조회 시도 안 함)
     */
    public static void populateDefaultCells(DailyReportTable table, LocalDate reportDate,
                                             HistoricalValueLookup lookup,
                                             HistoricalYearlyValueLookup yearlyLookup) {
        String code = table.getTableCode();
        switch (code) {
            case "TBL_PRODUCTION_INDEX" -> addProductionIndexCells(table, reportDate, lookup);
            case "TBL_INVENTORY"        -> addInventoryCells(table, reportDate, lookup);
            case "TBL_ENERGY"           -> addEnergyCells(table, reportDate);
            case "TBL_BOILER"           -> addBoilerCells(table, reportDate, lookup);
            case "TBL_SAFETY_INCIDENT_COUNT"  -> addSafetyIncidentCountCells(table, reportDate, lookup);
            case "TBL_SAFETY_INCIDENT_AMOUNT" -> addSafetyIncidentAmountCells(table, reportDate, lookup);
            case "TBL_SAFETY_YEARLY_TREND"    -> addSafetyYearlyTrendCells(table, reportDate, yearlyLookup);
            case "TBL_SAFETY_MONTHLY_TREND"   -> addSafetyMonthlyTrendCells(table, reportDate, lookup);
            default -> { /* unknown table code — skip */ }
        }
    }

    /** 하위 호환용 (yearlyLookup 없이 호출 시 표7 실측 조회 없이 "-"만 사용) */
    public static void populateDefaultCells(DailyReportTable table, LocalDate reportDate,
                                             HistoricalValueLookup lookup) {
        populateDefaultCells(table, reportDate, lookup, null);
    }

    /** 하위 호환용 (lookup 없이 호출 시 실측 조회 없이 하드코딩 샘플/"일보 없음"만 사용) */
    public static void populateDefaultCells(DailyReportTable table, LocalDate reportDate) {
        populateDefaultCells(table, reportDate, null, null);
    }

    /** 하위 호환용 (reportDate/lookup 없이 호출 시 오늘 날짜 기준, 실측 조회 없음) */
    public static void populateDefaultCells(DailyReportTable table) {
        populateDefaultCells(table, LocalDate.now(), null, null);
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
        return rollingMonths(current, 8);
    }

    /**
     * ★ 윈도우 크기를 파라미터화한 롤링 개월 목록 계산 (표8=16개월 전용으로 추가, 2026-09).
     * index 0 = current-(windowSize-1) (가장 오래된 달) ~ index windowSize-1 = current(라이브)
     */
    private static List<YearMonth> rollingMonths(YearMonth current, int windowSize) {
        List<YearMonth> rolling = new ArrayList<>();
        for (int i = windowSize - 1; i >= 0; i--) {
            rolling.add(current.minusMonths(i));
        }
        return rolling;
    }

    /**
     * ★ 연도 단위 롤링 목록 계산 (표7 전용, 2026-09 추가) — 위 rollingMonths와 동일한
     * 원리를 "연도" 단위로 적용한다. index 0 = currentYear-(windowSize-1) (가장 오래된 연도)
     * ~ index windowSize-1 = currentYear(라이브).
     */
    private static List<Integer> rollingYears(int currentYear, int windowSize) {
        List<Integer> years = new ArrayList<>();
        for (int i = windowSize - 1; i >= 0; i--) {
            years.add(currentYear - i);
        }
        return years;
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
        h(t, 0, 3, "E5",  "최종\n목표",     2, 1);
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
        // "최종목표"(E5)는 위에서 rowSpan=2로 세로 병합했으므로 E6는 별도 헤더 생성 안 함
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
        d(t,  8, 3, "E13", "daily", "매일");
        ro(t, 8, 4, "F13", yearlyAverage(lookup, tableCode, 8, liveCol, prevYear2, fb13, "89"));
        ro(t, 8, 5, "G13", yearlyAverage(lookup, tableCode, 8, liveCol, prevYear1, fb13, "91"));
        addHistoricalRollingRow(t, 8, 6, coordsForRow(histCols, 13), rolling, tableCode, liveCol, lookup, fb13);
        d(t,  8,13, "O13", "event", "발생 시");
        d(t,  8,14, "P13", "daily", "매일");

        // ── Row 9: 슬러지원단위 - 화장지 ──
        Map<YearMonth, String> fb14 = anchorFallbackMap("81", "58", "68", "50", "46", "53", "62");
        ro(t, 9, 2, "D14", "화장지", 1, 1);
        d(t,  9, 3, "E14", "daily", "매일");
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
        d(t,  6,11, "M25", "daily", "매일");
        d(t,  6,12, "N25", "daily", "매일");

        // ── Row 7: 장기재고 6개월 초과 ──
        ro(t, 7, 1, "C26", "6개월 초과", 1, 1);
        ro(t, 7, 2, "D26", "톤",          1, 1);
        ro(t, 7, 3, "E26", "0");
        addHistoricalRollingRow(t, 7, 4, coordsForRow(histCols, 26), rolling, tableCode, liveCol, lookup,
                anchorFallbackMap("917", "980", "786", "915", "957", "1543", "1130"));
        d(t,  7,11, "M26", "daily", "매일");
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
        d(t,  2, 2, "D36", "daily", "매일");
        d(t,  2, 3, "E36", null, null);
        d(t,  2, 4, "F36", "daily", "매일");
        d(t,  2, 5, "G36", null, null);

        // ── Row 3: 전력 - 화장지 ──
        ro(t, 3, 1, "C37", "화장지", 1, 1);
        d(t,  3, 2, "D37", "daily", "매일");
        d(t,  3, 3, "E37", null, null);
        d(t,  3, 4, "F37", "daily", "매일");
        d(t,  3, 5, "G37", null, null);

        // ── Row 4: 전력 - 화)초지5 ──
        ro(t, 4, 1, "C38", "화)초지5", 1, 1);
        d(t,  4, 2, "D38", "daily", "매일");
        d(t,  4, 3, "E38", null, null);
        d(t,  4, 4, "F38", "daily", "매일");
        d(t,  4, 5, "G38", null, null);

        // ── Row 5: 연료 - 제지 ──
        ro(t, 5, 0, "B39", "연료",    3, 1);
        ro(t, 5, 1, "C39", "제   지", 1, 1);
        d(t,  5, 2, "D39", "daily", "매일");
        d(t,  5, 3, "E39", null, null);
        d(t,  5, 4, "F39", "daily", "매일");
        d(t,  5, 5, "G39", null, null);

        // ── Row 6: 연료 - 화장지 ──
        ro(t, 6, 1, "C40", "화장지", 1, 1);
        d(t,  6, 2, "D40", "daily", "매일");
        d(t,  6, 3, "E40", null, null);
        d(t,  6, 4, "F40", "daily", "매일");
        d(t,  6, 5, "G40", null, null);

        // ── Row 7: 연료 - 화)초지5 ──
        ro(t, 7, 1, "C41", "화)초지5", 1, 1);
        d(t,  7, 2, "D41", "daily", "매일");
        d(t,  7, 3, "E41", null, null);
        d(t,  7, 4, "F41", "daily", "매일");
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
        d(t,  2, 1, "K36", "daily", "매일");
        d(t,  2, 2, "L36", "daily", "매일");
        ro(t, 2, 3, "M36", rollingValue(lookup, tableCode, 2, liveCol, mMinus2, fbLng));
        ro(t, 2, 4, "N36", rollingValue(lookup, tableCode, 2, liveCol, mMinus1, fbLng));
        d(t,  2, 5, "O36", "daily", "매일");
        d(t,  2, 6, "P36", "daily",   "매일");
        d(t,  2, 7, "Q36", "daily", "매일", 5, 1);

        // ── Row 3: 유동상소각로 ──
        Map<YearMonth, String> fbFluid = boilerAnchorFallback("15.3", "14.7");
        ro(t, 3, 0, "J37", "유동상소각로", 1, 1);
        d(t,  3, 1, "K37", "daily", "매일");
        d(t,  3, 2, "L37", "daily", "매일");
        ro(t, 3, 3, "M37", rollingValue(lookup, tableCode, 3, liveCol, mMinus2, fbFluid));
        ro(t, 3, 4, "N37", rollingValue(lookup, tableCode, 3, liveCol, mMinus1, fbFluid));
        d(t,  3, 5, "O37", "daily", "매일");
        d(t,  3, 6, "P37", "daily",   "매일");

        // ── Row 4: 복합보일러 ──
        Map<YearMonth, String> fbComplex = boilerAnchorFallback("56.8", "52.5");
        ro(t, 4, 0, "J38", "복합보일러", 1, 1);
        d(t,  4, 1, "K38", "daily", "매일");
        d(t,  4, 2, "L38", "daily", "매일");
        ro(t, 4, 3, "M38", rollingValue(lookup, tableCode, 4, liveCol, mMinus2, fbComplex));
        ro(t, 4, 4, "N38", rollingValue(lookup, tableCode, 4, liveCol, mMinus1, fbComplex));
        d(t,  4, 5, "O38", "daily", "매일");
        d(t,  4, 6, "P38", "daily",   "매일");

        // ── Row 5: 폐합성소각로 ──
        Map<YearMonth, String> fbWaste = boilerAnchorFallback("10.2", "11.6");
        ro(t, 5, 0, "J39", "폐합성소각로", 1, 1);
        d(t,  5, 1, "K39", "daily", "매일");
        d(t,  5, 2, "L39", "daily", "매일");
        ro(t, 5, 3, "M39", rollingValue(lookup, tableCode, 5, liveCol, mMinus2, fbWaste));
        ro(t, 5, 4, "N39", rollingValue(lookup, tableCode, 5, liveCol, mMinus1, fbWaste));
        d(t,  5, 5, "O39", "daily", "매일");
        d(t,  5, 6, "P39", "daily",   "매일");

        // ── Row 6: 합계 ──
        Map<YearMonth, String> fbTotal = boilerAnchorFallback("84.7", "79.2");
        ro(t, 6, 0, "J40", "합  계", 1, 1);
        d(t,  6, 1, "K40", "daily", "매일");
        d(t,  6, 2, "L40", "daily", "매일");
        ro(t, 6, 3, "M40", rollingValue(lookup, tableCode, 6, liveCol, mMinus2, fbTotal));
        ro(t, 6, 4, "N40", rollingValue(lookup, tableCode, 6, liveCol, mMinus1, fbTotal));
        d(t,  6, 5, "O40", "daily", "매일");
        d(t,  6, 6, "P40", "daily",   "매일");
    }

    // ═══════════════════════════════════════════════
    //  5/6. 안전사고 발생건수/손실금액  (공용 헬퍼 + 10행 × 18열 × 2)
    //  구조: 구분(col0-1 병합) | '22~'25년 월평균(col2-5, 정적 시드) |
    //        당월-2(col6-9: 기계/전기/생산/소계, READONLY 롤링) |
    //        당월-1(col10-13) | 당월(col14-17, DATA×4, 라이브 입력)
    //  ※ "당월" 4개 컬럼의 판단 기준은 월롤링과 동일 — reportDate 기준
    //    직전 2개월/당월을 매번 다시 계산한다 (요구사항 5).
    //  ※ 과거 2개월(당월-2/당월-1) 값은 PPT 실측값을 anchor 시드로 사용하고,
    //    커트오프 이후에는 실측 조회(lookup)를 우선한다 (요구사항 7).
    // ═══════════════════════════════════════════════

    /** 안전사고 표(5/6) 전용 anchor 기준월 — PPT 캡처 시점(2026년 8월)과 동일 */
    private static final YearMonth SAFETY_ANCHOR_MONTH = YearMonth.of(2026, 8);

    /** 안전사고 표의 "당월" 4개 서브컬럼(기계/전기/생산/소계)의 라이브 colIndex 고정값 */
    private static final int[] SAFETY_LIVE_COLS = {14, 15, 16, 17};

    /** 안전사고 표 행 1개 데이터 정의 (구분 라벨 + 연도평균 4개 + 당월-2/당월-1 폴백 4×2개) */
    private record SafetyIncidentRowDef(
            String label0, String label1, boolean mergeLabel,
            String[] yearAvg4, String[] m2Fallback4, String[] m1Fallback4) {
    }

    private static void addSafetyIncidentTable(DailyReportTable t, LocalDate reportDate,
                                                HistoricalValueLookup lookup,
                                                SafetyIncidentRowDef[] rows) {
        final String tableCode = t.getTableCode();
        YearMonth current = YearMonth.from(reportDate);
        YearMonth mMinus2 = current.minusMonths(2);
        YearMonth mMinus1 = current.minusMonths(1);
        // ★★ 2026-09 버그 수정: m2Fallback4/m1Fallback4 하드코딩 시드는
        // "조회 시점의 상대월(mMinus2/mMinus1)"이 아니라 "SAFETY_ANCHOR_MONTH(2026-08)
        // 기준 고정 캘린더월(2026-06/2026-07)"에 귀속된 값이다. 표1/2/7/8과 동일하게
        // anchor 기준 고정 연월에 매핑해야, 조회 날짜(reportDate)가 달마다 바뀌어도
        // 실제로 그 값이 속했던 달에만 정확히 표시되고 다른 달에는 실측조회/"-"로
        // 대체된다. (수정 전에는 매번 mMinus2/mMinus1 키로 매핑해버려서, 조회월이
        // anchor월(2026-08)이 아닌 순간 하드코딩 값이 엉뚱한 달로 밀려 표시되었다 —
        // 예: 9월 조회 시 mMinus1=8월인데 원래 7월 값이었던 시드가 8월 값으로 나타남.)
        YearMonth anchorM2 = SAFETY_ANCHOR_MONTH.minusMonths(2);
        YearMonth anchorM1 = SAFETY_ANCHOR_MONTH.minusMonths(1);

        // ── Row 0: 대 헤더 ──
        h(t, 0, 0, coord(1, 0), "구 분", 2, 2);
        h(t, 0, 2, coord(1, 2), "'22년\n월평균", 2, 1);
        h(t, 0, 3, coord(1, 3), "'23년\n월평균", 2, 1);
        h(t, 0, 4, coord(1, 4), "'24년\n월평균", 2, 1);
        h(t, 0, 5, coord(1, 5), "'25년\n월평균", 2, 1);
        h(t, 0, 6, coord(1, 6), monthGroupLabel(mMinus2), 1, 4);
        h(t, 0, 10, coord(1, 10), monthGroupLabel(mMinus1), 1, 4);
        h(t, 0, 14, coord(1, 14), monthGroupLabel(current), 1, 4);

        // ── Row 1: 소 헤더 (기계/전기/생산/소계 × 3그룹) ──
        String[] subHeaders = {"기계", "전기", "생산", "소계"};
        for (int g = 0; g < 3; g++) {
            int startCol = 6 + g * 4;
            for (int i = 0; i < 4; i++) {
                h(t, 1, startCol + i, coord(2, startCol + i), subHeaders[i], 1, 1);
            }
        }

        // ── Row 2~9: 사업부/설비별 데이터 ──
        for (int r = 0; r < rows.length; r++) {
            int row = 2 + r;
            int excelRow = 3 + r;
            SafetyIncidentRowDef def = rows[r];

            if (def.mergeLabel()) {
                ro(t, row, 0, coord(excelRow, 0), def.label0(), 1, 2);
            } else {
                ro(t, row, 0, coord(excelRow, 0), def.label0() == null ? "" : def.label0());
                ro(t, row, 1, coord(excelRow, 1), def.label1());
            }

            for (int i = 0; i < 4; i++) {
                ro(t, row, 2 + i, coord(excelRow, 2 + i), def.yearAvg4()[i]);
            }

            for (int i = 0; i < 4; i++) {
                Map<YearMonth, String> fb = new LinkedHashMap<>();
                fb.put(anchorM2, def.m2Fallback4()[i]);
                fb.put(anchorM1, def.m1Fallback4()[i]);
                int liveCol = SAFETY_LIVE_COLS[i];
                ro(t, row, 6 + i, coord(excelRow, 6 + i),
                        rollingValueSafety(lookup, tableCode, row, liveCol, mMinus2, fb));
                ro(t, row, 10 + i, coord(excelRow, 10 + i),
                        rollingValueSafety(lookup, tableCode, row, liveCol, mMinus1, fb));
            }

            for (int i = 0; i < 4; i++) {
                d(t, row, 14 + i, coord(excelRow, 14 + i), "event", "발생 시");
            }
        }
    }

    /**
     * 안전사고 표 전용 롤링 실측값 해석 — 표1/표2와 동일한 원리이나 anchor 기준월이
     * {@link #SAFETY_ANCHOR_MONTH}(2026-08)로 다르므로 별도 커트오프 없이
     * (안전사고 표는 신규 기능이라 과거 커트오프 개념이 필요 없음) 항상 lookup을
     * 우선 시도하고, 없으면 PPT 실측값 시드(fallbackMap)를 사용한다.
     */
    private static String rollingValueSafety(HistoricalValueLookup lookup, String tableCode,
                                              int rowIndex, int liveColIndex, YearMonth targetMonth,
                                              Map<YearMonth, String> fallbackMap) {
        if (lookup != null) {
            String realValue = lookup.find(tableCode, rowIndex, liveColIndex, targetMonth);
            if (realValue != null && !realValue.isBlank()) {
                return realValue;
            }
        }
        String fallback = fallbackMap.get(targetMonth);
        return fallback != null ? fallback : "-";
    }

    /** "'26년 08월" 형태의 월 그룹 헤더 라벨 생성 */
    private static String monthGroupLabel(YearMonth ym) {
        return String.format("'%02d년 %02d월", ym.getYear() % 100, ym.getMonthValue());
    }

    /** 안전사고/트렌드 표 전용 좌표 생성 — col은 A~Z 알파벳 순서로 직접 매핑(오프셋 없음) */
    private static String coord(int excelRow, int col) {
        return String.valueOf(EXCEL_COLS.charAt(col)) + excelRow;
    }

    // ═══════════════════════════════════════════════
    //  5. 안전사고 발생건수  (10행 × 18열)
    // ═══════════════════════════════════════════════
    private static void addSafetyIncidentCountCells(DailyReportTable t, LocalDate reportDate,
                                                      HistoricalValueLookup lookup) {
        SafetyIncidentRowDef[] rows = {
                new SafetyIncidentRowDef("제지", null, true,
                        new String[]{"11", "6", "6", "5"},
                        new String[]{"-", "1", "4", "5"}, new String[]{"2", "-", "5", "7"}),
                new SafetyIncidentRowDef("화장지초지", null, true,
                        new String[]{"1", "1", "0", "1"},
                        new String[]{"2", "-", "-", "2"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("화장지가공", null, true,
                        new String[]{"1", "1", "1", "1"},
                        new String[]{"1", "-", "-", "1"}, new String[]{"-", "1", "-", "1"}),
                new SafetyIncidentRowDef("생리대", null, true,
                        new String[]{"0", "0", "0", "0"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("기저귀", null, true,
                        new String[]{"0", "0", "0", "-"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("", "6호기", false,
                        new String[]{"0", "0", "0", "0"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("에너지", null, true,
                        new String[]{"1", "2", "1", "2"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("합 계", null, true,
                        new String[]{"13", "10", "9", "9"},
                        new String[]{"3", "1", "4", "8"}, new String[]{"2", "1", "5", "8"}),
        };
        addSafetyIncidentTable(t, reportDate, lookup, rows);
    }

    // ═══════════════════════════════════════════════
    //  6. 안전사고 손실금액  (10행 × 18열)
    // ═══════════════════════════════════════════════
    private static void addSafetyIncidentAmountCells(DailyReportTable t, LocalDate reportDate,
                                                       HistoricalValueLookup lookup) {
        SafetyIncidentRowDef[] rows = {
                new SafetyIncidentRowDef("제지", null, true,
                        new String[]{"73.1", "113.9", "69.4", "62.7"},
                        new String[]{"-", "7.0", "30.5", "37.5"}, new String[]{"22.9", "-", "42.2", "65.1"}),
                new SafetyIncidentRowDef("화장지초지", null, true,
                        new String[]{"4.7", "14.7", "2.0", "14.5"},
                        new String[]{"13.6", "-", "-", "13.6"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("화장지가공", null, true,
                        new String[]{"3.3", "1.1", "1.2", "1.8"},
                        new String[]{"1.7", "-", "-", "1.7"}, new String[]{"-", "4.3", "-", "4.3"}),
                new SafetyIncidentRowDef("생리대", null, true,
                        new String[]{"35.1", "1.7", "2.1", "1.2"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("기저귀", null, true,
                        new String[]{"0.2", "0", "0", "-"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("", "6호기", false,
                        new String[]{"0", "0", "0.5", "2.5"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("에너지", null, true,
                        new String[]{"21.2", "55.7", "70.2", "77.4"},
                        new String[]{"-", "-", "-", "-"}, new String[]{"-", "-", "-", "-"}),
                new SafetyIncidentRowDef("합 계", null, true,
                        new String[]{"137.6", "187.0", "145.5", "159.9"},
                        new String[]{"15.3", "7.0", "30.5", "52.8"}, new String[]{"22.9", "4.3", "42.2", "69.4"}),
        };
        addSafetyIncidentTable(t, reportDate, lookup, rows);
    }

    // ═══════════════════════════════════════════════
    //  7/8 전용 롤링 헬퍼 (2026-09 추가) — 표1/표2와 동일한 "실측값 우선,
    //  없으면 anchor 하드코딩 시드로 폴백" 패턴을 연도/월 단위로 재사용한다.
    //  표5/6과 마찬가지로 신규 기능이라 FEATURE_CUTOFF_DATE 커트오프는 두지
    //  않고, 항상 실측 조회를 먼저 시도한다.
    // ═══════════════════════════════════════════════

    /**
     * ★ 표7 하드코딩 시드가 처음 작성되었을 때 가정한 "기준연도(anchor year)" — 2026년.
     * 표7의 과거 8개 컬럼 하드코딩 리터럴 값은 이 기준연도의 직전 8개년
     * (2018~2025)에 대응한다. 기준연도(2026) 자체는 항상 라이브(DATA) 컬럼이므로
     * 폴백 맵에 포함하지 않는다 — 라이브였던 시점에 사용자가 실제로 입력한 값이
     * 이미 DB에 쌓여 있을 것이므로, 윈도우가 굴러가 2026년이 과거 컬럼이 될
     * 즈음엔 실측 조회(lookup)만으로 충분하다.
     */
    private static final int ANCHOR_YEAR = 2026;

    /**
     * anchor 기준 과거 8개년(oldest→newest 순)에 하드코딩 값 8개를 매핑해
     * "연도 → 폴백값" 맵을 만든다. (표1/표2의 anchorFallbackMap과 동일한 원리)
     */
    private static Map<Integer, String> yearlyAnchorFallbackMap(String... valuesOldestToNewest8) {
        List<Integer> anchorRolling = rollingYears(ANCHOR_YEAR, 9);
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int i = 0; i < 8 && i < valuesOldestToNewest8.length; i++) {
            map.put(anchorRolling.get(i), valuesOldestToNewest8[i]);
        }
        return map;
    }

    /**
     * 표7 전용 롤링 실측값 해석 — {@link #rollingValueSafety}와 동일한 원리를
     * "연도" 단위로 적용한다. 커트오프 없이 항상 실측 조회를 우선 시도하고,
     * 없으면 anchor 하드코딩 시드(fallbackMap)를 사용한다.
     */
    private static String rollingValueYearly(HistoricalYearlyValueLookup yearlyLookup, String tableCode,
                                              int rowIndex, int liveColIndex, int targetYear,
                                              Map<Integer, String> fallbackMap) {
        if (yearlyLookup != null) {
            String realValue = yearlyLookup.find(tableCode, rowIndex, liveColIndex, targetYear);
            if (realValue != null && !realValue.isBlank()) {
                return realValue;
            }
        }
        String fallback = fallbackMap.get(targetYear);
        return fallback != null ? fallback : "-";
    }

    /**
     * ★ 표8 하드코딩 시드가 처음 작성되었을 때 가정한 "기준월" — 표5/6과 동일한
     * {@link #SAFETY_ANCHOR_MONTH}(2026년 8월)를 재사용한다. 표8의 과거 15개
     * 컬럼 하드코딩 리터럴 값은 이 기준월의 직전 15개월(2025-05~2026-07)에
     * 대응한다. 기준월(2026-08) 자체는 항상 라이브(DATA) 컬럼이라 폴백 맵에
     * 포함하지 않는다 — 표7의 ANCHOR_YEAR와 동일한 이유.
     */
    private static Map<YearMonth, String> monthlyTrendAnchorFallbackMap(String... valuesOldestToNewest15) {
        List<YearMonth> anchorRolling = rollingMonths(SAFETY_ANCHOR_MONTH, 16);
        Map<YearMonth, String> map = new LinkedHashMap<>();
        for (int i = 0; i < 15 && i < valuesOldestToNewest15.length; i++) {
            map.put(anchorRolling.get(i), valuesOldestToNewest15[i]);
        }
        return map;
    }

    // ═══════════════════════════════════════════════
    //  7. 안전사고 연도별 추이  (14행 × 12열, 9년 롤링 윈도우)
    //  구조: 구분(col0-2, 계층형 병합) | 롤링 9개 연도(col3-11)
    //  마지막 컬럼(col11=당해년도)만 DATA(라이브 입력), 그 외는 실측 우선/
    //  anchor 시드 폴백(요구사항: 표1/표2와 동일한 "실제 입력값 우선" 방식).
    // ═══════════════════════════════════════════════
    private static void addSafetyYearlyTrendCells(DailyReportTable t, LocalDate reportDate,
                                                    HistoricalYearlyValueLookup yearlyLookup) {
        final String tableCode = t.getTableCode();
        final int liveCol = 11; // 표7의 라이브(실측 입력) 컬럼은 항상 col11 (윈도우 크기 9 고정)
        int currentYear = YearMonth.from(reportDate).getYear();
        List<Integer> rolling = rollingYears(currentYear, 9);

        // ── Row 0: 헤더 (롤링 9개 연도) ──
        h(t, 0, 0, coord(1, 0), "구분", 1, 3);
        for (int i = 0; i < rolling.size(); i++) {
            h(t, 0, 3 + i, coord(1, 3 + i), rolling.get(i) + "년", 1, 1);
        }

        // ── 데이터 행 (row1~13, excelRow 2~14) ──
        // 각 행의 값은 anchor(2026년) 기준 과거 8개년(2018~2025) 하드코딩 시드이며,
        // 실측 데이터가 있으면 항상 실측값이 우선한다 (rollingValueYearly).
        Map<Integer, String> fb1 = yearlyAnchorFallbackMap("5", "5", "6", "6", "7", "6", "7", "7");   // 공장 재해자수
        Map<Integer, String> fb2 = yearlyAnchorFallbackMap("0", "0", "0", "0", "0", "-", "-", "-");  // 공장 사망자수
        Map<Integer, String> fb3 = yearlyAnchorFallbackMap("5", "5", "6", "6", "7", "-", "7", "7");   // 공장 발생건수
        Map<Integer, String> fb4 = yearlyAnchorFallbackMap("1", "2", "1", "0", "4", "1", "1", "3");   // 협력사 재해자수
        Map<Integer, String> fb5 = yearlyAnchorFallbackMap("0", "0", "0", "0", "0", "-", "-", "-");  // 협력사 사망자수
        Map<Integer, String> fb6 = yearlyAnchorFallbackMap("1", "2", "1", "0", "4", "1", "1", "3");   // 협력사 발생건수
        Map<Integer, String> fb7 = yearlyAnchorFallbackMap("6", "7", "7", "6", "11", "7", "8", "10"); // 청주공장 총 발생건수
        Map<Integer, String> fb8 = yearlyAnchorFallbackMap("8", "2", "2", "5", "3", "-", "-", "-");  // 자회사 재해자수
        Map<Integer, String> fb9 = yearlyAnchorFallbackMap("0", "0", "0", "0", "0", "-", "-", "-");  // 자회사 사망자수
        Map<Integer, String> fb10 = yearlyAnchorFallbackMap("8", "2", "2", "5", "3", "-", "-", "-"); // 자회사 총 발생건수
        Map<Integer, String> fb11 = yearlyAnchorFallbackMap("14", "9", "9", "11", "14", "7", "8", "10"); // 합계 재해자수
        Map<Integer, String> fb12 = yearlyAnchorFallbackMap("0", "0", "0", "0", "0", "-", "-", "-"); // 합계 사망자수
        Map<Integer, String> fb13 = yearlyAnchorFallbackMap("14", "9", "9", "11", "14", "7", "8", "10"); // 합계 총 발생건수

        // 청주공장(rowSpan7) — 공장(rowSpan3)
        ro(t, 1, 0, coord(2, 0), "청주\n공장", 7, 1);
        ro(t, 1, 1, coord(2, 1), "공장", 3, 1);
        addYearlyTrendRow(t, 1, 2, "재해자수", rolling, fb1, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 2, 2, "사망자수", rolling, fb2, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 3, 2, "발생건수", rolling, fb3, tableCode, liveCol, yearlyLookup);
        // 협력사(rowSpan3)
        ro(t, 4, 1, coord(5, 1), "협력사", 3, 1);
        addYearlyTrendRow(t, 4, 2, "재해자수", rolling, fb4, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 5, 2, "사망자수", rolling, fb5, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 6, 2, "발생건수", rolling, fb6, tableCode, liveCol, yearlyLookup);
        // 청주공장 총 발생건수 (col1은 병합 없는 단독 빈 셀)
        ro(t, 7, 1, coord(8, 1), "");
        addYearlyTrendRow(t, 7, 2, "총 발생건수", rolling, fb7, tableCode, liveCol, yearlyLookup);
        // 자회사(rowSpan3, colSpan2 — col0-1 병합)
        ro(t, 8, 0, coord(9, 0), "자회사", 3, 2);
        addYearlyTrendRow(t, 8, 2, "재해자수", rolling, fb8, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 9, 2, "사망자수", rolling, fb9, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 10, 2, "총 발생건수", rolling, fb10, tableCode, liveCol, yearlyLookup);
        // 합계(rowSpan3, colSpan2)
        ro(t, 11, 0, coord(12, 0), "합계", 3, 2);
        addYearlyTrendRow(t, 11, 2, "재해자수", rolling, fb11, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 12, 2, "사망자수", rolling, fb12, tableCode, liveCol, yearlyLookup);
        addYearlyTrendRow(t, 13, 2, "총 발생건수", rolling, fb13, tableCode, liveCol, yearlyLookup);
    }

    /**
     * 표7(연도별) 한 행의 지표명 + 9개 연도값(마지막 컬럼=라이브 당해년도=DATA) 채우기.
     * 과거 8개 컬럼은 실측 우선/anchor 시드 폴백({@link #rollingValueYearly})으로 결정된다.
     */
    private static void addYearlyTrendRow(DailyReportTable t, int row, int col2, String label,
                                            List<Integer> rolling, Map<Integer, String> fallbackMap,
                                            String tableCode, int liveCol,
                                            HistoricalYearlyValueLookup yearlyLookup) {
        int excelRow = row + 1;
        ro(t, row, col2, coord(excelRow, col2), label);
        for (int i = 0; i < rolling.size() - 1; i++) {
            int yr = rolling.get(i);
            String value = rollingValueYearly(yearlyLookup, tableCode, row, liveCol, yr, fallbackMap);
            ro(t, row, 3 + i, coord(excelRow, 3 + i), value);
        }
        d(t, row, liveCol, coord(excelRow, liveCol), "event", "발생 시");
    }

    // ═══════════════════════════════════════════════
    //  8. 안전사고 월별 추이  (14행 × 19열, 16개월 롤링 윈도우)
    //  구조: 구분(col0-2, 계층형 병합) | 롤링 16개월(col3-18), 연도경계에서
    //  대헤더('25년/'26년 등)가 동적으로 colspan 분할됨 (표1/표2의 yearGroups와 동일 원리).
    //  마지막 컬럼(col18=당월)만 DATA(라이브 입력), 그 외는 실측 우선/anchor 시드 폴백.
    // ═══════════════════════════════════════════════
    private static void addSafetyMonthlyTrendCells(DailyReportTable t, LocalDate reportDate,
                                                      HistoricalValueLookup lookup) {
        final String tableCode = t.getTableCode();
        final int liveCol = 18; // 표8의 라이브(실측 입력) 컬럼은 항상 col18 (윈도우 크기 16 고정)
        YearMonth current = YearMonth.from(reportDate);
        List<YearMonth> rolling = rollingMonths(current, 16);

        // 연도별 그룹핑 (Row 0 대 헤더의 colspan 계산용) — colIdx 3~18(16개월) 대상
        Map<Integer, int[]> yearGroups = new LinkedHashMap<>(); // year → [startCol, count]
        for (int i = 0; i < rolling.size(); i++) {
            int yr = rolling.get(i).getYear();
            final int col = 3 + i;
            yearGroups.computeIfAbsent(yr, k -> new int[]{col, 0});
            yearGroups.get(yr)[1]++;
        }

        // ── Row 0: 대 헤더 ──
        h(t, 0, 0, coord(1, 0), "구분", 2, 3);
        for (Map.Entry<Integer, int[]> entry : yearGroups.entrySet()) {
            int yr = entry.getKey();
            int startCol = entry.getValue()[0];
            int count = entry.getValue()[1];
            h(t, 0, startCol, coord(1, startCol), "'" + String.valueOf(yr).substring(2) + "년", 1, count);
        }

        // ── Row 1: 월 소 헤더 (롤링 16개월) ──
        for (int i = 0; i < rolling.size(); i++) {
            int colIdx = 3 + i;
            h(t, 1, colIdx, coord(2, colIdx), rolling.get(i).getMonthValue() + "월", 1, 1);
        }

        // ── 데이터 행 (row2~13, excelRow 3~14) ──
        // 각 행의 값은 anchor(2026년 8월) 기준 과거 15개월(2025-05~2026-07) 하드코딩 시드이며,
        // 실측 데이터가 있으면 항상 실측값이 우선한다 (rollingValueSafety 재사용).
        Map<YearMonth, String> fb2  = monthlyTrendAnchorFallbackMap(
                "-", "-", "1", "-", "2", "-", "-", "1", "1", "1", "1", "2", "1", "-", "-"); // 공장 재해자수
        Map<YearMonth, String> fb3  = monthlyTrendAnchorFallbackMap(zeros16Dash());                       // 공장 사망자수
        Map<YearMonth, String> fb4  = fb2;                                                                 // 공장 총 발생건수 (동일값)
        Map<YearMonth, String> fb5  = monthlyTrendAnchorFallbackMap(
                "-", "-", "-", "-", "-", "-", "1", "-", "-", "-", "-", "-", "-", "1", "-"); // 협력사 재해자수
        Map<YearMonth, String> fb6  = monthlyTrendAnchorFallbackMap(zeros16Dash());                       // 협력사 사망자수
        Map<YearMonth, String> fb7  = fb5;                                                                 // 협력사 총 발생건수
        Map<YearMonth, String> fb8  = monthlyTrendAnchorFallbackMap(zeros16Dash());                       // 자회사 재해자수
        Map<YearMonth, String> fb9  = monthlyTrendAnchorFallbackMap(zeros16Dash());                       // 자회사 사망자수
        Map<YearMonth, String> fb10 = monthlyTrendAnchorFallbackMap(zeros16Dash());                       // 자회사 총 발생건수
        Map<YearMonth, String> fb11 = monthlyTrendAnchorFallbackMap(
                "-", "-", "1", "-", "2", "-", "1", "1", "1", "1", "1", "2", "1", "1", "-"); // 합계 재해자수
        Map<YearMonth, String> fb12 = monthlyTrendAnchorFallbackMap(zeros16Dash());                       // 합계 사망자수
        Map<YearMonth, String> fb13 = fb11;                                                                // 합계 총 발생건수

        // 청주공장(rowSpan6) — 공장(rowSpan3)
        ro(t, 2, 0, coord(3, 0), "청주\n공장", 6, 1);
        ro(t, 2, 1, coord(3, 1), "공장", 3, 1);
        addMonthlyTrendRow(t, 2, 2, "재해자수", rolling, fb2, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 3, 2, "사망자수", rolling, fb3, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 4, 2, "총 발생건수", rolling, fb4, tableCode, liveCol, lookup);
        // 협력사(rowSpan3)
        ro(t, 5, 1, coord(6, 1), "협력사", 3, 1);
        addMonthlyTrendRow(t, 5, 2, "재해자수", rolling, fb5, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 6, 2, "사망자수", rolling, fb6, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 7, 2, "총 발생건수", rolling, fb7, tableCode, liveCol, lookup);
        // 자회사(rowSpan3, colSpan2)
        ro(t, 8, 0, coord(9, 0), "자회사", 3, 2);
        addMonthlyTrendRow(t, 8, 2, "재해자수", rolling, fb8, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 9, 2, "사망자수", rolling, fb9, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 10, 2, "총 발생건수", rolling, fb10, tableCode, liveCol, lookup);
        // 합계(rowSpan3, colSpan2)
        ro(t, 11, 0, coord(12, 0), "합계", 3, 2);
        addMonthlyTrendRow(t, 11, 2, "재해자수", rolling, fb11, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 12, 2, "사망자수", rolling, fb12, tableCode, liveCol, lookup);
        addMonthlyTrendRow(t, 13, 2, "총 발생건수", rolling, fb13, tableCode, liveCol, lookup);
    }

    /** "-"로 채운 16개월치 배열 (전부 미발생) — anchor 폴백맵 시드로도 재사용 */
    private static String[] zeros16Dash() {
        String[] arr = new String[16];
        java.util.Arrays.fill(arr, "-");
        return arr;
    }

    /**
     * 표8(월별) 한 행의 지표명 + 16개월값(마지막 컬럼=라이브 당월=DATA) 채우기.
     * 과거 15개 컬럼은 실측 우선/anchor 시드 폴백({@link #rollingValueSafety})으로 결정된다.
     */
    private static void addMonthlyTrendRow(DailyReportTable t, int row, int col2, String label,
                                              List<YearMonth> rolling, Map<YearMonth, String> fallbackMap,
                                              String tableCode, int liveCol,
                                              HistoricalValueLookup lookup) {
        int excelRow = row + 1;
        ro(t, row, col2, coord(excelRow, col2), label);
        for (int i = 0; i < rolling.size() - 1; i++) {
            YearMonth ym = rolling.get(i);
            String value = rollingValueSafety(lookup, tableCode, row, liveCol, ym, fallbackMap);
            ro(t, row, 3 + i, coord(excelRow, 3 + i), value);
        }
        d(t, row, liveCol, coord(excelRow, liveCol), "event", "발생 시");
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
