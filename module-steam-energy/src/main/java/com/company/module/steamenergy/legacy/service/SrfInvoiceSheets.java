package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SRF 보일러 인보이스 통합본(KleanNara SRF Boiler AOT invoice) 의 시트·줄·칸을 <b>글자로</b> 찾는다.
 *
 * <p>월마다 받는 통합본은 배치가 조금씩 다르다 — 2026년 양식은 Summary 에 "4. OTHERS (O&amp;M of NOx Reduction Facility)"
 * 묶음이 끼어 아래 줄이 모두 8줄 밀렸고, 운영내역에는 De-NOx · 액체산소 열과 둘째 줄 머리글이 생겼고,
 * Client Invoice · 월별 운영내역에도 줄과 표가 늘었다. 그래서 좌표를 박지 않고 제목 글자로 자리를 찾는다.
 * 가져오기와 내보내기가 같은 찾기를 쓴다. 행·열은 모두 0-based.
 */
final class SrfInvoiceSheets {

    /** 규격 표의 숫자 칸은 모두 DECIMAL(18,6) */
    static final int SCALE = 6;
    static final int MAX_ROW = 400;
    static final int MAX_COL = 70;

    private static final Pattern DAY_LABEL = Pattern.compile("(\\d{1,2})\\s*일");
    private static final Pattern FISCAL_BLOCK = Pattern.compile("^(\\d{4})년도$");
    private static final Pattern INCIDENT_SHEET = Pattern.compile("^'?(\\d{2,4})년.*사고이력.*$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\).*");
    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    private SrfInvoiceSheets() {
    }

    // ───────────────────────── 공용 ─────────────────────────

    static String key(String value) {
        return SheetLabels.key(value);
    }

    static String text(Sheet sheet, int row, int col) {
        return SheetLabels.text(sheet, row, col);
    }

    static String keyAt(Sheet sheet, int row, int col) {
        return key(text(sheet, row, col));
    }

    /** 이름(공백 무시, 소문자)이 조건에 맞는 첫 시트. 없으면 null. */
    static Sheet sheet(Workbook workbook, Predicate<String> nameMatches) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
            Sheet sheet = workbook.getSheetAt(i);
            if (nameMatches.test(key(sheet.getSheetName()))) return sheet;
        }
        return null;
    }

    static Sheet summarySheet(Workbook workbook) {
        return sheet(workbook, name -> name.startsWith("summary"));
    }

    static Sheet clientSheet(Workbook workbook) {
        return sheet(workbook, name -> name.startsWith("clientinvoice"));
    }

    /** "운영내역" — "월별 운영내역" 과 헷갈리지 않게 이름이 정확히 맞는 것만. */
    static Sheet operationSheet(Workbook workbook) {
        return sheet(workbook, name -> name.equals("운영내역"));
    }

    static Sheet monthlySheet(Workbook workbook) {
        return sheet(workbook, name -> name.startsWith("월별운영내역"));
    }

    static Sheet bscSheet(Workbook workbook) {
        return sheet(workbook, name -> name.equals("bsc"));
    }

    static Sheet adjustmentSheet(Workbook workbook) {
        return sheet(workbook, name -> name.startsWith("adjustmentfactor"));
    }

    static Sheet accumulatedSheet(Workbook workbook) {
        return sheet(workbook, name -> name.startsWith("accumulatedsteam"));
    }

    static Sheet incidentSheet(Workbook workbook) {
        return sheet(workbook, name -> INCIDENT_SHEET.matcher(name).matches());
    }

    static Cell cell(Sheet sheet, int row, int col) {
        if (sheet == null || row < 0 || col < 0) return null;
        Row r = sheet.getRow(row);
        return r == null ? null : r.getCell(col);
    }

    static Cell cellOrCreate(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col);
        return c == null ? r.createCell(col) : c;
    }

    static boolean isFormula(Cell cell) {
        return cell != null && cell.getCellType() == CellType.FORMULA;
    }

    private static CellType valueType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    /** DECIMAL(18,6) 에 들어갈 모양으로 맞춘다 (엑셀의 21150.00000000009 → 21150). */
    static BigDecimal normalize(BigDecimal value) {
        if (value == null) return null;
        BigDecimal scaled = value.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.scale() < 0 ? scaled.setScale(0) : scaled;
    }

    /** 칸의 숫자 — 수식 칸은 저장된 계산 결과. 빈칸·오류·글자는 null. */
    static BigDecimal number(Cell cell) {
        if (cell == null) return null;
        CellType type = valueType(cell);
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return Double.isFinite(value) ? normalize(BigDecimal.valueOf(value)) : null;
        }
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().replace(",", "").trim();
            if (text.isEmpty()) return null;
            try {
                return normalize(new BigDecimal(text));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static BigDecimal number(Sheet sheet, int row, int col) {
        return number(cell(sheet, row, col));
    }

    /** 칸의 글자 — 날짜 칸은 yyyy-MM-dd, 숫자 칸은 꾸밈 없는 숫자. 빈칸은 null. */
    static String string(Cell cell) {
        if (cell == null) return null;
        CellType type = valueType(cell);
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            return text.isEmpty() ? null : text;
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            // 글자로 저장하는 칸(사고이력의 운휴시간 · 손실금액)이라 소수 자리를 자르지 않는다
            double value = cell.getNumericCellValue();
            if (!Double.isFinite(value)) return null;
            BigDecimal plain = BigDecimal.valueOf(value).stripTrailingZeros();
            return (plain.scale() < 0 ? plain.setScale(0) : plain).toPlainString();
        }
        return null;
    }

    static String string(Sheet sheet, int row, int col) {
        return string(cell(sheet, row, col));
    }

    /** 날짜 서식의 숫자 칸이면 그 날짜, 아니면 null. */
    static LocalDate date(Cell cell) {
        if (cell == null || valueType(cell) != CellType.NUMERIC || !DateUtil.isCellDateFormatted(cell)) return null;
        return cell.getLocalDateTimeCellValue().toLocalDate();
    }

    private static int lastRow(Sheet sheet) {
        return Math.min(sheet.getLastRowNum(), MAX_ROW);
    }

    /** 한 줄에서 날짜 칸(수식 아님)의 열 번호들 — 기간 "시작 ~ 끝" 을 찾는 데 쓴다. */
    private static List<Integer> dateColumns(Sheet sheet, int row) {
        List<Integer> columns = new ArrayList<>();
        for (int col = 0; col < MAX_COL; col += 1) {
            Cell c = cell(sheet, row, col);
            if (c != null && !isFormula(c) && date(c) != null) columns.add(col);
        }
        return columns;
    }

    // ───────────────────────── Summary(VK) ─────────────────────────

    /** Summary(VK) — 필드 → 줄. 값 열은 항목 열(D)과 합계 열(F) 두 가지다. */
    static final class Summary {
        Sheet sheet;
        int itemCol = 3;
        int totalCol = 5;
        int periodRow = -1;
        int periodStartCol = -1;
        int periodEndCol = -1;
        /** 필드(steam_srf_invoice_summary) → 줄 */
        final Map<String, Integer> rows = new LinkedHashMap<>();
        /** "3. OTHERS" 아래에서 화면에 칸이 없는 줄 (4) 이후) */
        final List<Integer> othersExtraRows = new ArrayList<>();
        /** "4. OTHERS (O&M of NOx Reduction Facility)" 같이 화면에 없는 묶음의 제목 줄 */
        final List<Integer> unknownBlockRows = new ArrayList<>();
        int totalRow = -1;

        int colOf(String field) {
            return "payableAdjust".equals(field) ? totalCol : itemCol;
        }

        Cell cellOf(String field) {
            Integer row = rows.get(field);
            return row == null ? null : cell(sheet, row, colOf(field));
        }

        LocalDate periodStart() {
            return periodRow < 0 || periodStartCol < 0 ? null : date(cell(sheet, periodRow, periodStartCol));
        }
    }

    /** 화면이 가진 Summary 입력 칸 17개 (srf-store.js 의 SUMMARY_ITEMS 와 같은 순서) */
    static final List<String> SUMMARY_FIELDS = List.of(
            "bscRate", "bscFactor", "afbscrBsc", "bscSteamAmount", "ascRate", "ascFactor", "ascSteamAmount",
            "others0", "others1", "others2", "others3", "payableAdjust", "liquidatedDamage", "ascRefundAnnual",
            "utilitiesDeduction", "otherDeduction1", "otherDeduction2");

    static final Map<String, String> SUMMARY_LABELS = Map.ofEntries(
            Map.entry("bscRate", "1. BSC ▸ BASE SERVICE CHARGE"), Map.entry("bscFactor", "1. BSC ▸ ESCALATION FACTOR"),
            Map.entry("afbscrBsc", "1. BSC ▸ AFBSCr - BSC"), Map.entry("bscSteamAmount", "1. BSC ▸ STEAM SUPPLY AMOUNT"),
            Map.entry("ascRate", "2. ASC ▸ ADDITIONAL SERVICE CHARGE"), Map.entry("ascFactor", "2. ASC ▸ ESCALATION FACTOR"),
            Map.entry("ascSteamAmount", "2. ASC ▸ STEAM SUPPLY AMOUNT"), Map.entry("others0", "3. OTHERS"),
            Map.entry("others1", "3. OTHERS ▸ 1)"), Map.entry("others2", "3. OTHERS ▸ 2)"), Map.entry("others3", "3. OTHERS ▸ 3)"),
            Map.entry("payableAdjust", "VAT 와 TOTAL AMOUNT PAYABLE 사이 조정 금액"),
            Map.entry("liquidatedDamage", "LIQUIDATED DAMAGE"), Map.entry("ascRefundAnnual", "ASC REFUND(Annual)"),
            Map.entry("utilitiesDeduction", "UTILITIES DEDUCTION COSTS"),
            Map.entry("otherDeduction1", "OTHER DEDUCTION COSTS (첫째)"), Map.entry("otherDeduction2", "OTHER DEDUCTION COSTS (둘째)"));

    /** 줄의 제목 — A~C 열에서 처음 나오는 글자. */
    private static String rowLabel(Sheet sheet, int row) {
        for (int col = 0; col <= 2; col += 1) {
            String value = text(sheet, row, col);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    /** 줄에서 "Won…" 단위 칸의 열. 없으면 -1. */
    private static int wonColumn(Sheet sheet, int row) {
        for (int col = 1; col < 12; col += 1) {
            if (keyAt(sheet, row, col).startsWith("won")) return col;
        }
        return -1;
    }

    static Summary summary(Sheet sheet) {
        Summary layout = new Summary();
        layout.sheet = sheet;
        String section = "";
        boolean vatSeen = false;
        for (int row = 0; row <= lastRow(sheet); row += 1) {
            String label = key(rowLabel(sheet, row));
            if (label.startsWith("invoicefortheperiod")) {
                List<Integer> dates = dateColumns(sheet, row);
                layout.periodRow = row;
                if (!dates.isEmpty()) layout.periodStartCol = dates.get(0);
                if (dates.size() > 1) layout.periodEndCol = dates.get(1);
            } else if (label.startsWith("1.bsc")) {
                section = "bsc";
            } else if (label.startsWith("2.asc")) {
                section = "asc";
            } else if (label.startsWith("3.others")) {
                section = "others";
                layout.rows.putIfAbsent("others0", row);
            } else if (label.matches("^\\d+\\.others.*")) {
                section = "unknown";
                layout.unknownBlockRows.add(row);
            } else if (label.startsWith("totalservicecharges")) {
                section = "total";
                layout.totalRow = row;
            } else if (label.startsWith("totalamountpayable")) {
                section = "payable";
            } else if (label.equals("deductions")) {
                section = "deductions";
            } else if (label.startsWith("netcash")) {
                section = "net";
            } else if (section.equals("bsc")) {
                if (label.startsWith("baseservicecharge")) layout.rows.putIfAbsent("bscRate", row);
                else if (label.startsWith("escalationfactor")) layout.rows.putIfAbsent("bscFactor", row);
                else if (label.startsWith("afbscr")) layout.rows.putIfAbsent("afbscrBsc", row);
                else if (label.startsWith("steamsupplyamount")) layout.rows.putIfAbsent("bscSteamAmount", row);
            } else if (section.equals("asc")) {
                if (label.startsWith("additionalservicecharge")) layout.rows.putIfAbsent("ascRate", row);
                else if (label.startsWith("escalationfactor")) layout.rows.putIfAbsent("ascFactor", row);
                else if (label.startsWith("steamsupplyamount")) layout.rows.putIfAbsent("ascSteamAmount", row);
            } else if (section.equals("others")) {
                Matcher m = NUMBERED.matcher(label);
                if (m.matches()) {
                    int no = Integer.parseInt(m.group(1));
                    if (no >= 1 && no <= 3) layout.rows.putIfAbsent("others" + no, row);
                    else layout.othersExtraRows.add(row);
                }
            } else if (section.equals("total")) {
                if (label.startsWith("vatat")) vatSeen = true;
                else if (vatSeen && label.isEmpty() && wonColumn(sheet, row) >= 0) layout.rows.putIfAbsent("payableAdjust", row);
            } else if (section.equals("deductions")) {
                if (label.startsWith("liquidateddamage")) layout.rows.putIfAbsent("liquidatedDamage", row);
                else if (label.startsWith("ascrefund")) layout.rows.putIfAbsent("ascRefundAnnual", row);
                else if (label.startsWith("utilitiesdeduction")) layout.rows.putIfAbsent("utilitiesDeduction", row);
                else if (label.startsWith("otherdeduction")) {
                    if (!layout.rows.containsKey("otherDeduction1")) layout.rows.put("otherDeduction1", row);
                    else layout.rows.putIfAbsent("otherDeduction2", row);
                }
            }
        }
        // 값 열: 단위("Won…") 칸의 바로 왼쪽
        Integer rateRow = layout.rows.get("bscRate");
        if (rateRow != null && wonColumn(sheet, rateRow) > 0) layout.itemCol = wonColumn(sheet, rateRow) - 1;
        if (layout.totalRow >= 0 && wonColumn(sheet, layout.totalRow) > 0) layout.totalCol = wonColumn(sheet, layout.totalRow) - 1;
        return layout;
    }

    // ───────────────────────── Client Invoice ─────────────────────────

    static final String UTILITIES = "utilities", OTHER_OUT = "other-out", OTHER_IN = "other-in", EXTRA = "extra";
    /** 화면의 줄 수 (srf-boiler-invoice-client.html) */
    static final Map<String, Integer> CLIENT_CAPACITY = Map.of(UTILITIES, 10, OTHER_OUT, 3, OTHER_IN, 3);
    /** 유틸리티 10줄의 제목에 들어 있어야 할 글자 (순서대로) */
    static final List<String> UTILITY_KEYWORDS = List.of("electricity", "lng", "보충수", "processwater", "약품희석수",
            "water(생활용수)", "processwater", "1stsoftwater", "2ndsoftenwater", "water(생활용수)");

    static final class Client {
        Sheet sheet;
        int headerRow = -1;
        int sectionCol = 0;
        Integer labelCol, unitCol, qtyCol, priceCol, amountCol, noteCol;
        int periodRow = -1;
        int periodStartCol = -1;
        int periodEndCol = -1;
        /** 구분 → 줄 번호들 (Sub total 앞까지) */
        final Map<String, List<Integer>> lines = new LinkedHashMap<>();
        /** 화면에 없는 묶음(4. Other Charge …) — 제목 → 줄 번호들 */
        final Map<String, List<Integer>> extraSections = new LinkedHashMap<>();
        final Map<String, Integer> subtotalRows = new LinkedHashMap<>();

        boolean found() {
            return headerRow >= 0 && qtyCol != null && priceCol != null;
        }
    }

    static Client client(Sheet sheet) {
        Client layout = new Client();
        layout.sheet = sheet;
        layout.headerRow = SheetLabels.findRowWithAll(sheet, 0, "세부 항목", "단위");
        if (layout.headerRow < 0) return layout;
        int header = layout.headerRow;
        Integer sectionCol = SheetLabels.column(sheet, header, "구분", 0);
        layout.sectionCol = sectionCol == null ? 0 : sectionCol;
        layout.labelCol = SheetLabels.column(sheet, header, "세부 항목", 0);
        layout.unitCol = SheetLabels.column(sheet, header, "단위", 0);
        layout.qtyCol = SheetLabels.column(sheet, header, "양", 0);
        layout.priceCol = SheetLabels.column(sheet, header, "단가", 0);
        layout.amountCol = SheetLabels.column(sheet, header, "청구금액", 0);
        layout.noteCol = SheetLabels.column(sheet, header, "비고", 0);
        for (int row = 0; row < header; row += 1) {
            List<Integer> dates = dateColumns(sheet, row);
            if (dates.size() >= 2) {
                layout.periodRow = row;
                layout.periodStartCol = dates.get(0);
                layout.periodEndCol = dates.get(1);
                break;
            }
        }
        String current = null;
        List<Integer> currentLines = null;
        for (int row = header + 1; row <= lastRow(sheet); row += 1) {
            String sectionLabel = text(sheet, row, layout.sectionCol);
            String section = key(sectionLabel);
            if (section.startsWith("1.utilities")) {
                current = UTILITIES;
                currentLines = layout.lines.computeIfAbsent(current, k -> new ArrayList<>());
            } else if (section.startsWith("2.othercharge")) {
                current = OTHER_OUT;
                currentLines = layout.lines.computeIfAbsent(current, k -> new ArrayList<>());
            } else if (section.startsWith("3.othercharge")) {
                current = OTHER_IN;
                currentLines = layout.lines.computeIfAbsent(current, k -> new ArrayList<>());
            } else if (section.matches("^\\d+\\.(othercharge|utilities).*")) {
                current = EXTRA;
                currentLines = layout.extraSections.computeIfAbsent(
                        sectionLabel.replaceAll("\\s+", " ").trim(), k -> new ArrayList<>());
            }
            if (current == null) continue;
            if (layout.labelCol != null && keyAt(sheet, row, layout.labelCol).equals("subtotal")) {
                if (!EXTRA.equals(current)) layout.subtotalRows.put(current, row);
                current = null;
                currentLines = null;
                continue;
            }
            currentLines.add(row);
        }
        return layout;
    }

    // ───────────────────────── 운영내역 ─────────────────────────

    static final class Operation {
        Sheet sheet;
        int headerRow = -1;
        int dateCol = -1;
        Integer steamCol, processCol, deaeratorCol, powerCol;
        /** 일 → 줄 */
        final Map<Integer, Integer> dayRows = new LinkedHashMap<>();
        int totalRow = -1;
        /** 화면에 없는데 값이 들어 있는 열의 이름 (약품희석수 Main (A), 전력량 De-NOx 총 사용량(B) …) */
        final List<String> extraColumns = new ArrayList<>();
        /** "2. 2026년 10월 보일러 운영내역" 제목 칸 {줄, 열}. 없으면 null */
        int[] titleCell;
    }

    private static final Pattern TITLE_PERIOD = Pattern.compile("\\d{4}\\s*년\\s*\\d{1,2}\\s*월");

    static Operation operation(Sheet sheet) {
        Operation layout = new Operation();
        layout.sheet = sheet;
        layout.headerRow = SheetLabels.findRow(sheet, 0, k -> k.equals("일자"));
        if (layout.headerRow < 0) return layout;
        int header = layout.headerRow;
        layout.dateCol = SheetLabels.column(sheet, header, "일자", 0);
        for (int row = 0; row < header && layout.titleCell == null; row += 1) {
            for (int col = 0; col < MAX_COL; col += 1) {
                if (TITLE_PERIOD.matcher(text(sheet, row, col)).find()) {
                    layout.titleCell = new int[]{row, col};
                    break;
                }
            }
        }
        for (int col = layout.dateCol + 1; col < MAX_COL; col += 1) {
            String label = keyAt(sheet, header, col);
            if (label.startsWith("스팀생산량") && layout.steamCol == null) layout.steamCol = col;
            else if (label.startsWith("공정수사용량") && layout.processCol == null) layout.processCol = col;
            else if (label.startsWith("탈기기급수량") && layout.deaeratorCol == null) layout.deaeratorCol = col;
            else if (label.equals("전력량") && layout.powerCol == null) layout.powerCol = col;
        }
        int firstDayRow = -1;
        for (int row = header + 1; row <= Math.min(header + 45, lastRow(sheet)); row += 1) {
            if (keyAt(sheet, row, layout.dateCol).startsWith("누계")) {
                layout.totalRow = row;
                break;
            }
            Integer day = dayOf(cell(sheet, row, layout.dateCol));
            if (day == null) continue;
            layout.dayRows.putIfAbsent(day, row);
            if (firstDayRow < 0) firstDayRow = row;
        }
        if (firstDayRow < 0) return layout;
        // 화면에 없는 열 — 머리글이 아는 이름이 아닌데 일별 값이 있는 열
        String top = "";
        for (int col = layout.dateCol + 1; col < MAX_COL; col += 1) {
            String here = text(sheet, header, col);
            if (!here.isBlank()) top = here;
            String topKey = key(top);
            boolean known = topKey.startsWith("스팀생산량") || topKey.startsWith("공정수사용량") || topKey.startsWith("보충수")
                    || topKey.startsWith("탈기기급수량") || topKey.startsWith("급수량-보충수")
                    || (layout.powerCol != null && col == layout.powerCol);
            if (known) continue;
            boolean hasValue = false;
            for (int row : layout.dayRows.values()) {
                if (number(sheet, row, col) != null) {
                    hasValue = true;
                    break;
                }
            }
            if (!hasValue) continue;
            StringBuilder name = new StringBuilder(top.replaceAll("\\s+", " ").trim());
            for (int row = header + 1; row < firstDayRow; row += 1) {
                String sub = text(sheet, row, col).replaceAll("\\s+", " ").trim();
                if (!sub.isEmpty() && !sub.startsWith("(")) name.append(' ').append(sub);
            }
            layout.extraColumns.add(name.toString().trim());
        }
        return layout;
    }

    /** 일자 칸에서 일(1~31) — "10월 1일", 2, 날짜 칸을 모두 받는다. */
    static Integer dayOf(Cell cell) {
        if (cell == null) return null;
        LocalDate date = date(cell);
        if (date != null) return date.getDayOfMonth();
        CellType type = valueType(cell);
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return value >= 1 && value <= 31 && value == Math.rint(value) ? (int) value : null;
        }
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            Matcher m = DAY_LABEL.matcher(text);
            if (m.find()) {
                int day = Integer.parseInt(m.group(1));
                return day >= 1 && day <= 31 ? day : null;
            }
            if (text.matches("\\d{1,2}")) {
                int day = Integer.parseInt(text);
                return day >= 1 && day <= 31 ? day : null;
            }
        }
        return null;
    }

    // ───────────────────────── BSC ─────────────────────────

    /** BSC 시트의 "Base Service Charge Rate (Won/ton)" 값 칸 {줄, 열}. 못 찾으면 null. */
    static int[] bscRateCell(Sheet sheet) {
        int row = SheetLabels.findRow(sheet, 0, k -> k.startsWith("baseservicechargerate(won/ton)"));
        if (row < 0) return null;
        Integer col = SheetLabels.columnStartingWith(sheet, row, "Base Service Charge Rate (Won/ton)", 0);
        return col == null ? null : new int[]{row + 1, col};
    }

    // ───────────────────────── Adjustment Factor ─────────────────────────

    static final List<String> ADJUSTMENT_SECTIONS = List.of("afbsc", "afbsc-rebase", "afasc");

    static final class Adjustment {
        Sheet sheet;
        /** 표 → 항목 코드 → 줄 */
        final Map<String, Map<String, Integer>> rows = new LinkedHashMap<>();
        /** 표 → {계수 열, Base 열, Present 열} */
        final Map<String, int[]> cols = new LinkedHashMap<>();
    }

    static Adjustment adjustment(Sheet sheet) {
        Adjustment layout = new Adjustment();
        layout.sheet = sheet;
        String pending = null;
        for (int row = 0; row <= lastRow(sheet); row += 1) {
            String label = key(rowLabel(sheet, row));
            if (label.startsWith("2-1.adjustmentfactor")) pending = "afbsc-rebase";
            else if (label.startsWith("2.adjustmentfactor")) pending = "afbsc";
            else if (label.startsWith("3.adjustmentfactor")) pending = "afasc";
            if (pending == null || layout.rows.containsKey(pending)) continue;
            Integer coefCol = SheetLabels.column(sheet, row, "Coefficient", 0);
            Integer baseCol = SheetLabels.column(sheet, row, "Base", 0);
            Integer presentCol = SheetLabels.column(sheet, row, "Present", 0);
            if (coefCol == null || baseCol == null || presentCol == null || coefCol < 2) continue;
            layout.cols.put(pending, new int[]{coefCol, baseCol, presentCol});
            Map<String, Integer> items = new LinkedHashMap<>();
            for (int r = row + 1; r <= Math.min(row + 15, lastRow(sheet)); r += 1) {
                String item = keyAt(sheet, r, coefCol - 2);
                if (!item.matches("[a-h]")) {
                    if (item.startsWith("afbsc") || item.startsWith("afasc")) break;
                    continue;
                }
                String definition = keyAt(sheet, r, coefCol - 1);
                String code = item;
                if (item.equals("e") && definition.contains("bottom")) code = "e-bottom";
                else if (item.equals("e") && definition.contains("fly")) code = "e-fly";
                items.putIfAbsent(code, r);
            }
            layout.rows.put(pending, items);
            pending = null;
        }
        return layout;
    }

    // ───────────────────────── Accumulated steam volume ─────────────────────────

    static final class Accumulated {
        Sheet sheet;
        int headerRow = -1;
        Integer yearCol, monthCol, contractCol, bscCol, ascCol;
        /** 달(1~12) → 줄. 회계연도 순서(6월…5월)로 들어 있다 */
        final Map<Integer, Integer> monthRows = new LinkedHashMap<>();
        /** 맨 위 표의 YEAR 칸에 적힌 첫 연도 (회계연도 시작 연도). 없으면 null */
        Integer labelledYear;
        /** 아래쪽 과거 연도 표의 수 */
        int historyBlocks;

        boolean found() {
            return headerRow >= 0 && monthCol != null && contractCol != null && bscCol != null && ascCol != null;
        }
    }

    static Accumulated accumulated(Sheet sheet) {
        Accumulated layout = new Accumulated();
        layout.sheet = sheet;
        layout.headerRow = SheetLabels.findRow(sheet, 0, k -> k.startsWith("contract") && k.contains("supplyvolume"));
        if (layout.headerRow < 0) return layout;
        int header = layout.headerRow;
        layout.yearCol = SheetLabels.column(sheet, header, "YEAR", 0);
        layout.monthCol = SheetLabels.column(sheet, header, "MONTH", 0);
        layout.contractCol = SheetLabels.columnStartingWith(sheet, header, "Contract", 0);
        layout.bscCol = SheetLabels.column(sheet, header + 1, "BSC", 0);
        layout.ascCol = SheetLabels.column(sheet, header + 1, "ASC", 0);
        if (layout.monthCol == null) return layout;
        int end = header + 1;
        for (int row = header + 1; row <= Math.min(header + 18, lastRow(sheet)); row += 1) {
            String label = keyAt(sheet, row, layout.monthCol);
            if (label.equals("total")) break;
            Integer month = MONTH_NAMES.get(label);
            if (month == null) continue;
            layout.monthRows.putIfAbsent(month, row);
            end = row;
            if (layout.labelledYear == null && layout.yearCol != null) {
                BigDecimal year = number(sheet, row, layout.yearCol);
                if (year != null) layout.labelledYear = year.intValue();
            }
        }
        for (int row = end + 1; row <= lastRow(sheet); row += 1) {
            if (SheetLabels.column(sheet, row, "YEAR", 0) != null && SheetLabels.column(sheet, row, "MONTH", 0) != null) {
                layout.historyBlocks += 1;
            }
        }
        return layout;
    }

    // ───────────────────────── 월별 운영내역 ─────────────────────────

    /** 화면의 월별 입력 칸 21개 (srf-store.js 의 MONTHLY_FIELDS) */
    static final List<String> MONTHLY_FIELDS = List.of("steamQty", "steamUnit", "powerQty", "powerUnit", "lngQty", "lngUnit",
            "processQty", "processUnit", "softQty", "softUnit", "softenQty", "softenUnit", "lifeQty", "lifeUnit",
            "etcSpecial", "etcBsc7", "etcSrfShare", "etcInspect", "etcOver7", "etcAirOver", "etcWaste");

    /** {수량 머리글, 수량 필드, 단가 필드} — 단가는 수량 열 바로 뒤의 "…단가" 열 */
    private static final String[][] MONTHLY_PAIRS = {
            {"스팀구매량", "steamQty", "steamUnit"}, {"전력사용량", "powerQty", "powerUnit"}, {"lng사용량", "lngQty", "lngUnit"},
            {"공정수사용량", "processQty", "processUnit"}, {"청수사용량", "softQty", "softUnit"},
            {"연수사용량", "softenQty", "softenUnit"}, {"생활용수", "lifeQty", "lifeUnit"}};
    /** {머리글에 들어 있는 글자, 필드} — "7차연도"·"8차연도" 처럼 해마다 바뀌는 앞머리는 보지 않는다 */
    private static final String[][] MONTHLY_ETC = {
            {"미달금액", "etcSpecial"}, {"bsc물량정산금", "etcBsc7"}, {"srf이익", "etcSrfShare"}, {"검사수수료", "etcInspect"},
            {"단가초과지급분", "etcOver7"}, {"대기초과배출부과금", "etcAirOver"}, {"폐기물처분부담금", "etcWaste"}};

    static final class Monthly {
        Sheet sheet;
        /** 시트에 있는 회계연도 표: 연도 → "YYYY년도" 줄 */
        final Map<Integer, Integer> blocks = new LinkedHashMap<>();
        /** 고른 표의 "YYYY년도" 줄. 없으면 -1 */
        int blockRow = -1;
        int labelCol = 0;
        /** 필드 → 열 */
        final Map<String, Integer> cols = new LinkedHashMap<>();
        /** "지급액" 열 (스팀구매량 × 스팀단가). 없으면 null */
        Integer paidCol;
        /** 달(1~12) → 줄 */
        final Map<Integer, Integer> monthRows = new LinkedHashMap<>();
        /** "※ De-Nox설비 운영비" 표가 옆에 붙어 있는가 */
        boolean deNoxTable;
    }

    /**
     * @param fiscalYear     찾을 회계연도(6월이 속한 해)
     * @param firstIfMissing 그 연도 표가 없으면 맨 위 표를 고른다 (내보내기 — 서식의 맨 위 표를 그 연도로 바꿔 쓴다)
     */
    static Monthly monthly(Sheet sheet, int fiscalYear, boolean firstIfMissing) {
        Monthly layout = new Monthly();
        layout.sheet = sheet;
        for (int row = 0; row <= lastRow(sheet); row += 1) {
            Matcher m = FISCAL_BLOCK.matcher(keyAt(sheet, row, layout.labelCol));
            if (m.matches()) layout.blocks.putIfAbsent(Integer.parseInt(m.group(1)), row);
            for (int col = 0; col < MAX_COL && row <= 3 && !layout.deNoxTable; col += 1) {
                if (keyAt(sheet, row, col).contains("de-nox설비")) layout.deNoxTable = true;
            }
        }
        Integer blockRow = layout.blocks.get(fiscalYear);
        if (blockRow == null && firstIfMissing && !layout.blocks.isEmpty()) blockRow = layout.blocks.values().iterator().next();
        if (blockRow == null) return layout;
        layout.blockRow = blockRow;
        int itemRow = blockRow + 1;
        for (String[] pair : MONTHLY_PAIRS) {
            Integer qtyCol = SheetLabels.column(sheet, itemRow, pair[0], 1);
            if (qtyCol == null) continue;
            layout.cols.put(pair[1], qtyCol);
            for (int col = qtyCol + 1; col <= qtyCol + 2; col += 1) {
                if (keyAt(sheet, itemRow, col).endsWith("단가")) {
                    layout.cols.put(pair[2], col);
                    break;
                }
            }
        }
        layout.paidCol = SheetLabels.column(sheet, itemRow, "지급액", 1);
        for (int col = 1; col < MAX_COL; col += 1) {
            String label = keyAt(sheet, itemRow, col);
            if (label.isEmpty()) continue;
            for (String[] etc : MONTHLY_ETC) {
                if (label.contains(etc[0])) layout.cols.putIfAbsent(etc[1], col);
            }
        }
        layout.monthRows.putAll(SheetLabels.monthRows(sheet, layout.labelCol, itemRow + 1, itemRow + 14,
                k -> k.startsWith("합계")));
        return layout;
    }

    /** 회계연도(6월 시작)의 달 → 실제 연도 */
    static int calendarYear(int fiscalYear, int month) {
        return month >= 6 ? fiscalYear : fiscalYear + 1;
    }

    /** 연/월이 속한 회계연도(6월 시작) */
    static int fiscalYearOf(int year, int month) {
        return month >= 6 ? year : year - 1;
    }

    // ───────────────────────── 사고이력 ─────────────────────────

    static final class Incident {
        Sheet sheet;
        /** 시트 이름("26년 사고이력")의 연도. 못 읽으면 null */
        Integer year;
        int headerRow = -1;
        Integer noCol, dateCol, reasonCol, actionCol, downtimeCol, lossCol, noteCol;
        /** No. → 줄 */
        final Map<Integer, Integer> rows = new LinkedHashMap<>();
        /** "'26년 복합보일러 설비 사고 이력" 제목 칸 {줄, 열}. 없으면 null */
        int[] titleCell;

        boolean found() {
            return headerRow >= 0 && noCol != null && dateCol != null && reasonCol != null;
        }
    }

    static Integer incidentYear(String sheetName) {
        Matcher m = INCIDENT_SHEET.matcher(key(sheetName));
        if (!m.matches()) return null;
        int year = Integer.parseInt(m.group(1));
        return year < 100 ? 2000 + year : year;
    }

    static Incident incident(Sheet sheet) {
        Incident layout = new Incident();
        layout.sheet = sheet;
        layout.year = incidentYear(sheet.getSheetName());
        layout.headerRow = SheetLabels.findRowWithAll(sheet, 0, "No.", "일자");
        if (layout.headerRow < 0) return layout;
        int header = layout.headerRow;
        layout.noCol = SheetLabels.column(sheet, header, "No.", 0);
        layout.dateCol = SheetLabels.column(sheet, header, "일자", 0);
        layout.reasonCol = SheetLabels.column(sheet, header, "사유", 0);
        layout.actionCol = SheetLabels.column(sheet, header, "조치내역", 0);
        layout.downtimeCol = SheetLabels.columnStartingWith(sheet, header, "운휴시간", 0);
        layout.lossCol = SheetLabels.columnStartingWith(sheet, header, "손실금액", 0);
        layout.noteCol = SheetLabels.column(sheet, header, "비고", 0);
        for (int row = 0; row < header && layout.titleCell == null; row += 1) {
            for (int col = 0; col < 12; col += 1) {
                if (key(text(sheet, row, col)).contains("사고이력")) {
                    layout.titleCell = new int[]{row, col};
                    break;
                }
            }
        }
        for (int row = header + 1; row <= Math.min(header + 60, lastRow(sheet)); row += 1) {
            Cell noCell = cell(sheet, row, layout.noCol);
            if (noCell == null || isFormula(noCell)) continue;
            BigDecimal no = valueType(noCell) == CellType.NUMERIC ? number(noCell) : null;
            if (no == null) {
                if (!text(sheet, row, layout.noCol).isBlank()) break;   // "발생횟수" 합계 줄
                continue;
            }
            layout.rows.putIfAbsent(no.intValue(), row);
        }
        return layout;
    }
}
