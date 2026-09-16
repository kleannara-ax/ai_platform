package com.company.module.steamenergy.legacy.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 유동상 운전일지 엑셀 ↔ 저장 위치 매핑표.
 *
 * <p>1~31일 시트는 배치가 같으므로 아래 표 하나로 모든 날짜를 처리한다.
 * 엑셀 셀 주소를 그대로 적어 원본 양식과 눈으로 대조할 수 있게 했다.
 *
 * <p>저장 위치(Store)
 * <ul>
 *   <li>{@link Store#DETAIL} : 세부 운영내역 {@code fluidized_detail_main}. target = 세부 시트의 행 번호(열은 일자+2)</li>
 *   <li>{@link Store#FLOW}   : {@code flow_m_fluid_incinerator} (세부 7행 스팀 송기량). target 미사용</li>
 *   <li>{@link Store#LOG}    : 운전일지 전용 {@code fluidized_daily_log}. target = col_index</li>
 * </ul>
 */
public final class FluidizedDailyLogCellMap {

    public enum Store { DETAIL, FLOW, LOG }

    /**
     * @param ref    엑셀 셀 주소 (예: "D7")
     * @param store  저장 위치
     * @param target DETAIL=세부 행 번호 / LOG=col_index / FLOW=미사용
     * @param scale  단위 환산 (KG → 톤 = 0.001, 그 외 1)
     * @param text   숫자가 아니라 글자 그대로 저장할 항목 (근무자·특이사항 등)
     * @param label  주석용 항목명
     */
    public record Entry(String ref, Store store, int target, double scale, boolean text, String label) {
        public int row() { return parseRow(ref) - 1; }   // POI 0-based
        public int col() { return parseCol(ref) - 1; }
    }

    private static final double TON = 0.001d;

    private static Entry num(String ref, Store store, int target, String label) {
        return new Entry(ref, store, target, 1, false, label);
    }

    private static Entry ton(String ref, Store store, int target, String label) {
        return new Entry(ref, store, target, TON, false, label);
    }

    private static Entry str(String ref, int colIndex, String label) {
        return new Entry(ref, Store.LOG, colIndex, 1, true, label);
    }

    /** 하루치 시트 전체 매핑 (엑셀 셀 → 저장 위치) */
    public static final List<Entry> DAY_CELLS = List.of(
            // ── 근무자 (1~3행) ─────────────────────────────────────
            str("B2", 1, "근무자 오전"),
            str("D2", 2, "근무자 오후"),
            str("F2", 3, "근무자 야간"),

            // ── 1. 소각현황 (KG) → 세부는 톤 ───────────────────────
            ton("D7", Store.DETAIL, 9, "SRF 소각량"),
            ton("G7", Store.DETAIL, 8, "SLUDGE 소각량"),

            // ── 2. BOILER 운전현황 ────────────────────────────────
            num("R7", Store.DETAIL, 6, "스팀 생산량"),
            num("U7", Store.FLOW, 0, "스팀 송기량"),
            num("X7", Store.DETAIL, 4, "가동시간"),

            // ── 3. 약품 사용현황 (전일재고 C / 입고 E / 사용 G) ────
            str("C12", 20, "탄산암모늄 전일재고"), num("E12", Store.DETAIL, 18, "탄산암모늄 입고"), num("G12", Store.DETAIL, 19, "탄산암모늄 사용"),
            str("C13", 23, "청관제 전일재고"),   num("E13", Store.DETAIL, 21, "청관제 입고"),   num("G13", Store.DETAIL, 22, "청관제 사용"),
            str("C14", 26, "활성탄 전일재고"),   num("E14", Store.DETAIL, 24, "활성탄 입고"),   num("G14", Store.DETAIL, 25, "활성탄 사용"),
            str("C15", 29, "가성소다 전일재고"), num("E15", Store.DETAIL, 27, "가성소다 입고"), num("G15", Store.DETAIL, 28, "가성소다 사용"),
            str("C16", 32, "소석회 전일재고"),   num("E16", Store.DETAIL, 30, "소석회 입고"),   num("G16", Store.DETAIL, 31, "소석회 사용"),
            str("C17", 35, "소금 전일재고"),     num("E17", Store.DETAIL, 33, "소금 입고"),     num("G17", Store.DETAIL, 34, "소금 사용"),
            str("C18", 38, "경유 전일재고"),     num("E18", Store.DETAIL, 36, "경유 입고"),     num("G18", Store.DETAIL, 37, "경유 사용"),
            str("C19", 41, "SRF 전일재고"),      ton("E19", Store.DETAIL, 46, "SRF 입고"),
            // SRF 사용(G19)은 세부에서 소각량(9행)으로 산출되므로 기록하지 않는다.
            str("C20", 44, "규사 전일재고"),     num("E20", Store.DETAIL, 39, "규사 입고"),     num("G20", Store.DETAIL, 40, "규사 사용"),

            // ── 4. 소각재 발생량 ──────────────────────────────────
            str("S12", 50, "비산재 반출량"),
            str("S14", 51, "바닥재 반출량"),
            str("S18", 52, "불연물 주간"),
            str("U18", 53, "불연물 야간"),

            // ── 5. 전력 사용량 (전일지침 D / 금일지침 G) ───────────
            // 사용량(= 금일 − 전일)은 POWER_USAGE 에서 세부 65~72행으로 산출한다.
            str("D24", 60, "MAIN 전일지침"),        str("G24", 61, "MAIN 금일지침"),
            str("D25", 62, "ASH 저장조 전일지침"),  str("G25", 63, "ASH 저장조 금일지침"),
            str("D26", 64, "BAG FILTER 전일지침"),  str("G26", 65, "BAG FILTER 금일지침"),
            str("D27", 66, "SCRUBBER 전일지침"),    str("G27", 67, "SCRUBBER 금일지침"),
            str("D28", 68, "활성탄 공급 전일지침"), str("G28", 69, "활성탄 공급 금일지침"),
            str("D29", 70, "소석회 공급 전일지침"), str("G29", 71, "소석회 공급 금일지침"),
            str("D30", 72, "요소수 공급 전일지침"), str("G30", 73, "요소수 공급 금일지침"),
            str("D31", 74, "F.D 전일지침"),         str("G31", 75, "F.D 금일지침"),
            str("D32", 76, "활성탄 BAG 전일지침"),  str("G32", 77, "활성탄 BAG 금일지침"),
            str("D33", 78, "소석회 BAG 전일지침"),  str("G33", 79, "소석회 BAG 금일지침"),

            // ── 6. 유틸리티 사용현황 (전일 S / 금일 V) ─────────────
            str("S24", 90, "재이용수 전일"),   num("V24", Store.DETAIL, 74, "재이용수 금일"),
            str("S25", 92, "복류수 전일"),     num("V25", Store.DETAIL, 75, "복류수 금일"),
            str("S26", 94, "응축수 전일"),     num("V26", Store.DETAIL, 76, "응축수 금일"),
            str("S27", 96, "보일러 급수 전일"), num("V27", Store.DETAIL, 77, "보일러 급수 금일"),
            str("S28", 98, "폐수 전일"),       num("V28", Store.DETAIL, 78, "폐수 금일"),
            str("S29", 100, "연수 전일"),      num("V29", Store.DETAIL, 79, "연수 금일")
            // 7. 특이사항은 양식에서 제외됨 (2026-08 회의 결정, 엑셀 33행 아래 삭제)
    );

    /** 전력 사용량 = 금일지침 − 전일지침 → 세부 운영내역 행 (F.D 까지만 세부에 항목이 있음) */
    public record PowerUsage(String prevRef, String todayRef, int detailRow, String label) {}

    public static final List<PowerUsage> POWER_USAGE = List.of(
            new PowerUsage("D24", "G24", 65, "MAIN"),
            new PowerUsage("D25", "G25", 66, "ASH 저장조"),
            new PowerUsage("D26", "G26", 67, "BAG FILTER"),
            new PowerUsage("D27", "G27", 68, "SCRUBBER"),
            new PowerUsage("D28", "G28", 69, "활성탄 공급"),
            new PowerUsage("D29", "G29", 70, "소석회 공급"),
            new PowerUsage("D30", "G30", 71, "요소수 공급"),
            new PowerUsage("D31", "G31", 72, "F.D")
    );

    /** 시트 배치 확인용 앵커 — 이 셀 값이 다르면 양식이 어긋난 시트다. */
    public record Anchor(String ref, String expected) {}

    public static final List<Anchor> DAY_ANCHORS = List.of(
            new Anchor("A6", "구 분"),
            new Anchor("A11", "품 명"),
            new Anchor("A23", "구 분")
    );

    /** 시트 날짜(검증용) */
    public static final String DATE_REF = "H3";

    // ── 함수율 시트 ────────────────────────────────────────────────
    /** 첫 데이터 행(1-based). 하루가 오전/오후/야간 3행을 차지한다. */
    public static final int MOISTURE_FIRST_ROW = 3;
    public static final int MOISTURE_ROWS_PER_DAY = 3;
    public static final String MOISTURE_DATE_COL = "B";
    /** 탈수기 1~5번의 취득시간 / 함수율 열 */
    public static final String[] MOISTURE_TIME_COLS = {"D", "F", "H", "J", "L"};
    public static final String[] MOISTURE_VALUE_COLS = {"E", "G", "I", "K", "M"};

    // ── 셀 주소 파서 ───────────────────────────────────────────────
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

    /** 매핑에 쓰인 셀 주소 목록 (점검/문서용) */
    public static List<String> mappedRefs() {
        List<String> refs = new ArrayList<>();
        DAY_CELLS.forEach(entry -> refs.add(entry.ref()));
        return refs;
    }

    private FluidizedDailyLogCellMap() {
    }
}
