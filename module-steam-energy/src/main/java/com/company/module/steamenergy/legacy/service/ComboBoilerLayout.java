package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 복합보일러 "스팀생산실적(N월)" 시트의 배치 — <b>머리글 글자로</b> 열·행을 찾는다.
 *
 * <p>예전에는 열을 {@code comboSteam=B, kn20=Q, runtimeMin=AM} 처럼, 행을
 * {@code 7행=1일, 42행=LNG 연료단가, 50행=벤트 생산량, 58행=회수량 첫 줄} 처럼 코드에 박아 뒀다.
 * 그래서 원본에서 열이나 행을 하나만 지워도 그 뒤가 전부 한 칸씩 밀린 채
 * <b>다른 항목으로 저장</b>됐다.
 *
 * <p>한 시트에 성격이 다른 표가 다섯 벌 있어 표마다 따로 찾는다.
 *
 * <pre>
 *  ① 일별 표      4행 설비 / 5행 항목 / 6행 단위·세부, 7행부터 A열이 1~31
 *  ② 연료단가     41행 "구 분 … 연료단가", 42~46행 B열이 설비 이름
 *  ③ 벤트 손실    49행 "구 분 / 단가(전월) / 1월1일 …", 50~54행 C열이 항목
 *  ④ 스팀 회수량  57행 "날짜 / 스팀회수 감소시간 / 내용 …", 58행부터 데이터
 *  ⑤ 연간 계획    Q열의 "복합보일러" · "제스코" · "LNG사용량" 제목 아래 월 표
 * </pre>
 *
 * <p>①의 열은 머리글이 세 줄(설비 / 항목 / 단위·세부)이다. 열마다 그 세 줄의 글자를 모아
 * <b>항목마다 정해 둔 조건</b>으로 찾는다 — "설비가 복합보일러이고 항목이 스팀구매량",
 * "세부가 20톤이고 설비가 가동시간이 아님" 같은 식이다. 열의 <b>순서나 위치에 기대지 않으므로</b>
 * 열이 추가·삭제되거나 자리가 바뀌어도 같은 항목을 찾는다.
 *
 * <p>양식이 해마다 조금씩 바뀐다. 2026년 10월부터 폐합성 1호가 빠지고 유동상 보일러에
 * "총스팀량(FI-301) · 응축수(FI-303)"가 생겼고, 11월부터 외부보일러가 빠지고
 * "폐합성소각로 기관실유량계"가 생겼다. 새로 생긴 세 칸은 <b>참고자료</b>라 합계에 넣지 않는다.
 *
 * <p>월 시트마다 표기도 조금씩 다르다(1월 "디스파샤" ↔ 10월 "디스퍼져",
 * 1월 "시간" ↔ 10월 "가동시간(분)"). 그래서 조건에 <b>후보 이름을 여러 개</b> 둔다.
 *
 * <p>못 찾으면 그 항목·그 줄은 <b>건너뛴다.</b> 열 번호로 폴백하면 값이 엉뚱한 항목에 붙는다.
 * 값을 잘못 넣느니 넣지 않고 {@link #notFound()} 로 알리는 편이 안전하다.
 *
 * <p>행은 서비스와 맞춰 <b>1-based</b>, 열은 <b>0-based</b> 로 돌려준다.
 */
final class ComboBoilerLayout {


    /** 일별 표 항목 줄의 기본 위치(0-based) — 원본은 4행 설비 / 5행 항목 / 6행 단위·세부. A열 "일" 로 다시 찾는다. */
    private static final int ITEM_ROW = 4;
    private static final int FIRST_DAY_ROW = 6;
    private static final int LAST_DAY_ROW = 40;
    private static final int DAY_COL = 0;

    /** 보조 표를 찾아볼 범위(1-based 행) — 원본은 89행까지 쓴다. */
    private static final int MAX_ROW = 110;
    private static final int MAX_COL = 40;

    private static final Pattern DAY_OF_MONTH = Pattern.compile("(\\d{1,2})\\s*일");
    private static final Pattern MONTH_LABEL = Pattern.compile("(\\d{1,2})\\s*월");

    /** 라벨이 적힌 열(0-based) — 연료단가는 B, 벤트는 C, 회수량은 A, 연간 계획은 Q. */
    private static final int FUEL_PRICE_LABEL_COL = 1;
    private static final int VENT_LABEL_COL = 2;
    private static final int RECOVERY_DATE_COL = 0;
    private static final int PLAN_LABEL_COL = 16;

    /** 계측기 번호만 적힌 세부 칸("FI-301", "FIT-304") — 정규화하면 {@code fi301} 이 된다. */
    private static final Pattern FLOW_TAG = Pattern.compile("^[a-z]{2,4}\\d{1,4}$");

    /** "설비가 복합보일러/외부보일러이고 항목이 …" 로 찾는 칸 — {@code {키, 설비, 항목}}. */
    private static final String[][] PURCHASE_FIELDS = {
            {"comboSteam", "복합보일러", "스팀구매량"},
            {"comboCondensate", "복합보일러", "응축수판매량"},
            {"comboMakeup", "복합보일러", "보충수판매량"},
            {"comboDeaerator", "복합보일러", "탈기기급수량"},
            {"comboProcess", "복합보일러", "공정수판매량"},
            {"comboDilution", "복합보일러", "희석수판매량"},
            {"externalSteam", "외부보일러", "스팀구매량"},
            {"externalCondensate", "외부보일러", "응축수판매량"},
            {"externalMakeup", "외부보일러", "보충수판매량"}
    };

    /** 일별 표의 한 열 — 머리글 세 줄의 글자(정규화한 것)와 세부 칸의 원문. */
    private record HeaderCol(int col, String group, String item, String detail, String rawDetail) {
        /** 가장 구체적인 이름 — 세부 칸이 단위·계측기 번호뿐이면 항목 칸의 글자다. */
        String leaf() {
            return detail.isEmpty() || FLOW_TAG.matcher(detail).matches() ? item : detail;
        }

        boolean runtimeGroup() {
            return group.contains("가동시간");
        }
    }

    /** 연료단가 표의 설비 이름 — 42~46행 B열. */
    private static final String[][] FUEL_PRICE_ROWS = {
            {"fuelPrice-lngBoiler", "LNG 보일러"},
            {"fuelPrice-fluidized", "유동상소각로"},
            {"fuelPrice-combo", "복합보일러"},
            {"fuelPrice-waste", "폐합성소각로"},
            {"fuelPrice-external", "외부보일러"}
    };

    /** 벤트 손실 표의 항목 — 50~52행 C열. 괄호 설명은 정규화 때 지워진다. */
    private static final String[][] VENT_ROWS = {
            {"ventProduction", "생산량"},       // 생산량 (FIT-301)
            {"ventInternal", "소내 사용량"},     // 소내 사용량 (추정)
            {"ventUsage", "사용량"}             // 사용량 (FIT-304)
    };

    /** 연간 계획 표 — {@code {키, 제목, 행 라벨…}}. 제목은 Q열에 있다. */
    private static final String[][] PLAN_BLOCKS = {
            {"combo", "복합보일러", "기본", "특약", "소 계", "실적", "잔량"},
            {"jesco", "제스코", "계획", "실적", "잔량"},
            {"lng", "LNG사용량", "보일러", "복합"}
    };

    /** 연간 계획 표의 행 키 — 서비스가 쓰는 접두어와 짝이 맞는 순서다. */
    private static final Map<String, String[]> PLAN_ROW_KEYS = Map.of(
            "combo", new String[]{"combo-plan-1-", "combo-plan-2-", "combo-plan-3-", "combo-plan-4-", "combo-plan-5-"},
            "jesco", new String[]{"jesco-plan-0-", "jesco-plan-1-", "jesco-plan-2-"},
            "lng", new String[]{"lngBoiler-", "lngCombo-"}
    );

    /**
     * 연간 계획 표 한 벌.
     *
     * @param rows          행 키 → 1-based 행
     * @param firstMonthCol 첫 월 열(0-based)
     * @param lastMonthCol  마지막 월 열(0-based) — "계" 칸 바로 왼쪽
     */
    record PlanBlock(Map<String, Integer> rows, int firstMonthCol, int lastMonthCol) {
        boolean usable() {
            return !rows.isEmpty() && firstMonthCol >= 0 && lastMonthCol >= firstMonthCol;
        }
    }

    private final Map<String, Integer> dayColumns = new LinkedHashMap<>();
    private final Map<Integer, Integer> dayRows = new LinkedHashMap<>();
    private final Map<String, Integer> fuelPriceRows = new LinkedHashMap<>();
    private final Map<String, Integer> ventRows = new LinkedHashMap<>();
    private final Map<Integer, Integer> ventDayColumns = new LinkedHashMap<>();
    private final Map<String, Integer> recoveryColumns = new LinkedHashMap<>();
    private final Map<String, PlanBlock> planBlocks = new LinkedHashMap<>();
    private final List<String> notFound = new ArrayList<>();
    private int fuelPriceColumn = -1;
    private int ventUnitPriceColumn = -1;
    private int recoveryFirstRow = -1;

    private ComboBoilerLayout() {
    }

    static ComboBoilerLayout of(Sheet sheet) {
        ComboBoilerLayout layout = new ComboBoilerLayout();
        layout.readDayTable(sheet);
        layout.readFuelPriceTable(sheet);
        layout.readVentTable(sheet);
        layout.readRecoveryTable(sheet);
        layout.readPlanBlocks(sheet);
        return layout;
    }

    // ── ① 일별 표 ────────────────────────────────────────────────────────────

    private void readDayTable(Sheet sheet) {
        // 머리글 줄: A열이 "일" 인 줄이 항목 줄이고 그 위가 설비, 아래가 단위·세부다. 못 찾으면 원본의 4~6행.
        int itemRow = ITEM_ROW;
        for (int row = 0; row <= 14; row += 1) {
            if (normalize(text(sheet, row + 1, DAY_COL)).equals("일")) {
                itemRow = row;
                break;
            }
        }
        int groupRow = Math.max(itemRow - 1, 0);
        int detailRow = itemRow + 1;

        List<HeaderCol> cols = headerColumns(sheet, groupRow, itemRow, detailRow);

        for (String[] field : PURCHASE_FIELDS) {
            String group = normalize(field[1]);
            String item = normalize(field[2]);
            putDay(field[0], field[1] + " " + field[2], first(cols, c -> c.group().equals(group) && c.item().equals(item)));
        }
        // 폐합성 소각로 — 10월 양식의 설비 칸은 "폐합성소각로 깨끗한나라 보일러" 라서 항목 이름으로 찾는다.
        putDay("wasteSteam1", "폐합성 소각로 1호 스팀 구입량", first(cols, c -> c.item().equals("1호스팀구입량")));
        putDay("wasteSteam2", "폐합성 소각로 2호 스팀 구입량", first(cols, c -> c.item().equals("2호스팀구입량")));
        putDay("wasteRoomSteam2", "폐합성소각로 기관실유량계 2호 스팀량",
                first(cols, c -> c.group().contains("기관실") && c.item().startsWith("2호")));

        // 깨끗한나라 보일러 스팀생산량 ↔ 당사 보일러 가동시간 — 세부 이름(20톤·50톤)이 같아 설비로 가른다.
        putDay("kn20", "깨끗한나라 보일러 20톤", first(cols, c -> !c.runtimeGroup() && c.detail().equals("20톤")));
        putDay("kn50", "깨끗한나라 보일러 50톤", first(cols, c -> !c.runtimeGroup() && c.detail().equals("50톤")));
        putDay("runtime20", "가동시간 20톤", first(cols, c -> c.runtimeGroup() && c.detail().equals("20톤")));
        putDay("runtime50", "가동시간 50톤", first(cols, c -> c.runtimeGroup() && c.detail().equals("50톤")));
        putDay("runtimeFluidized", "가동시간 유동상", first(cols, c -> c.runtimeGroup() && c.detail().equals("유동상")));

        readFluidizedColumns(cols);
        readChangeColumns(cols);

        putDay("down1", "#1호기 운휴시간", first(cols, c -> c.detail().equals("1호기")));
        putDay("down2", "#2호기 운휴시간", first(cols, c -> c.detail().equals("2호기")));
        // "스팀량" 은 디스퍼져분과 시간당(계산)이 나란히 있다 — 항목 칸이 디스퍼져(1월 "디스파샤")인 쪽이다.
        HeaderCol disperser = first(cols, c -> c.detail().equals("스팀량")
                && (c.item().contains("디스퍼") || c.item().contains("디스파")));
        if (disperser == null) disperser = first(cols, c -> c.detail().equals("스팀량") && !c.item().contains("시간당"));
        putDay("disperserSteam", "디스퍼져 스팀량", disperser);
        // 1월 "시간" ↔ 10월 "가동시간(분)"
        putDay("runtimeMin", "디스퍼져 가동시간(분)",
                first(cols, c -> c.detail().equals("시간") || c.detail().equals("가동시간")));

        int lastDayRow = detailRow + 1 + (LAST_DAY_ROW - FIRST_DAY_ROW);
        HeaderColumnIndex days = HeaderColumnIndex.build(sheet, -1, groupRow, itemRow, detailRow + 1, lastDayRow, DAY_COL);
        for (int day = 1; day <= 31; day += 1) {
            Integer row = days.rowOfDay(day);
            if (row != null) dayRows.put(day, row + 1);   // 서비스는 1-based 행을 쓴다
        }
        if (dayRows.isEmpty()) notFound.add("일별 표의 일자 칸(A열)");
    }

    /**
     * 유동상 — 옛 양식은 "스팀 생산량 · 응축수" 두 칸, 2026년 10월부터는
     * "총스팀량(FI-301) · 응축수(FI-303) · 스팀 생산량(FI-304) · 응축수(FI-07)" 네 칸이다.
     * "응축수" 가 두 번 나오므로 <b>바로 왼쪽의 스팀 칸</b>이 어느 쪽인지로 가른다(쌍이 통째로 자리를 바꿔도 맞는다).
     */
    private void readFluidizedColumns(List<HeaderCol> cols) {
        List<HeaderCol> fluidized = cols.stream()
                .filter(c -> c.group().contains("유동상") && !c.runtimeGroup())
                .toList();
        HeaderCol total = first(fluidized, c -> c.leaf().equals("총스팀량"));
        HeaderCol production = first(fluidized, c -> c.leaf().equals("스팀생산량"));
        putDay("fluidizedTotalSteam", "유동상 보일러 총스팀량", total);
        putDay("fluidizedSteam", "유동상 스팀 생산량", production);

        HeaderCol totalCondensate = null;
        HeaderCol productionCondensate = null;
        for (HeaderCol c : fluidized) {
            if (!c.leaf().equals("응축수")) continue;
            HeaderCol owner = null;                       // 왼쪽에서 가장 가까운 스팀 칸
            for (HeaderCol steam : new HeaderCol[]{total, production}) {
                if (steam == null || steam.col() > c.col()) continue;
                if (owner == null || steam.col() > owner.col()) owner = steam;
            }
            if (owner != null && owner == total) {
                if (totalCondensate == null) totalCondensate = c;
            } else if (productionCondensate == null) {
                productionCondensate = c;
            }
        }
        putDay("fluidizedCondensate", "유동상 응축수", productionCondensate);
        if (total != null) putDay("fluidizedTotalCondensate", "유동상 보일러 총스팀량 응축수", totalCondensate);
    }

    /** "변동사항(복합보일러)" ↔ "변동사항(제스코,유동상 보일러)" — 괄호 안 글자로 가르고, 없으면 왼쪽이 복합이다. */
    private void readChangeColumns(List<HeaderCol> cols) {
        List<HeaderCol> changes = cols.stream().filter(c -> c.detail().equals("변동사항")).toList();
        HeaderCol combo = first(changes, c -> c.rawDetail().contains("복합"));
        HeaderCol jesco = first(changes, c -> c.rawDetail().contains("제스코") || c.rawDetail().contains("유동상"));
        if (combo == null && jesco == null && changes.size() >= 2) {
            combo = changes.get(0);
            jesco = changes.get(1);
        }
        putDay("changeCombo", "변동사항(복합보일러)", combo);
        putDay("changeJesco", "변동사항(제스코)", jesco);
    }

    /** 머리글 세 줄을 열마다 모은다. 설비 칸은 병합돼 있어 왼쪽 값을 이어받는다. */
    private static List<HeaderCol> headerColumns(Sheet sheet, int groupRow, int itemRow, int detailRow) {
        int lastCol = 0;
        for (int rowIndex : new int[]{groupRow, itemRow, detailRow}) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) lastCol = Math.max(lastCol, row.getLastCellNum());
        }
        List<HeaderCol> cols = new ArrayList<>();
        String group = "";
        for (int col = DAY_COL + 1; col <= lastCol; col += 1) {
            String groupText = normalize(text(sheet, groupRow + 1, col));
            if (!groupText.isEmpty()) group = groupText;
            String rawDetail = text(sheet, detailRow + 1, col);
            cols.add(new HeaderCol(col, group, normalize(text(sheet, itemRow + 1, col)), normalize(rawDetail), rawDetail));
        }
        return cols;
    }

    private static HeaderCol first(List<HeaderCol> cols, java.util.function.Predicate<HeaderCol> condition) {
        for (HeaderCol col : cols) {
            if (condition.test(col)) return col;
        }
        return null;
    }

    private void putDay(String key, String what, HeaderCol col) {
        if (col == null) notFound.add(what);
        else dayColumns.put(key, col.col());
    }

    // ── ② 연료단가 표 ────────────────────────────────────────────────────────

    private void readFuelPriceTable(Sheet sheet) {
        // "구 분" 줄은 벤트 손실 표에도 있다 — "연료단가" 칸이 같이 있는 줄만 고른다.
        int headerRow = -1;
        for (int row = 1; row <= MAX_ROW && headerRow < 0; row += 1) {
            if (!normalize(text(sheet, row, FUEL_PRICE_LABEL_COL)).equals("구분")) continue;
            if (findColumnContaining(sheet, row, "연료단가") >= 0) headerRow = row;
        }
        if (headerRow < 0) {
            notFound.add("연료단가 표의 '구 분' 줄");
            return;
        }
        fuelPriceColumn = findColumnContaining(sheet, headerRow, "연료단가");
        for (String[] field : FUEL_PRICE_ROWS) {
            int row = findRowByLabel(sheet, headerRow + 1, MAX_ROW, FUEL_PRICE_LABEL_COL, field[1]);
            if (row < 0) {
                notFound.add("연료단가 " + field[1]);
                continue;
            }
            fuelPriceRows.put(field[0], row);
        }
    }

    // ── ③ 벤트 손실 표 ───────────────────────────────────────────────────────

    private void readVentTable(Sheet sheet) {
        // "구 분" 줄은 연료단가 표에도 있다 — 날짜 칸("1월1일", "2일" …)이 늘어선 줄만 고른다.
        int headerRow = -1;
        for (int row = 1; row <= MAX_ROW && headerRow < 0; row += 1) {
            if (!normalize(text(sheet, row, FUEL_PRICE_LABEL_COL)).equals("구분")) continue;
            if (!dayColumnsOf(sheet, row).isEmpty()) headerRow = row;
        }
        if (headerRow < 0) {
            notFound.add("벤트 손실 표의 머리글 줄");
            return;
        }
        ventDayColumns.putAll(dayColumnsOf(sheet, headerRow));
        ventUnitPriceColumn = firstColumnEqualTo(sheet, headerRow, "단가");
        if (ventUnitPriceColumn < 0) notFound.add("벤트 손실비용 단가 열");

        for (String[] field : VENT_ROWS) {
            int row = findRowByLabel(sheet, headerRow + 1, MAX_ROW, VENT_LABEL_COL, field[1]);
            if (row < 0) {
                notFound.add("벤트 " + field[1]);
                continue;
            }
            ventRows.put(field[0], row);
        }
        int lossRow = findRowByLabel(sheet, headerRow + 1, MAX_ROW, VENT_LABEL_COL, "손실비용");
        if (lossRow < 0) {
            notFound.add("벤트 손실비용 줄");
            ventUnitPriceColumn = -1;
        } else {
            ventRows.put("ventLossUnitPrice", lossRow);
        }
    }

    // ── ④ 스팀 회수량 표 ─────────────────────────────────────────────────────

    private void readRecoveryTable(Sheet sheet) {
        int headerRow = -1;
        for (int row = 1; row <= MAX_ROW && headerRow < 0; row += 1) {
            if (normalize(text(sheet, row, RECOVERY_DATE_COL)).equals("날짜")) headerRow = row;
        }
        if (headerRow < 0) {
            notFound.add("스팀 회수량 표의 '날짜' 줄");
            return;
        }
        recoveryFirstRow = headerRow + 1;
        recoveryColumns.put("lossDate", RECOVERY_DATE_COL);                   // "날짜" 칸 자신
        putRecoveryColumn(sheet, headerRow, "lossTime", "스팀회수 감소시간");
        putRecoveryColumn(sheet, headerRow, "lossNote", "내용");
        Integer rate = column(sheet, headerRow, "스팀 감소량");
        if (rate == null) {
            notFound.add("스팀 감소량 열");
        } else {
            // "스팀 감소량(T/H)" 은 두 칸이 병합돼 있다 — 왼쪽이 감소율, 오른쪽이 기준값이다.
            recoveryColumns.put("lossSteamRate", rate);
            recoveryColumns.put("lossBaseRate", rate + 1);
        }
        putRecoveryColumn(sheet, headerRow, "lossMinutes", "감소시간");
    }

    private void putRecoveryColumn(Sheet sheet, int headerRow, String key, String label) {
        Integer col = column(sheet, headerRow, label);
        if (col == null) {
            notFound.add("스팀 회수량 " + label);
            return;
        }
        recoveryColumns.put(key, col);
    }

    // ── ⑤ 연간 계획 표 ───────────────────────────────────────────────────────

    private void readPlanBlocks(Sheet sheet) {
        for (String[] block : PLAN_BLOCKS) {
            String key = block[0];
            int titleRow = findPlanTitleRow(sheet, block[1]);
            if (titleRow < 0) {
                notFound.add("연간 계획 '" + block[1] + "' 제목");
                continue;
            }
            int headerRow = titleRow + 1;
            int blockLastRow = Math.min(headerRow + 12, MAX_ROW);

            // 월 칸은 "20.6월", "7월", "1월" 처럼 적혀 있고 그 끝이 "계" 다.
            int firstMonthCol = -1;
            int lastMonthCol = -1;
            for (int col = PLAN_LABEL_COL + 1; col < MAX_COL; col += 1) {
                String value = text(sheet, headerRow, col);
                if (value.isBlank()) continue;
                if (MONTH_LABEL.matcher(value).find()) {
                    if (firstMonthCol < 0) firstMonthCol = col;
                    lastMonthCol = col;
                } else if (firstMonthCol >= 0) {
                    break;                                  // "계" 를 만나면 끝
                }
            }
            if (firstMonthCol < 0) {
                notFound.add("연간 계획 '" + block[1] + "' 월 칸");
                continue;
            }

            Map<String, Integer> rows = new LinkedHashMap<>();
            String[] rowKeys = PLAN_ROW_KEYS.get(key);
            int cursor = headerRow + 1;
            for (int i = 0; i < rowKeys.length; i += 1) {
                String label = block[2 + i];
                int row = findPlanRow(sheet, cursor, blockLastRow, label);
                if (row < 0) {
                    notFound.add("연간 계획 '" + block[1] + "' " + label);
                    continue;
                }
                rows.put(rowKeys[i], row);
                cursor = row + 1;
            }
            planBlocks.put(key, new PlanBlock(rows, firstMonthCol, lastMonthCol));
        }
    }

    private static int findPlanTitleRow(Sheet sheet, String title) {
        String key = normalize(title);
        for (int row = 1; row <= MAX_ROW; row += 1) {
            if (normalize(text(sheet, row, PLAN_LABEL_COL)).equals(key)) return row;
        }
        return -1;
    }

    /** 라벨은 구분 열(Q)이나 그 오른쪽 칸(R)에 있다 — "계획 / 기본" 처럼 두 칸을 나눠 쓴다. */
    private static int findPlanRow(Sheet sheet, int firstRow, int lastRow, String label) {
        String key = normalize(label);
        for (int row = firstRow; row <= lastRow; row += 1) {
            if (normalize(text(sheet, row, PLAN_LABEL_COL)).equals(key)) return row;
            if (normalize(text(sheet, row, PLAN_LABEL_COL + 1)).equals(key)) return row;
        }
        return -1;
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    /** 일별 표: 항목 키 → 0-based 열. 못 찾은 항목은 들어 있지 않다. */
    Map<String, Integer> dayColumns() {
        return dayColumns;
    }

    /** 일별 표: 일(1~31) → 1-based 행. */
    Map<Integer, Integer> dayRows() {
        return dayRows;
    }

    Map<String, Integer> fuelPriceRows() {
        return fuelPriceRows;
    }

    /** 연료단가 열(0-based). 못 찾으면 -1. */
    int fuelPriceColumn() {
        return fuelPriceColumn;
    }

    Map<String, Integer> ventRows() {
        return ventRows;
    }

    /** 벤트 손실 표: 일(1~31) → 0-based 열. */
    Map<Integer, Integer> ventDayColumns() {
        return ventDayColumns;
    }

    /** 벤트 손실비용의 단가 열(0-based). 못 찾으면 -1. */
    int ventUnitPriceColumn() {
        return ventUnitPriceColumn;
    }

    /** 스팀 회수량 표의 첫 데이터 줄(1-based). 못 찾으면 -1. */
    int recoveryFirstRow() {
        return recoveryFirstRow;
    }

    Map<String, Integer> recoveryColumns() {
        return recoveryColumns;
    }

    PlanBlock planBlock(String key) {
        return planBlocks.get(key);
    }

    /** 머리글을 못 찾아 건너뛴 항목들. */
    List<String> notFound() {
        return notFound;
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    /** 날짜 칸("1월1일", "2일" …)이 늘어선 줄에서 일 → 0-based 열. */
    private static Map<Integer, Integer> dayColumnsOf(Sheet sheet, int headerRow) {
        Map<Integer, Integer> columns = new LinkedHashMap<>();
        for (int col = 0; col < MAX_COL; col += 1) {
            Matcher matcher = DAY_OF_MONTH.matcher(text(sheet, headerRow, col));
            if (!matcher.find()) continue;
            int day = Integer.parseInt(matcher.group(1));
            if (day >= 1 && day <= 31) columns.putIfAbsent(day, col);
        }
        return columns;
    }

    private static int firstColumnEqualTo(Sheet sheet, int headerRow, String label) {
        Integer col = column(sheet, headerRow, label);
        return col == null ? -1 : col;
    }

    /** {@code labelCol}(0-based)에서 라벨이 정확히 맞는 줄을 찾는다. */
    private static int findRowByLabel(Sheet sheet, int firstRow, int lastRow, int labelCol, String label) {
        String key = normalize(label);
        for (int row = firstRow; row <= lastRow; row += 1) {
            if (normalize(text(sheet, row, labelCol)).equals(key)) return row;
        }
        return -1;
    }

    private static Integer column(Sheet sheet, int headerRow, String label) {
        String key = normalize(label);
        for (int col = 0; col < MAX_COL; col += 1) {
            if (normalize(text(sheet, headerRow, col)).equals(key)) return col;
        }
        return null;
    }

    private static int findColumnContaining(Sheet sheet, int headerRow, String keyword) {
        String key = normalize(keyword);
        for (int col = 0; col < MAX_COL; col += 1) {
            if (normalize(text(sheet, headerRow, col)).contains(key)) return col;
        }
        return -1;
    }

    /** @param rowNumber 1-based 행, @param colIndex 0-based 열 */
    private static String text(Sheet sheet, int rowNumber, int colIndex) {
        if (rowNumber < 1 || colIndex < 0) return "";
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        return SheetLabels.text(cell);   // 수식 칸이면 저장된 계산 결과
    }

    /** 표기 차이를 흡수한다 — {@link HeaderColumnIndex} 와 같은 규칙이다. */
    private static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC);
        text = text.replaceAll("\\([^)]*\\)", "");
        text = text.replaceAll("[\\s\\u00A0]+", "");
        text = text.replaceAll("[#·・\\-_\\[\\]]", "");
        return text.toLowerCase(Locale.ROOT);
    }
}
