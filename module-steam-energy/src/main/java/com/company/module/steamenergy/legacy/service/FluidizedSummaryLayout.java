package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 유동상 운영내역 워크북의 배치 — <b>시트 이름과 머리글 글자로</b> 칸을 찾는다.
 *
 * <p>예전에는 시트를 {@code getSheetAt(0), getSheetAt(4), getSheetAt(7)} 처럼 <b>순번</b>으로,
 * 칸을 {@code 5~8행=사업계획, 7행=1일, C열=스팀 생산량} 처럼 <b>좌표</b>로 잡았다.
 * 그래서 원본에서 시트를 하나 끼워 넣거나 행·열을 하나 지우면 그 뒤가 전부 밀린 채
 * <b>다른 항목으로 저장</b>됐다.
 *
 * <p>모든 행·열은 POI 와 같은 <b>0-based</b> 다({@code getCell(sheet, row, col)} 과 짝이 맞는다).
 *
 * <p>못 찾으면 그 항목은 <b>건너뛴다.</b> 좌표로 폴백하면 값이 엉뚱한 줄에 붙기 때문이다.
 */
final class FluidizedSummaryLayout {

    private static final Pattern MONTH = Pattern.compile("(\\d{1,2})\\s*월");
    private static final Pattern DAY = Pattern.compile("(\\d{1,2})\\s*일");

    private static final int MAX_ROW = 120;
    private static final int MAX_COL = 48;

    /** 월 머리글로 인정할 최소 개수 — "`25년 10월" 같은 한두 칸짜리 줄을 거르려고 둔다. */
    private static final int MIN_MONTH_HEADERS = 6;

    private FluidizedSummaryLayout() {
    }

    // ── 시트 찾기 ────────────────────────────────────────────────────────────

    /**
     * 시트를 <b>이름 앞머리</b>로 찾는다. 원본 시트 이름이 "1. 운영실적", "4. SRF입고내역" 처럼
     * 번호로 시작하므로 그 번호를 쓴다. 못 찾으면 예전 순번으로 돌아간다 —
     * 시트 이름을 바꾼 파일도 지금까지처럼은 읽히게 하기 위해서다.
     *
     * @param prefixes      시트 이름 앞머리 후보 (예: {@code "4."}, {@code "SRF"})
     * @param fallbackIndex 못 찾았을 때 쓸 예전 순번
     */
    static Sheet sheetByName(Workbook workbook, int fallbackIndex, String... prefixes) {
        for (String prefix : prefixes) {
            String key = normalize(prefix);
            for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
                Sheet sheet = workbook.getSheetAt(i);
                if (normalize(sheet.getSheetName()).startsWith(key)) return sheet;
            }
        }
        return fallbackIndex < workbook.getNumberOfSheets() ? workbook.getSheetAt(fallbackIndex) : null;
    }

    // ── "1. 운영실적" 시트 ───────────────────────────────────────────────────

    /**
     * 연간 사업계획 표와 월별 특이사항.
     *
     * @param planRows  항목 키 → 행 (운영비·전력비·폐기물처리비·SRF수입금)
     * @param planCols  월(1~12) → "계획" 칸의 열
     * @param noteRows  월(1~12) → 특이사항 줄
     * @param summaryRow 이달 총평 줄. 없으면 -1
     * @param summaryCol 이달 총평의 "비 고" 열. 없으면 -1
     */
    record Annual(Map<String, Integer> planRows, Map<Integer, Integer> planCols,
                  Map<Integer, Integer> noteRows, int summaryRow, int summaryCol) {
    }

    /** 사업계획 표의 항목 — {@code {키, 행 라벨}}. 순서가 화면 순서다. */
    private static final String[][] PLAN_ROWS = {
            {"operating", "운영비"},
            {"power", "전력비"},
            {"waste", "폐기물처리비"},
            {"srf_income", "SRF수입금"}
    };

    static Annual annualOf(Sheet sheet) {
        Map<String, Integer> planRows = new LinkedHashMap<>();
        Map<Integer, Integer> planCols = new LinkedHashMap<>();
        Map<Integer, Integer> noteRows = new LinkedHashMap<>();
        if (sheet == null) return new Annual(planRows, planCols, noteRows, -1, -1);

        int headerRow = findMonthHeaderRow(sheet, 0);
        if (headerRow >= 0) {
            Map<Integer, Integer> monthCols = monthColumnsOf(sheet, headerRow);
            for (Map.Entry<Integer, Integer> month : monthCols.entrySet()) {
                // 한 달은 "계획 / 실적 / 증감" 세 칸이다 — 그 중 "계획" 칸을 쓴다.
                Integer planCol = findSubColumn(sheet, headerRow + 1, month.getValue(),
                        nextColumn(monthCols, month.getValue()), "계획");
                if (planCol != null) planCols.put(month.getKey(), planCol);
            }
            for (String[] field : PLAN_ROWS) {
                int row = findRowByLabel(sheet, headerRow + 1, MAX_ROW, 0, field[1]);
                if (row >= 0) planRows.put(field[0], row);
            }
        }

        // 월별 특이사항: "유동상 소각로 정비" 블록 안에서 B열의 "N월" 로 줄을 찾는다.
        int noteBlockRow = -1;
        for (int row = 0; row <= MAX_ROW && noteBlockRow < 0; row += 1) {
            if (normalize(text(sheet, row, 0)).contains("정비")) noteBlockRow = row;
        }
        if (noteBlockRow >= 0) {
            for (int row = noteBlockRow; row <= Math.min(noteBlockRow + 24, MAX_ROW); row += 1) {
                Integer month = monthOf(text(sheet, row, 1));
                if (month != null) noteRows.putIfAbsent(month, row);
            }
        }

        int summaryRow = findRowByLabel(sheet, 0, MAX_ROW, 0, "위탁운영비");
        int summaryCol = -1;
        if (summaryRow >= 0) {
            // "비 고" 머리글은 총평 줄 바로 위 표 머리글에 있다.
            for (int row = summaryRow; row >= 0 && summaryCol < 0; row -= 1) {
                Integer col = columnInRow(sheet, row, "비고");
                if (col != null) summaryCol = col;
            }
        }
        return new Annual(planRows, planCols, noteRows, summaryRow, summaryCol);
    }

    // ── "2. 운영실적" 시트 ───────────────────────────────────────────────────

    /**
     * 일별 도급비용 표와 지급현황 표.
     *
     * @param dayRows     일(1~31) → 줄
     * @param columns     항목 키 → 열
     * @param costRow     지급현황 데이터 줄. 없으면 -1
     * @param costColumns 지급현황 항목 키 → 열
     */
    record Contract(Map<Integer, Integer> dayRows, Map<String, Integer> columns,
                    int costRow, Map<String, Integer> costColumns) {
    }

    /** 일별 표의 항목 — {@code {키, 머리글}}. 키는 {@code table_cell_value.col_index} 와 짝이 맞는다. */
    private static final String[][] CONTRACT_COLUMNS = {
            {"steam", "스팀 생산량"},
            {"price", "스팀단가"},
            {"stopHours", "운휴시간"},
            {"fixedCost", "운휴시간당 고정비"}
    };

    private static final String[][] COST_COLUMNS = {
            {"labor", "인건비"},
            {"operating", "운영비"},
            {"improvement", "설비개선비용"}
    };

    static Contract contractOf(Sheet sheet) {
        Map<Integer, Integer> dayRows = new LinkedHashMap<>();
        Map<String, Integer> columns = new LinkedHashMap<>();
        Map<String, Integer> costColumns = new LinkedHashMap<>();
        if (sheet == null) return new Contract(dayRows, columns, -1, costColumns);

        // 항목 머리글 줄 — "스팀 생산량" 과 "스팀단가" 가 같이 있는 줄이다.
        int headerRow = -1;
        for (int row = 0; row <= MAX_ROW && headerRow < 0; row += 1) {
            if (columnInRow(sheet, row, "스팀 생산량") != null && columnInRow(sheet, row, "스팀단가") != null) {
                headerRow = row;
            }
        }
        if (headerRow >= 0) {
            for (String[] field : CONTRACT_COLUMNS) {
                Integer col = columnInRow(sheet, headerRow, field[1]);
                if (col != null) columns.put(field[0], col);
            }
            // 일자 칸("1일" … "31일")은 항목 머리글 왼쪽 열에 있다.
            int dayCol = findDayColumn(sheet, headerRow);
            if (dayCol >= 0) {
                for (int row = headerRow + 1; row <= MAX_ROW; row += 1) {
                    Matcher matcher = DAY.matcher(text(sheet, row, dayCol));
                    if (!matcher.matches()) continue;
                    int day = Integer.parseInt(matcher.group(1));
                    if (day >= 1 && day <= 31) dayRows.putIfAbsent(day, row);
                }
            }
        }

        // 지급현황: "설비명" 머리글 줄 다음이 데이터 줄이다.
        int costHeaderRow = -1;
        Integer nameCol = null;
        for (int row = 0; row <= MAX_ROW && costHeaderRow < 0; row += 1) {
            Integer col = columnInRow(sheet, row, "설비명");
            if (col == null) continue;
            costHeaderRow = row;
            nameCol = col;
        }
        int costRow = -1;
        if (costHeaderRow >= 0) {
            for (String[] field : COST_COLUMNS) {
                Integer col = columnInRow(sheet, costHeaderRow, field[1]);
                if (col != null) costColumns.put(field[0], col);
            }
            for (int row = costHeaderRow + 1; row <= Math.min(costHeaderRow + 5, MAX_ROW) && costRow < 0; row += 1) {
                if (!text(sheet, row, nameCol).isBlank()) costRow = row;
            }
        }
        return new Contract(dayRows, columns, costRow, costColumns);
    }

    // ── "4. SRF입고내역" 시트 ────────────────────────────────────────────────

    /**
     * SRF 입고내역 표.
     *
     * @param dayRows    일(1~31) → 줄
     * @param vendorCols 업체 열 — 왼쪽부터 순서대로. "합 계" 앞까지다
     * @param rateRow    "처리단가" 줄. 없으면 -1
     */
    record SrfInbound(Map<Integer, Integer> dayRows, List<Integer> vendorCols, int rateRow) {
    }

    static SrfInbound srfInboundOf(Sheet sheet) {
        Map<Integer, Integer> dayRows = new LinkedHashMap<>();
        List<Integer> vendorCols = new ArrayList<>();
        if (sheet == null) return new SrfInbound(dayRows, vendorCols, -1);

        // 머리글 줄 — "구 분" 칸이 있고 그 오른쪽에 업체 이름이 늘어선 줄이다.
        int headerRow = -1;
        int labelCol = -1;
        for (int row = 0; row <= MAX_ROW && headerRow < 0; row += 1) {
            Integer col = columnInRow(sheet, row, "구분");
            if (col == null) continue;
            headerRow = row;
            labelCol = col;
        }
        if (headerRow < 0) return new SrfInbound(dayRows, vendorCols, -1);

        for (int col = labelCol + 1; col < MAX_COL; col += 1) {
            String value = normalize(text(sheet, headerRow, col));
            if (value.isEmpty()) continue;
            if (value.equals("합계") || value.equals("누계")) break;   // 업체 목록 끝
            vendorCols.add(col);
        }

        // 날짜 칸은 엑셀 날짜값이다 — 서식이 어떻든 '일' 을 그대로 읽는다.
        for (int row = headerRow + 1; row <= MAX_ROW; row += 1) {
            Integer day = dayOfDateCell(sheet, row, labelCol);
            if (day != null) dayRows.putIfAbsent(day, row);
        }
        int rateRow = findRowByLabel(sheet, headerRow + 1, MAX_ROW, labelCol, "처리단가");
        return new SrfInbound(dayRows, vendorCols, rateRow);
    }

    // ── "5. …설비개선리스트" / "6. …사고이력" 시트 ──────────────────────────

    /**
     * 머리글 한 줄 + 그 아래 목록으로 된 표.
     *
     * @param firstRow 첫 데이터 줄. 표를 못 찾으면 -1
     * @param lastRow  마지막 데이터 줄(합계 줄 바로 위)
     * @param columns  항목 키 → 열
     */
    record LabeledTable(int firstRow, int lastRow, Map<String, Integer> columns) {
        boolean usable() {
            return firstRow >= 0 && lastRow >= firstRow && !columns.isEmpty();
        }
    }

    /** 설비개선 왼쪽 표(월별 집행) — {@code {키, 머리글}}. */
    private static final String[][] IMPROVEMENT_MONTHLY_COLUMNS = {
            {"month", "월"},
            {"payment", "지급액"},
            {"spent", "사용액"},
            {"vendor", "업체명"},
            {"usage", "사용처"}
    };

    /** 설비개선 오른쪽 표(개선 항목) — 비고 칸은 머리글이 없어 "완료여부" 오른쪽으로 잡는다. */
    private static final String[][] IMPROVEMENT_ITEM_COLUMNS = {
            {"category", "구 분"},
            {"no", "No."},
            {"item", "Item"},
            {"planCost", "계획비용"},
            {"actualCost", "실 비용"},
            {"status", "완료여부"}
    };

    /** 사고·정비 이력 표 — {@code {키, 머리글}}. 정비 표에는 "손실금액" 칸이 없다. */
    private static final String[][] ACCIDENT_COLUMNS = {
            {"date", "일 자"},
            {"reason", "사 유"},
            {"action", "조치내역"},
            {"downtime", "운휴시간"},
            {"loss", "손실금액"},
            {"note", "비 고"}
    };

    /** 합계·집계 줄로 보는 라벨 — 여기서 목록이 끝난다. */
    private static final String[] SUMMARY_LABELS = {
            "총액", "합계", "총계", "발생횟수", "정비횟수", "추진건수", "추가건수"
    };

    /**
     * 개선 항목 표가 차지하는 줄 수 — 머리글 다음 줄부터 이만큼이다.
     *
     * <p>이 표는 중간에 "추진건수 / 완료건수" 집계 줄이 끼어 있어서 합계 라벨만으로는 끝을
     * 알 수 없다(끼어든 줄에서 멈춰 버린다). 그래서 길이만 고정하고, 끼어든 집계 줄은
     * 호출부가 {@code isImprovementSummaryRow} 로 건너뛴다. 머리글 기준이라 표 <b>위에</b>
     * 줄이 생겨도 따라 움직인다.
     */
    private static final int IMPROVEMENT_ITEM_ROW_SPAN = 21;

    /** 설비개선 왼쪽(월별 집행) 표. */
    static LabeledTable improvementMonthlyOf(Sheet sheet) {
        return labeledTable(sheet, IMPROVEMENT_MONTHLY_COLUMNS, "월", "지급액", 0);
    }

    /** 설비개선 오른쪽(개선 항목) 표. */
    static LabeledTable improvementItemOf(Sheet sheet) {
        LabeledTable table = labeledTable(sheet, IMPROVEMENT_ITEM_COLUMNS, "구 분", "Item",
                IMPROVEMENT_ITEM_ROW_SPAN);
        Integer status = table.columns().get("status");
        if (status != null) table.columns().put("note", status + 1);   // 비고 칸은 머리글이 없다
        return table;
    }

    /** 사고 이력·정기 정비 이력 두 표. 위에서 아래 순서다. */
    static List<LabeledTable> accidentBlocksOf(Sheet sheet) {
        List<LabeledTable> blocks = new ArrayList<>();
        if (sheet == null) return blocks;
        for (int row = 0; row <= MAX_ROW; row += 1) {
            if (columnInRow(sheet, row, "일 자") == null) continue;
            if (columnInRow(sheet, row, "조치내역") == null) continue;
            blocks.add(tableAt(sheet, row, ACCIDENT_COLUMNS, 0));
        }
        return blocks;
    }

    /**
     * {@code anchorA}·{@code anchorB} 가 함께 있는 줄을 머리글로 보고 표를 읽는다.
     *
     * @param rowSpan 0 이면 합계 줄을 만날 때까지, 0 보다 크면 그만큼을 목록으로 본다
     */
    private static LabeledTable labeledTable(Sheet sheet, String[][] fields,
                                             String anchorA, String anchorB, int rowSpan) {
        if (sheet != null) {
            for (int row = 0; row <= MAX_ROW; row += 1) {
                if (columnInRow(sheet, row, anchorA) == null) continue;
                if (columnInRow(sheet, row, anchorB) == null) continue;
                return tableAt(sheet, row, fields, rowSpan);
            }
        }
        return new LabeledTable(-1, -1, new LinkedHashMap<>());
    }

    private static LabeledTable tableAt(Sheet sheet, int headerRow, String[][] fields, int rowSpan) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (String[] field : fields) {
            Integer col = columnInRow(sheet, headerRow, field[1]);
            if (col != null) columns.put(field[0], col);
        }
        // 목록은 합계 줄 직전까지다. 합계 라벨은 표의 첫 칸 왼쪽("No." 칸)에 적히기도 해서
        // 이 표가 쓰는 열 범위를 한 칸 넓혀 훑는다. 한 줄에 표가 좌우로 둘 놓인 설비개선 시트에서
        // 옆 표의 합계 줄에 끌려가지 않으려면 범위를 이 표로 좁혀야 한다.
        int firstCol = columns.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int lastCol = columns.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int scanFrom = Math.max(firstCol - 1, 0);

        if (rowSpan > 0) {
            return new LabeledTable(headerRow + 1, Math.min(headerRow + rowSpan, MAX_ROW), columns);
        }
        int lastRow = Math.min(headerRow + 40, MAX_ROW);
        for (int row = headerRow + 1; row <= lastRow; row += 1) {
            if (hasSummaryLabel(sheet, row, scanFrom, lastCol)) {
                lastRow = row - 1;
                break;
            }
        }
        return new LabeledTable(headerRow + 1, lastRow, columns);
    }

    private static boolean hasSummaryLabel(Sheet sheet, int row, int firstCol, int lastCol) {
        for (int col = firstCol; col <= lastCol; col += 1) {
            String key = normalize(text(sheet, row, col));
            if (key.isEmpty()) continue;
            for (String label : SUMMARY_LABELS) {
                if (key.equals(normalize(label))) return true;
            }
        }
        return false;
    }

    // ── 내려받은 파일을 되읽을 때(실적비교·도급비용) ─────────────────────────

    /** 월 머리글이 늘어선 줄을 찾는다. {@code fromRow} 부터 아래로 훑는다. */
    static int findMonthHeaderRow(Sheet sheet, int fromRow) {
        for (int row = fromRow; row <= MAX_ROW; row += 1) {
            if (monthColumnsOf(sheet, row).size() >= MIN_MONTH_HEADERS) return row;
        }
        return -1;
    }

    /** 한 줄에서 "1월" … "12월" 칸을 읽어 월 → 열. */
    static Map<Integer, Integer> monthColumnsOf(Sheet sheet, int row) {
        Map<Integer, Integer> columns = new LinkedHashMap<>();
        for (int col = 0; col < MAX_COL; col += 1) {
            Integer month = monthOf(text(sheet, row, col));
            if (month != null) columns.putIfAbsent(month, col);
        }
        return columns;
    }

    /** 일자 칸("1일" … "31일")이 있는 열. {@code headerRow} 아래를 본다. 없으면 -1. */
    static int findDayColumn(Sheet sheet, int headerRow) {
        for (int col = 0; col < MAX_COL; col += 1) {
            for (int row = headerRow + 1; row <= Math.min(headerRow + 6, MAX_ROW); row += 1) {
                if (DAY.matcher(text(sheet, row, col)).matches()) return col;
            }
        }
        return -1;
    }

    /** 일자 칸으로 일 → 줄. */
    static Map<Integer, Integer> dayRowsOf(Sheet sheet, int dayCol, int fromRow) {
        Map<Integer, Integer> rows = new LinkedHashMap<>();
        for (int row = fromRow; row <= MAX_ROW; row += 1) {
            Matcher matcher = DAY.matcher(text(sheet, row, dayCol));
            if (!matcher.matches()) continue;
            int day = Integer.parseInt(matcher.group(1));
            if (day >= 1 && day <= 31) rows.putIfAbsent(day, row);
        }
        return rows;
    }

    /**
     * 머리글 아래에서 "1월" … "12월" 이 적힌 줄을 찾는다 — 월 → 줄.
     *
     * <p>내려받은 실적비교 파일의 월별 특이사항처럼, 한 달이 한 줄인 표에 쓴다.
     */
    static Map<Integer, Integer> monthRowsBelow(Sheet sheet, int headerRow, int monthCol) {
        Map<Integer, Integer> rows = new LinkedHashMap<>();
        for (int row = headerRow + 1; row <= Math.min(headerRow + 24, MAX_ROW); row += 1) {
            Integer month = monthOf(text(sheet, row, monthCol));
            if (month != null) rows.putIfAbsent(month, row);
        }
        return rows;
    }

    /** 한 줄에서 라벨이 정확히 맞는 칸의 열. 없으면 null. */
    static Integer columnInRow(Sheet sheet, int row, String label) {
        String key = normalize(label);
        for (int col = 0; col < MAX_COL; col += 1) {
            if (normalize(text(sheet, row, col)).equals(key)) return col;
        }
        return null;
    }

    /** 한 열에서 라벨이 정확히 맞는 줄. 없으면 -1. */
    static int findRowByLabel(Sheet sheet, int firstRow, int lastRow, int labelCol, String label) {
        String key = normalize(label);
        for (int row = firstRow; row <= lastRow; row += 1) {
            if (normalize(text(sheet, row, labelCol)).equals(key)) return row;
        }
        return -1;
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    /** {@code from} 이상 {@code until} 미만에서 라벨이 맞는 칸을 찾는다. */
    private static Integer findSubColumn(Sheet sheet, int row, int from, int until, String label) {
        String key = normalize(label);
        for (int col = from; col < Math.min(until, MAX_COL); col += 1) {
            if (normalize(text(sheet, row, col)).equals(key)) return col;
        }
        return null;
    }

    /** 다음 월 칸의 열. 마지막 달이면 표 끝까지 본다. */
    private static int nextColumn(Map<Integer, Integer> monthCols, int current) {
        int next = MAX_COL;
        for (int col : monthCols.values()) {
            if (col > current) next = Math.min(next, col);
        }
        return next;
    }

    private static Integer monthOf(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = MONTH.matcher(value);
        if (!matcher.find()) return null;
        int month = Integer.parseInt(matcher.group(1));
        return (month >= 1 && month <= 12) ? month : null;
    }

    /** 엑셀 날짜 일련번호로 볼 범위 — 2009년~2064년. 이 표의 날짜 칸은 이 안에 든다. */
    private static final double DATE_SERIAL_MIN = 40000;
    private static final double DATE_SERIAL_MAX = 60000;

    /**
     * 날짜 칸에서 '일' 을 읽는다. 날짜가 아니면 null.
     *
     * <p>원본의 날짜 칸은 {@code m"월" d"일"} 같은 사용자 서식을 쓰는데 POI 가 이 서식을
     * 날짜로 알아보지 못해 {@code 45931.0} 처럼 <b>일련번호</b>로 읽힌다. 그래서 서식이
     * 날짜가 아니어도 값이 날짜 범위에 들면 일련번호로 보고 날짜로 바꾼다.
     *
     * <p>내려받은 파일처럼 "1일" 이라고 <b>글자</b>로 적힌 칸도 함께 받는다.
     */
    private static Integer dayOfDateCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell)
                    || (value >= DATE_SERIAL_MIN && value <= DATE_SERIAL_MAX)) {
                return DateUtil.getLocalDateTime(value).getDayOfMonth();
            }
            return null;
        }
        Matcher matcher = DAY.matcher(text(sheet, rowIndex, colIndex));
        if (!matcher.matches()) return null;
        int day = Integer.parseInt(matcher.group(1));
        return (day >= 1 && day <= 31) ? day : null;
    }

    private static String text(Sheet sheet, int rowIndex, int colIndex) {
        if (sheet == null || rowIndex < 0 || colIndex < 0) return "";
        Row row = sheet.getRow(rowIndex);
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
