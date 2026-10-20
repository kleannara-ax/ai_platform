package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 원단위 일지의 "2. 호기별 가동시간당 스팀" / "4. 유동상·소각로·외부·복합 일일 사용량" 구역 배치 —
 * <b>설비(위 줄) + 항목(아래 줄) 제목으로</b> 열을, <b>일자 칸으로</b> 행을 찾는다.
 *
 * <p>예전에는 43~73행 C·E·G·I·K·N·T·W·Z·AC 열, 120~150행 B~P 열을 코드에 박아 뒀다.
 * 원본에서 열이나 줄을 하나만 지워도 그 뒤가 전부 밀린 채 다른 항목으로 저장됐다.
 *
 * <pre>
 *  2번 구역  41행: PM-2 ·····  PM-3 ·······  PM-3 ················  TM-3  TM-4  TM-5  화장지 총 합계
 *            42행: 총 스팀량 가동시간 원단위 비고 | 스팀량 가동시간 원단위 비고 | 디스퍼져 가동시간 원단위 Ventilation 가동시간 …
 *  4번 구역 117행: 유동상 스팀 구매비용 | 폐합성소각로 … | 복합보일러 … | 외부보일러 …
 *           118행: 유동상 단가 비용 | 스팀생산량 단가 비용 | 스팀생산량 단가 비용 차감비 … | …
 * </pre>
 *
 * <p>2번 구역은 "PM-3" 이 두 묶음(스팀량·디스퍼져)에 같은 이름으로 있고 "가동시간" 이 그 안에 세 번 나온다.
 * 그래서 같은 설비 안에서는 <b>왼쪽부터 순서대로</b>, 직전에 찾은 칸 다음부터 훑는다
 * ({@link HeaderColumnIndex#columnAfter}). 항목표는 반드시 왼쪽→오른쪽 순서로 적는다.
 *
 * <p>못 찾으면 그 칸은 <b>건너뛴다.</b> 행·열은 모두 0-based 다.
 */
final class SteamUnitSectionLayout {

    private static final int MAX_ROW = 260;
    private static final int MAX_COL = 60;

    /** 항목 — 저장 표, 저장 col_index, 설비(위 줄), 항목(아래 줄). */
    private record Field(String table, int colIndex, String group, String item) {}

    /** 2번 구역 — 왼쪽→오른쪽 순서. */
    private static final List<Field> USAGE_FIELDS = List.of(
            new Field("usage_m_pm2", 1, "PM-2", "가동시간(hr)"),
            new Field("usage_m_pm2", 3, "PM-2", "비 고"),
            new Field("usage_m_pm3a", 1, "PM-3", "가동시간(hr)"),      // 스팀량(M+C) 묶음
            new Field("usage_m_pm3a", 3, "PM-3", "비 고"),
            new Field("usage_m_pm3b", 1, "PM-3", "가동시간(hr)"),      // 디스퍼져 묶음 — 같은 이름이라 순서로 가른다
            new Field("usage_m_pm3b", 5, "PM-3", "가동시간"),          // Ventilation 가동시간
            new Field("usage_m_tm", 1, "TM-3", "가동시간(hr)"),
            new Field("usage_m_tm", 4, "TM-4", "가동시간(hr)"),
            new Field("usage_m_tm", 7, "TM-5", "가동시간(hr)"),
            new Field("usage_m_tissue", 1, "화장지 총 합계", "가동시간(hr)")
    );

    /** 4번 구역 — 왼쪽→오른쪽 순서. */
    private static final List<Field> FLOW_FIELDS = List.of(
            new Field("flow_m_fluid_incinerator", 0, "유동상 스팀 구매비용", "유동상"),
            new Field("flow_m_fluid_incinerator", 1, "유동상 스팀 구매비용", "단가"),
            new Field("flow_m_fluid_incinerator", 3, "폐합성소각로 스팀 구매비용", "스팀생산량"),
            new Field("flow_m_fluid_incinerator", 4, "폐합성소각로 스팀 구매비용", "단가"),
            new Field("flow_m_combined", 0, "복합보일러 스팀 구매비용", "스팀생산량"),
            new Field("flow_m_combined", 1, "복합보일러 스팀 구매비용", "단가"),
            new Field("flow_m_combined", 3, "복합보일러 스팀 구매비용", "차감비"),
            new Field("flow_m_boiler", 0, "외부보일러 스팀 구매비용", "스팀생산량"),
            new Field("flow_m_boiler", 1, "외부보일러 스팀 구매비용", "단가"),
            new Field("flow_m_boiler", 3, "외부보일러 스팀 구매비용", "차감비")
    );

    private final Map<String, Map<Integer, Integer>> columnsByTable = new LinkedHashMap<>();
    private final Map<Integer, Integer> dayRows = new LinkedHashMap<>();
    private final List<String> notFound = new ArrayList<>();

    private SteamUnitSectionLayout() {
    }

    /**
     * 1번 구역(호기별 생산량당 스팀사용 원단위)의 머리글 색인.
     *
     * <p>예전에는 머리글 줄을 3·4·5행, 일자를 6~36행으로 박아 뒀다. 이제 A열의 "일자" 칸을 찾아
     * 그 줄을 큰 구분, 다음 줄을 설비(PM-2 …), 그다음 줄을 항목(Main 스팀량 …)으로 보고,
     * 일자는 "누계" 줄 앞까지로 잡는다. 못 찾으면 null.
     */
    static HeaderColumnIndex unitHeaderIndex(Sheet sheet) {
        int topRow = -1;
        for (int row = 0; row <= MAX_ROW && topRow < 0; row += 1) {
            if (normalize(text(sheet, row, 0)).equals(normalize("일자"))) topRow = row;
        }
        if (topRow < 0) return null;
        int itemRow = topRow + 2;
        int lastRow = itemRow + 1;
        for (int row = itemRow + 1; row <= Math.min(itemRow + 40, MAX_ROW); row += 1) {
            if (normalize(text(sheet, row, 0)).equals(normalize("누계"))) break;
            lastRow = row;
        }
        return HeaderColumnIndex.build(sheet, topRow, topRow + 1, itemRow, itemRow + 1, lastRow, 0);
    }

    /**
     * A열에서 글자 규칙에 맞는 줄을 찾는다. {@code unitInC} 이면 같은 줄 C열이 "단위" 인 줄만 본다
     * (월 누계·월 요약 표의 머리글 줄). 못 찾으면 -1.
     */
    static int findLabelRow(Sheet sheet, java.util.regex.Pattern pattern, boolean unitInC) {
        for (int row = 0; row <= MAX_ROW; row += 1) {
            if (!pattern.matcher(text(sheet, row, 0)).matches()) continue;
            if (unitInC && !normalize(text(sheet, row, 2)).equals(normalize("단위"))) continue;
            return row;
        }
        return -1;
    }

    /** 2번 구역 — 항목 줄의 "총 스팀량(톤)" 을 기준으로 잡고, 그 윗줄이 설비 줄이다. */
    static SteamUnitSectionLayout usage(Sheet sheet) {
        SteamUnitSectionLayout layout = new SteamUnitSectionLayout();
        int itemRow = findRow(sheet, "총 스팀량(톤)");
        if (itemRow < 1) {
            layout.notFound.add("2번 구역 제목 줄('총 스팀량(톤)')");
            return layout;
        }
        layout.read(sheet, itemRow - 1, itemRow, USAGE_FIELDS);
        return layout;
    }

    /** 4번 구역 — 설비 줄의 "유동상 스팀 구매비용" 을 기준으로 잡고, 그 아랫줄이 항목 줄이다. */
    static SteamUnitSectionLayout flow(Sheet sheet) {
        SteamUnitSectionLayout layout = new SteamUnitSectionLayout();
        int groupRow = findRow(sheet, "유동상 스팀 구매비용");
        if (groupRow < 0) {
            layout.notFound.add("4번 구역 제목 줄('유동상 스팀 구매비용')");
            return layout;
        }
        layout.read(sheet, groupRow, groupRow + 1, FLOW_FIELDS);
        return layout;
    }

    private void read(Sheet sheet, int groupRow, int itemRow, List<Field> fields) {
        // 일자: 항목 줄 아래 A열의 1~31 — "누계" 를 만나면 끝(아래 구역의 일자를 집지 않게)
        int lastRow = itemRow + 1;
        for (int row = itemRow + 1; row <= Math.min(itemRow + 40, MAX_ROW); row += 1) {
            if (normalize(text(sheet, row, 0)).equals(normalize("누계"))) break;
            Integer day = dayOf(sheet, row);
            if (day != null) dayRows.putIfAbsent(day, row);
            lastRow = row;
        }
        if (dayRows.isEmpty()) notFound.add("일자 칸(" + (itemRow + 2) + "행 아래)");

        HeaderColumnIndex index = HeaderColumnIndex.build(sheet, -1, groupRow, itemRow, itemRow + 1, lastRow, 0);
        Map<String, Integer> cursorByGroup = new LinkedHashMap<>();
        for (Field field : fields) {
            int cursor = cursorByGroup.getOrDefault(field.group(), 0);
            Integer col = index.columnAfter(cursor, field.group(), field.item());
            if (col == null) {
                notFound.add(field.table() + " '" + field.group() + " / " + field.item() + "'");
                continue;
            }
            columnsByTable.computeIfAbsent(field.table(), key -> new LinkedHashMap<>()).put(field.colIndex(), col);
            cursorByGroup.put(field.group(), col + 1);
        }
    }

    /** 표 이름 → {저장 col_index → 시트 열}. 제목을 못 찾은 칸은 들어 있지 않다. */
    Map<Integer, Integer> columns(String tableName) {
        return columnsByTable.getOrDefault(tableName, Map.of());
    }

    /** 이 구역이 쓰는 표 이름 — 항목표에 적힌 순서. */
    List<String> tables() {
        return new ArrayList<>(columnsByTable.keySet());
    }

    /** 일(1~31) → 시트 행. */
    Map<Integer, Integer> dayRows() {
        return dayRows;
    }

    /** 제목을 못 찾아 건너뛴 칸. */
    List<String> notFound() {
        return notFound;
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    private static int findRow(Sheet sheet, String label) {
        String key = normalize(label);
        for (int row = 0; row <= MAX_ROW; row += 1) {
            for (int col = 0; col < MAX_COL; col += 1) {
                if (normalize(text(sheet, row, col)).equals(key)) return row;
            }
        }
        return -1;
    }

    /** A열 일자 — 가져오기의 readDay 와 같게 숫자 칸만 본다(수식 칸은 건너뛴다). */
    private static Integer dayOf(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(0);
        if (cell == null || cell.getCellType() != CellType.NUMERIC) return null;
        double value = cell.getNumericCellValue();
        int day = (int) value;
        return (day >= 1 && day <= 31 && day == value) ? day : null;
    }

    private static String text(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        return SheetLabels.text(cell);   // 수식 칸이면 저장된 계산 결과
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[\\s\\u00A0]+", "").toLowerCase(Locale.ROOT);
    }
}
