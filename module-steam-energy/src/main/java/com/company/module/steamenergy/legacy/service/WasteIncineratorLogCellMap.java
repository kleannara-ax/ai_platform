package com.company.module.steamenergy.legacy.service;

import java.util.List;

/**
 * 폐합성소각로 운전일지 엑셀 ↔ 저장 위치 매핑표.
 *
 * <p>유동상({@link FluidizedDailyLogCellMap})과 같은 방식으로, 엑셀 셀 위치를 그대로 적어
 * 원본 양식과 눈으로 대조할 수 있게 했다. 다만 이 통합문서는 <b>시트 = 항목 분류</b> 이고
 * 하루가 열(또는 행) 하나이므로, 시트마다 "일자 시작 위치 + 항목 줄" 로 정의한다.
 *
 * <p>저장 위치는 모두 {@code waste_incin_log} (row_key = 일, col_index = 항목 고정번호).
 * col_index 규약은 sql/module-steam-energy/10_cell_conventions.sql 과 같다.
 *
 * <p>계산 칸(합계·재고 2일 이후·적산량·누계·투입비)은 화면에서 계산하므로 매핑하지 않는다.
 * 재고 행은 1일(기초재고)만 읽고, 2일부터는 전일재고+반입-사용 으로 화면이 계산한다.
 */
public final class WasteIncineratorLogCellMap {

    public enum Layout {
        /** 일자가 열로 늘어나는 시트 (소각량·스팀량·급수량·전력·폐기물·약품·TMS·폐기물발생) */
        DAYS_IN_COLUMNS,
        /** 일자가 행으로 늘어나는 시트 (스팀량(적산)·외부반입) */
        DAYS_IN_ROWS
    }

    /**
     * @param line      DAYS_IN_COLUMNS = 엑셀 행 번호(1-based) / DAYS_IN_ROWS = 엑셀 열 문자
     * @param colIndex  waste_incin_log col_index
     * @param text      숫자가 아니라 글자 그대로 저장할 항목 (저/고 등)
     * @param firstDay  1일만 읽는 항목 (기초재고)
     * @param label     주석용 항목명
     */
    public record Entry(String line, int colIndex, boolean text, boolean firstDay, String label) {}

    /** 시트 배치 확인용 앵커 — 값이 다르면 양식이 어긋난 시트다. */
    public record Anchor(String ref, String expected) {}

    /**
     * @param sheetName   엑셀 시트 이름
     * @param layout      일자 방향
     * @param firstDayCol DAYS_IN_COLUMNS: 1일이 들어가는 열(1-based). DAYS_IN_ROWS 에선 미사용
     * @param firstDayRow DAYS_IN_ROWS: 1일이 들어가는 행(1-based). DAYS_IN_COLUMNS 에선 미사용
     * @param titleRef    "(07월)" 처럼 월이 적힌 제목 셀
     */
    public record SheetMap(
            String sheetName, Layout layout, int firstDayCol, int firstDayRow,
            String titleRef, List<Anchor> anchors, List<Entry> entries
    ) {}

    private static Entry num(String line, int colIndex, String label) {
        return new Entry(line, colIndex, false, false, label);
    }

    private static Entry txt(String line, int colIndex, String label) {
        return new Entry(line, colIndex, true, false, label);
    }

    /** 기초재고 — 1일 칸만 읽는다. */
    private static Entry base(String line, int colIndex, String label) {
        return new Entry(line, colIndex, false, true, label);
    }

    private static Anchor anchor(String ref, String expected) {
        return new Anchor(ref, expected);
    }

    public static final List<SheetMap> SHEETS = List.of(
            // ── 소각량 (일자 C열부터) ──────────────────────────────
            new SheetMap("소각량", Layout.DAYS_IN_COLUMNS, 3, 0, "A1",
                    List.of(anchor("A3", "구분"), anchor("A4", "폐1"), anchor("A10", "폐2"), anchor("A16", "합계")),
                    List.of(
                            num("4", 1, "폐1 자폐"),
                            num("5", 2, "폐1 재활용"),
                            // 6행 합계 = 계산
                            num("7", 3, "폐1 발생량"),
                            num("8", 4, "폐1 가동시간"),
                            num("9", 5, "폐1 외폐재고"),
                            num("10", 11, "폐2 자폐"),
                            num("11", 12, "폐2 재활용"),
                            // 12행 합계 = 계산
                            num("13", 13, "폐2 발생량"),
                            num("14", 14, "폐2 가동시간"),
                            num("15", 15, "폐2 자폐재고"),
                            // 16행 합계 자폐 / 18행 합계 재활용 = 계산
                            num("17", 21, "합계 외폐")
                    )),

            // ── 스팀량 (일자 B열부터) ──────────────────────────────
            new SheetMap("스팀량", Layout.DAYS_IN_COLUMNS, 2, 0, "A1",
                    List.of(anchor("A3", "구분"), anchor("A4", "폐1"), anchor("A9", "기관"), anchor("A17", "1호기")),
                    List.of(
                            // 2행 시간당 / 7·8행 합계 / 17~20행 호기별 = 계산
                            num("4", 100, "폐1 생산량"),
                            num("5", 101, "폐2-1 생산량"),
                            num("6", 102, "폐2-2 생산량"),
                            num("9", 103, "기관"),
                            txt("10", 104, "저/고")
                    )),

            // ── 스팀량(적산) — 일자가 행(5행 = 1일), 적산량 열은 계산 ──
            new SheetMap("스팀량(적산)", Layout.DAYS_IN_ROWS, 0, 5, "A1",
                    List.of(anchor("A3", "날짜"), anchor("B4", "적산량"), anchor("C4", "생산량")),
                    List.of(
                            num("C", 100, "폐#1 생산량"),
                            num("E", 101, "폐#2 1차 생산량"),
                            num("G", 102, "폐#2 2차 생산량")
                    )),

            // ── 급수량 (일자 B열부터) ──────────────────────────────
            new SheetMap("급수량", Layout.DAYS_IN_COLUMNS, 2, 0, "A1",
                    List.of(anchor("A3", "구분"), anchor("A4", "폐1-1"), anchor("A7", "폐2-1")),
                    List.of(
                            num("4", 200, "폐1-1"),
                            num("5", 201, "폐1-2"),
                            // 6행 폐#1-T = 계산
                            num("7", 202, "폐2-1"),
                            num("8", 203, "폐2-2")
                            // 9행 폐#2-T = 계산
                    )),

            // ── 전력 (일자 B열부터) ────────────────────────────────
            new SheetMap("전력", Layout.DAYS_IN_COLUMNS, 2, 0, "A1",
                    List.of(anchor("A3", "구분"), anchor("A5", "폐#1"), anchor("A7", "EP")),
                    List.of(
                            num("4", 300, "지침"),
                            num("5", 301, "폐#1"),
                            num("6", 302, "폐#2"),
                            num("7", 303, "EP")
                            // 8행 Total = 계산
                    )),

            // ── 폐기물 (일자 C열부터) ──────────────────────────────
            new SheetMap("폐기물", Layout.DAYS_IN_COLUMNS, 3, 0, "B1",
                    List.of(anchor("A3", "구분"), anchor("A4", "비산재"), anchor("A7", "바닥재")),
                    List.of(
                            num("4", 400, "비산재 폐#1"),
                            num("5", 401, "비산재 폐#2"),
                            // 6행 Total = 계산
                            num("7", 402, "바닥재 폐#1"),
                            num("8", 403, "바닥재 폐#2")
                            // 9행 Total = 계산
                    )),

            // ── 약품 (일자 C열부터, 재고는 1일만) ──────────────────
            new SheetMap("약품", Layout.DAYS_IN_COLUMNS, 3, 0, "B1",
                    List.of(anchor("A3", "구분"), anchor("A4", "요소수"), anchor("A20", "부생유"), anchor("A26", "크링커")),
                    List.of(
                            num("4", 500, "요소수 반입"), num("5", 501, "요소수 사용"), base("6", 502, "요소수 기초재고"),
                            num("7", 503, "청관재 반입"), num("8", 504, "청관재 사용"), base("9", 505, "청관재 기초재고"),
                            num("10", 506, "소금 반입"), num("11", 507, "소금 사용"), num("12", 508, "소금 유동상"),
                            base("13", 509, "소금 기초재고"),
                            num("14", 510, "가성 반입"), num("15", 511, "가성 사용"), base("16", 512, "가성 기초재고"),
                            num("17", 513, "활성탄 반입"), num("18", 514, "활성탄 사용"), base("19", 515, "활성탄 기초재고"),
                            num("20", 516, "부생유 반입"),
                            num("21", 517, "부생유 폐1"), num("22", 518, "부생유 폐2"),
                            num("23", 519, "부생유 슬1"), num("24", 520, "부생유 슬2"),
                            base("25", 521, "부생유 기초재고량"),
                            num("26", 522, "크링커 측벽"), num("27", 523, "크링커 낙차"),
                            num("28", 524, "크링커 반입"), num("29", 525, "크링커 사용"),
                            base("30", 526, "크링커 기초재고")
                            // 31행 투입비 = 계산
                    )),

            // ── TMS (일자 C열부터) ─────────────────────────────────
            new SheetMap("TMS", Layout.DAYS_IN_COLUMNS, 3, 0, "B1",
                    List.of(anchor("A3", "구분"), anchor("A4", "폐#1"), anchor("A10", "폐#2")),
                    List.of(
                            num("4", 600, "폐#1 분진"), num("5", 601, "폐#1 CO"), num("6", 602, "폐#1 Nox"),
                            num("7", 603, "폐#1 Sox"), num("8", 604, "폐#1 HCl"), num("9", 605, "폐#1 온도"),
                            num("10", 606, "폐#2 분진"), num("11", 607, "폐#2 CO"), num("12", 608, "폐#2 Nox"),
                            num("13", 609, "폐#2 Sox"), num("14", 610, "폐#2 HCl"), num("15", 611, "폐#2 온도")
                    )),

            // ── 외부반입 — 일자가 행(3행 = 1일), P열 누계는 계산 ────
            new SheetMap("외부반입", Layout.DAYS_IN_ROWS, 0, 3, "A1",
                    List.of(anchor("A2", "업체명"), anchor("B2", "보노아")),
                    List.of(
                            num("B", 700, "보노아"), num("C", 701, "스라이브"), num("D", 702, "나투라"),
                            num("E", 703, "대한제지"), num("F", 704, "신성"), num("G", 705, "그린이엔텍"),
                            num("H", 706, "금보산업"), num("I", 707, "청원es"), num("J", 708, "티와이"),
                            num("K", 709, "재운산업"), num("L", 710, "현진RC"), num("M", 711, "중앙이엔비"),
                            num("N", 712, "주원"), num("O", 713, "태창")
                    )),

            // ── 폐기물발생 (일자 B열부터) ──────────────────────────
            new SheetMap("폐기물발생", Layout.DAYS_IN_COLUMNS, 2, 0, "A1",
                    List.of(anchor("A3", "구분"), anchor("A4", "폐목재"), anchor("A9", "재고량")),
                    List.of(
                            num("4", 800, "폐목재"), num("5", 801, "생불"), num("6", 802, "폐합성"),
                            num("7", 803, "리젝트"), num("8", 804, "라가"), num("9", 805, "재고량")
                    ))
    );

    // ── 셀 주소 파서 (유동상 매핑표와 동일 규칙) ───────────────────
    public static int parseCol(String ref) {
        int col = 0;
        for (char ch : ref.toCharArray()) {
            if (!Character.isLetter(ch)) break;
            col = col * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return col;
    }

    public static int parseRow(String ref) {
        StringBuilder digits = new StringBuilder();
        for (char ch : ref.toCharArray()) {
            if (Character.isDigit(ch)) digits.append(ch);
        }
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
    }

    private WasteIncineratorLogCellMap() {
    }
}
