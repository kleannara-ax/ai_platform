package com.company.module.steamenergy.legacy.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 유동상 운전일지 엑셀 ↔ 저장 위치 매핑표.
 *
 * <p>1~31일 시트는 배치가 같으므로 아래 표 하나로 모든 날짜를 처리한다.
 * 엑셀 셀 주소를 그대로 적어 원본 양식과 눈으로 대조할 수 있게 했다.
 *
 * <p><b>칸은 헤더 글자로 찾는다.</b> 이 시트는 표가 아니라 양식(form)이라 칸 하나가
 * <b>(섹션, 행 라벨, 열 라벨)</b> 로 정해진다. 예를 들어 탄산암모늄 입고량은
 * {@code ("3. 약품", "탄산암모늄", "입고량")} 이다. 좌표만 쓰면 양식에 행이 하나
 * 추가될 때 아래 항목이 통째로 밀려 말없이 엉뚱한 값이 들어가므로,
 * {@link FluidizedDailyLogSheetIndex} 가 섹션 블록을 잡아 라벨로 위치를 해석한다.
 * {@link Entry#ref()} 의 좌표는 <b>힌트 겸 폴백</b>으로 남는다.
 *
 * <p>헤더 글자는 <b>원본 시트에 적힌 그대로</b> 적는다(공백 포함 — "소 금", "폐 수").
 * 비교할 때 공백·괄호·{@code # · - _} 는 무시하므로 띄어쓰기가 달라도 찾는다.
 * 라벨이 없는 칸(근무자 3칸)은 헤더 없이 좌표로만 찾는다.
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
     * 시트에 적힌 헤더 글자.
     *
     * @param section  섹션 제목 앞부분 (예: "3. 약품")
     * @param rowLabel 섹션의 라벨 열에 적힌 행 이름 (예: "탄산암모늄")
     * @param colLabel 그 항목 위쪽 헤더 행에 적힌 열 이름 (예: "입고량")
     */
    public record Header(String section, String rowLabel, String colLabel) {}

    /**
     * @param ref    엑셀 셀 주소 (예: "D7") — 헤더로 못 찾았을 때의 폴백 좌표
     * @param store  저장 위치
     * @param target DETAIL=세부 행 번호 / LOG=col_index / FLOW=미사용
     * @param scale  단위 환산 (KG → 톤 = 0.001, 그 외 1)
     * @param text   숫자가 아니라 글자 그대로 저장할 항목 (근무자·특이사항 등)
     * @param label  주석용 항목명
     * @param header 시트 헤더 글자. null 이면 라벨이 없는 칸이라 좌표로만 찾는다
     */
    public record Entry(String ref, Store store, int target, double scale, boolean text,
                        String label, Header header) {
        public int row() { return parseRow(ref) - 1; }   // POI 0-based
        public int col() { return parseCol(ref) - 1; }
    }

    private static final double TON = 0.001d;

    // 섹션 제목 앞부분 — 뒤의 단위 표기((KG), (TON, HR) 등)는 비교하지 않는다.
    private static final String S1 = "1. 소각현황";
    private static final String S2 = "2. BOILER";
    private static final String S3 = "3. 약품";
    private static final String S4 = "4. 소각재";
    private static final String S5 = "5. 전력";
    private static final String S6 = "6. 유틸리티";

    private static Header h(String section, String rowLabel, String colLabel) {
        return new Header(section, rowLabel, colLabel);
    }

    private static Entry num(String ref, Store store, int target, String label, Header header) {
        return new Entry(ref, store, target, 1, false, label, header);
    }

    private static Entry ton(String ref, Store store, int target, String label, Header header) {
        return new Entry(ref, store, target, TON, false, label, header);
    }

    private static Entry str(String ref, int colIndex, String label, Header header) {
        return new Entry(ref, Store.LOG, colIndex, 1, true, label, header);
    }

    /** 라벨이 없는 칸 — 좌표로만 찾는다. */
    private static Entry strAt(String ref, int colIndex, String label) {
        return new Entry(ref, Store.LOG, colIndex, 1, true, label, null);
    }

    /** 하루치 시트 전체 매핑 (엑셀 셀 → 저장 위치) */
    public static final List<Entry> DAY_CELLS = List.of(
            // ── 근무자 (1~3행) — 라벨 칸이 없어 좌표로만 찾는다 ────
            strAt("B2", 1, "근무자 오전"),
            strAt("D2", 2, "근무자 오후"),
            strAt("F2", 3, "근무자 야간"),

            // ── 1. 소각현황 (KG) → 세부는 톤 ───────────────────────
            ton("D7", Store.DETAIL, 9, "SRF 소각량", h(S1, "금 일", "SRF소각량")),
            ton("G7", Store.DETAIL, 8, "SLUDGE 소각량", h(S1, "금 일", "SLUDGE 소각량")),

            // ── 2. BOILER 운전현황 ────────────────────────────────
            num("R7", Store.DETAIL, 6, "스팀 생산량", h(S2, "금 일", "스팀 생산량")),
            num("U7", Store.FLOW, 0, "스팀 송기량", h(S2, "금 일", "스팀 송기량")),
            num("X7", Store.DETAIL, 4, "가동시간", h(S2, "금 일", "가동시간")),

            // ── 3. 약품 사용현황 (전일재고 C / 입고 E / 사용 G) ────
            str("C12", 20, "탄산암모늄 전일재고", h(S3, "탄산암모늄", "전일 재고량")),
            num("E12", Store.DETAIL, 18, "탄산암모늄 입고", h(S3, "탄산암모늄", "입고량")),
            num("G12", Store.DETAIL, 19, "탄산암모늄 사용", h(S3, "탄산암모늄", "사용량")),
            str("C13", 23, "청관제 전일재고", h(S3, "청관제", "전일 재고량")),
            num("E13", Store.DETAIL, 21, "청관제 입고", h(S3, "청관제", "입고량")),
            num("G13", Store.DETAIL, 22, "청관제 사용", h(S3, "청관제", "사용량")),
            str("C14", 26, "활성탄 전일재고", h(S3, "활성탄", "전일 재고량")),
            num("E14", Store.DETAIL, 24, "활성탄 입고", h(S3, "활성탄", "입고량")),
            num("G14", Store.DETAIL, 25, "활성탄 사용", h(S3, "활성탄", "사용량")),
            str("C15", 29, "가성소다 전일재고", h(S3, "가성소다", "전일 재고량")),
            num("E15", Store.DETAIL, 27, "가성소다 입고", h(S3, "가성소다", "입고량")),
            num("G15", Store.DETAIL, 28, "가성소다 사용", h(S3, "가성소다", "사용량")),
            str("C16", 32, "소석회 전일재고", h(S3, "소석회", "전일 재고량")),
            num("E16", Store.DETAIL, 30, "소석회 입고", h(S3, "소석회", "입고량")),
            num("G16", Store.DETAIL, 31, "소석회 사용", h(S3, "소석회", "사용량")),
            str("C17", 35, "소금 전일재고", h(S3, "소 금", "전일 재고량")),
            num("E17", Store.DETAIL, 33, "소금 입고", h(S3, "소 금", "입고량")),
            num("G17", Store.DETAIL, 34, "소금 사용", h(S3, "소 금", "사용량")),
            str("C18", 38, "경유 전일재고", h(S3, "경 유", "전일 재고량")),
            num("E18", Store.DETAIL, 36, "경유 입고", h(S3, "경 유", "입고량")),
            num("G18", Store.DETAIL, 37, "경유 사용", h(S3, "경 유", "사용량")),
            str("C19", 41, "SRF 전일재고", h(S3, "SRF", "전일 재고량")),
            ton("E19", Store.DETAIL, 46, "SRF 입고", h(S3, "SRF", "입고량")),
            // SRF 사용(G19)은 세부에서 소각량(9행)으로 산출되므로 기록하지 않는다.
            str("C20", 44, "규사 전일재고", h(S3, "규 사", "전일 재고량")),
            num("E20", Store.DETAIL, 39, "규사 입고", h(S3, "규 사", "입고량")),
            num("G20", Store.DETAIL, 40, "규사 사용", h(S3, "규 사", "사용량")),

            // ── 4. 소각재 발생량 ──────────────────────────────────
            str("S12", 50, "비산재 반출량", h(S4, "비산재", "반출량")),
            str("S14", 51, "바닥재 반출량", h(S4, "바닥재", "반출량")),
            str("S18", 52, "불연물 주간", h(S4, "불연물", "주간")),
            str("U18", 53, "불연물 야간", h(S4, "불연물", "야간")),

            // ── 5. 전력 사용량 (전일지침 D / 금일지침 G) ───────────
            // 사용량(= 금일 − 전일)은 POWER_USAGE 에서 세부 65~72행으로 산출한다.
            str("D24", 60, "MAIN 전일지침", h(S5, "MAIN", "전일지침")),
            str("G24", 61, "MAIN 금일지침", h(S5, "MAIN", "금일지침")),
            str("D25", 62, "ASH 저장조 전일지침", h(S5, "ASH 저장조", "전일지침")),
            str("G25", 63, "ASH 저장조 금일지침", h(S5, "ASH 저장조", "금일지침")),
            str("D26", 64, "BAG FILTER 전일지침", h(S5, "BAG FILTER", "전일지침")),
            str("G26", 65, "BAG FILTER 금일지침", h(S5, "BAG FILTER", "금일지침")),
            str("D27", 66, "SCRUBBER 전일지침", h(S5, "SCRUBBER", "전일지침")),
            str("G27", 67, "SCRUBBER 금일지침", h(S5, "SCRUBBER", "금일지침")),
            str("D28", 68, "활성탄 공급 전일지침", h(S5, "활성탄 공급", "전일지침")),
            str("G28", 69, "활성탄 공급 금일지침", h(S5, "활성탄 공급", "금일지침")),
            str("D29", 70, "소석회 공급 전일지침", h(S5, "소석회 공급", "전일지침")),
            str("G29", 71, "소석회 공급 금일지침", h(S5, "소석회 공급", "금일지침")),
            str("D30", 72, "요소수 공급 전일지침", h(S5, "요소수 공급", "전일지침")),
            str("G30", 73, "요소수 공급 금일지침", h(S5, "요소수 공급", "금일지침")),
            str("D31", 74, "F.D 전일지침", h(S5, "F.D", "전일지침")),
            str("G31", 75, "F.D 금일지침", h(S5, "F.D", "금일지침")),
            str("D32", 76, "활성탄 BAG 전일지침", h(S5, "활성탄 BAG", "전일지침")),
            str("G32", 77, "활성탄 BAG 금일지침", h(S5, "활성탄 BAG", "금일지침")),
            str("D33", 78, "소석회 BAG 전일지침", h(S5, "소석회 BAG", "전일지침")),
            str("G33", 79, "소석회 BAG 금일지침", h(S5, "소석회 BAG", "금일지침")),

            // ── 6. 유틸리티 사용현황 (전일 S / 금일 V) ─────────────
            str("S24", 90, "재이용수 전일", h(S6, "재이용수", "전일 사용량")),
            num("V24", Store.DETAIL, 74, "재이용수 금일", h(S6, "재이용수", "금일 사용량")),
            str("S25", 92, "복류수 전일", h(S6, "복류수", "전일 사용량")),
            num("V25", Store.DETAIL, 75, "복류수 금일", h(S6, "복류수", "금일 사용량")),
            str("S26", 94, "응축수 전일", h(S6, "응축수", "전일 사용량")),
            num("V26", Store.DETAIL, 76, "응축수 금일", h(S6, "응축수", "금일 사용량")),
            str("S27", 96, "보일러 급수 전일", h(S6, "보일러 급수", "전일 사용량")),
            num("V27", Store.DETAIL, 77, "보일러 급수 금일", h(S6, "보일러 급수", "금일 사용량")),
            str("S28", 98, "폐수 전일", h(S6, "폐 수", "전일 사용량")),
            num("V28", Store.DETAIL, 78, "폐수 금일", h(S6, "폐 수", "금일 사용량")),
            str("S29", 100, "연수 전일", h(S6, "연 수", "전일 사용량")),
            num("V29", Store.DETAIL, 79, "연수 금일", h(S6, "연 수", "금일 사용량"))
            // 7. 특이사항은 양식에서 제외됨 (2026-08 회의 결정, 엑셀 33행 아래 삭제)
    );

    /**
     * 전력 사용량 = 금일지침 − 전일지침 → 세부 운영내역 행 (F.D 까지만 세부에 항목이 있음).
     *
     * @param rowLabel 5. 전력 섹션의 행 라벨 — 좌표 대신 이 이름으로 찾는다
     */
    public record PowerUsage(String prevRef, String todayRef, int detailRow, String label, String rowLabel) {}

    public static final List<PowerUsage> POWER_USAGE = List.of(
            new PowerUsage("D24", "G24", 65, "MAIN", "MAIN"),
            new PowerUsage("D25", "G25", 66, "ASH 저장조", "ASH 저장조"),
            new PowerUsage("D26", "G26", 67, "BAG FILTER", "BAG FILTER"),
            new PowerUsage("D27", "G27", 68, "SCRUBBER", "SCRUBBER"),
            new PowerUsage("D28", "G28", 69, "활성탄 공급", "활성탄 공급"),
            new PowerUsage("D29", "G29", 70, "소석회 공급", "소석회 공급"),
            new PowerUsage("D30", "G30", 71, "요소수 공급", "요소수 공급"),
            new PowerUsage("D31", "G31", 72, "F.D", "F.D")
    );

    /** 전력 섹션 제목 — POWER_USAGE 해석에 쓴다. */
    public static final String POWER_SECTION = S5;
    /** 전력 섹션의 전일/금일 열 이름. */
    public static final String POWER_PREV_COL = "전일지침";
    public static final String POWER_TODAY_COL = "금일지침";

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
