package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.dto.SteamSrfInvoiceExcelImportResponse;
import com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.Accumulated;
import com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.Adjustment;
import com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.Client;
import com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.Incident;
import com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.Monthly;
import com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.Operation;
import com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.Summary;
import com.company.module.steamenergy.service.table.SteamComboDailyProductionAdapter;
import com.company.module.steamenergy.service.table.SteamIncidentAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceAccumulatedAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceAdjustmentAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceBscAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceClientItemAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceMonthlyAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceOperationDailyAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceSummaryAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.cell;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.isFormula;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.key;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.number;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.string;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.text;

/**
 * SRF 보일러 인보이스 통합본(KleanNara SRF Boiler AOT invoice(N월).xls) 가져오기.
 *
 * <p>시트마다 화면이 가진 칸만 규격 표(steam_srf_invoice_*)에 넣는다. 자리는 {@link SrfInvoiceSheets} 가 글자로 찾는다.
 * <ul>
 *   <li><b>원본값이 우선</b> — 엑셀의 빈칸은 그 달의 저장값도 비운다.</li>
 *   <li>화면에 칸이 없는 것(4. OTHERS (NOx) 묶음, De-NOx · 액체산소 열 …)은 저장소를 만들지 않고 건너뛰며 경고로 알린다.</li>
 *   <li>시트나 제목을 못 찾으면 그 부분만 건너뛰고 경고로 알린다(이미 저장된 값은 그대로 둔다).</li>
 *   <li>Summary 의 Client Invoice 연동 칸(3. OTHERS, 1), UTILITIES · OTHER DEDUCTION)은 엑셀에서 수식이고
 *       값이 연동값과 같으면 NULL 로 둔다 — 화면이 Client Invoice 를 따라가게. 손으로 넣은 숫자이거나 연동값과 다르면 그 값을 넣는다.</li>
 *   <li>운영내역은 전력량만 저장한다. 스팀생산량 · 공정수 · 탈기기급수량은 복합보일러 생산 데이터와 비교만 한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SrfInvoiceExcelImportService {

    /** 비어 있으면 Client Invoice 를 따르는 Summary 칸 */
    static final Set<String> CLIENT_LINKED = Set.of("others0", "others1", "utilitiesDeduction", "otherDeduction1", "otherDeduction2");
    /** 화면의 Adjustment Factor 항목 */
    static final Map<String, Set<String>> ADJUSTMENT_ITEMS = Map.of(
            "afbsc", Set.of("a", "b", "c", "d", "e", "e-bottom", "e-fly", "f", "g"),
            "afbsc-rebase", Set.of("a", "b", "c", "d", "e-bottom", "e-fly", "f", "g", "h"),
            "afasc", Set.of("b", "c", "d", "e", "f", "g"));
    static final String INCIDENT_PREFIX = "srf-accident-record:";
    private static final BigDecimal LINK_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal COMPARE_TOLERANCE = new BigDecimal("0.0005");

    private final SteamSrfInvoiceSummaryAdapter summaryTable;
    private final SteamSrfInvoiceClientItemAdapter clientTable;
    private final SteamSrfInvoiceOperationDailyAdapter operationTable;
    private final SteamSrfInvoiceMonthlyAdapter monthlyTable;
    private final SteamSrfInvoiceAccumulatedAdapter accumulatedTable;
    private final SteamSrfInvoiceBscAdapter bscTable;
    private final SteamSrfInvoiceAdjustmentAdapter adjustmentTable;
    private final SteamIncidentAdapter incidentTable;
    private final SteamComboDailyProductionAdapter comboTable;

    /** 가져오기 한 번의 진행 상태 */
    private static final class Run {
        int year;
        int month;
        final List<String> warnings = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        BigDecimal workbookTotal;
        BigDecimal screenTotal;

        void count(String sheet, Object value) {
            counts.putIfAbsent(sheet, 0);
            if (value != null) counts.merge(sheet, 1, Integer::sum);
        }
    }

    /**
     * @param year  선택한 연도 (없으면 엑셀의 기간을 쓴다)
     * @param month 선택한 월 (없으면 엑셀의 기간을 쓴다)
     * @throws IllegalArgumentException 기간을 읽지 못했거나 선택한 기간과 다를 때
     */
    @Transactional
    public SteamSrfInvoiceExcelImportResponse importWorkbook(InputStream input, Integer year, Integer month) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            return importWorkbook(workbook, year, month);
        }
    }

    SteamSrfInvoiceExcelImportResponse importWorkbook(Workbook workbook, Integer year, Integer month) {
        Run run = new Run();
        Sheet summarySheet = SrfInvoiceSheets.summarySheet(workbook);
        Summary summary = summarySheet == null ? null : SrfInvoiceSheets.summary(summarySheet);
        resolvePeriod(run, summary, year, month);

        Map<String, Map<String, Object>> clientState = importClient(workbook, run);
        importSummary(summary, clientState, run);
        importOperation(workbook, run);
        importMonthly(workbook, run);
        importAccumulated(workbook, run);
        importBsc(workbook, run);
        importAdjustment(workbook, run);
        importIncident(workbook, run);

        int imported = run.counts.values().stream().mapToInt(Integer::intValue).sum();
        return SteamSrfInvoiceExcelImportResponse.builder()
                .year(run.year).month(run.month)
                .fiscalYear(SrfInvoiceSheets.fiscalYearOf(run.year, run.month))
                .imported(imported).sheets(run.counts)
                .workbookTotal(run.workbookTotal).screenTotal(run.screenTotal)
                .warnings(run.warnings)
                .build();
    }

    // ───────────────────────── 기간 ─────────────────────────

    private void resolvePeriod(Run run, Summary summary, Integer year, Integer month) {
        LocalDate start = summary == null ? null : summary.periodStart();
        if (start == null) {
            if (year == null || month == null) {
                throw new IllegalArgumentException("Summary(VK) 시트의 'Invoice for the period' 시작일을 읽지 못했습니다. "
                        + "SRF 보일러 인보이스 통합본(KleanNara SRF Boiler AOT invoice)이 맞는지 확인해 주세요.");
            }
            run.year = year;
            run.month = month;
            run.warnings.add("Summary(VK) 시트에서 인보이스 기간을 읽지 못해 선택한 기간(" + year + "년 " + month + "월)으로 반영했습니다.");
            return;
        }
        run.year = start.getYear();
        run.month = start.getMonthValue();
        if ((year != null && year != run.year) || (month != null && month != run.month)) {
            throw new IllegalArgumentException("엑셀의 인보이스 기간(" + run.year + "년 " + run.month + "월)이 선택한 기간("
                    + (year == null ? run.year : year) + "년 " + (month == null ? run.month : month) + "월)과 다릅니다. "
                    + "기간을 맞추거나 해당 월의 파일을 올려 주세요.");
        }
    }

    // ───────────────────────── Client Invoice ─────────────────────────

    /** @return 반영한 뒤의 그 달 Client Invoice 줄 — "구분|줄" → 행 (Summary 연동값 계산용) */
    private Map<String, Map<String, Object>> importClient(Workbook workbook, Run run) {
        Map<String, Map<String, Object>> state = new LinkedHashMap<>();
        for (Map<String, Object> row : clientTable.load(run.year, run.month)) {
            state.put(row.get("section") + "|" + row.get("lineNo"), new LinkedHashMap<>(row));
        }
        Sheet sheet = SrfInvoiceSheets.clientSheet(workbook);
        if (sheet == null) {
            run.warnings.add("'Client Invoice' 시트가 없어 건너뛰었습니다.");
            return state;
        }
        Client layout = SrfInvoiceSheets.client(sheet);
        if (!layout.found()) {
            run.warnings.add("Client Invoice: 머리글(세부 항목 · 단위 · 양 · 단가)을 찾지 못해 건너뛰었습니다.");
            return state;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String section : List.of(SrfInvoiceSheets.UTILITIES, SrfInvoiceSheets.OTHER_OUT, SrfInvoiceSheets.OTHER_IN)) {
            List<Integer> lines = layout.lines.get(section);
            if (lines == null) {
                run.warnings.add("Client Invoice: '" + sectionName(section) + "' 구분을 찾지 못해 건너뛰었습니다.");
                continue;
            }
            int capacity = SrfInvoiceSheets.CLIENT_CAPACITY.get(section);
            for (int i = 0; i < lines.size(); i += 1) {
                int line = lines.get(i);
                BigDecimal qty = number(sheet, line, layout.qtyCol);
                BigDecimal price = number(sheet, line, layout.priceCol);
                String label = layout.labelCol == null ? null : cleanLabel(string(sheet, line, layout.labelCol));
                if (i >= capacity) {
                    if (qty != null || price != null) {
                        run.warnings.add("Client Invoice: '" + sectionName(section) + "' " + (i + 1) + "번째 줄"
                                + (label == null ? "" : "(" + label + ")") + "은 화면에 칸이 없어 건너뛰었습니다.");
                    }
                    continue;
                }
                String stateKey = section + "|" + i;
                Map<String, Object> row = state.computeIfAbsent(stateKey, k -> new LinkedHashMap<>());
                row.put("yearNo", run.year);
                row.put("monthNo", run.month);
                row.put("section", section);
                row.put("lineNo", i);
                row.put("qty", qty);
                row.put("unitPrice", price);
                if (layout.noteCol != null) row.put("note", limit(string(sheet, line, layout.noteCol), 500));
                if (SrfInvoiceSheets.UTILITIES.equals(section)) {
                    String keyword = SrfInvoiceSheets.UTILITY_KEYWORDS.get(i);
                    if (label != null && !key(label).contains(keyword)) {
                        run.warnings.add("Client Invoice: 유틸리티 " + (i + 1) + "번째 줄 제목이 '" + label
                                + "' 입니다 — 화면의 " + (i + 1) + "번째 항목과 같은지 확인해 주세요.");
                    }
                } else {
                    row.put("itemLabel", limit(label, 200));
                    if (layout.unitCol != null) row.put("unitLabel", limit(string(sheet, line, layout.unitCol), 50));
                }
                for (String field : List.of("qty", "unitPrice", "note", "itemLabel", "unitLabel")) {
                    run.count("clientInvoice", row.get(field));
                }
                rows.add(row);
            }
        }
        layout.extraSections.forEach((title, lines) -> {
            BigDecimal sum = BigDecimal.ZERO;
            for (int line : lines) {
                BigDecimal qty = number(sheet, line, layout.qtyCol);
                BigDecimal price = number(sheet, line, layout.priceCol);
                if (qty != null && price != null) sum = sum.add(qty.multiply(price));
            }
            run.warnings.add("Client Invoice: '" + title + "' 묶음(" + lines.size() + "줄, 합계 " + fmt(sum)
                    + "원)은 화면에 칸이 없어 건너뛰었습니다.");
        });
        if (!rows.isEmpty()) clientTable.saveBulk(run.year, run.month, rows, false);
        return state;
    }

    private static String sectionName(String section) {
        return switch (section) {
            case SrfInvoiceSheets.UTILITIES -> "1. Utilities";
            case SrfInvoiceSheets.OTHER_OUT -> "2. Other Charge (깨끗한 나라 → 베올리아)";
            default -> "3. Other Charge (깨끗한 나라 ← 베올리아)";
        };
    }

    /** " · Electricity (전력)" 의 앞머리 점을 떼어 낸다. 점만 있으면 null. */
    static String cleanLabel(String label) {
        if (label == null) return null;
        String cleaned = label.replaceFirst("^[·\\s\\u00A0]+", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** 화면(srf-boiler-invoice-pages.js clientLinkedValue)과 같은 연동값. 수량이나 단가가 비어 있으면 null. */
    static BigDecimal clientLinkValue(String field, Map<String, Map<String, Object>> clientState) {
        return switch (field) {
            case "others0" -> clientAmount(clientState, SrfInvoiceSheets.OTHER_IN, 0);
            case "others1" -> clientAmount(clientState, SrfInvoiceSheets.OTHER_IN, 1);
            case "otherDeduction1" -> clientAmount(clientState, SrfInvoiceSheets.OTHER_OUT, 0);
            case "otherDeduction2" -> clientAmount(clientState, SrfInvoiceSheets.OTHER_OUT, 1);
            case "utilitiesDeduction" -> {
                BigDecimal sum = null;
                for (int i = 0; i < 10; i += 1) {
                    BigDecimal amount = clientAmount(clientState, SrfInvoiceSheets.UTILITIES, i);
                    if (amount != null) sum = sum == null ? amount : sum.add(amount);
                }
                yield sum;
            }
            default -> null;
        };
    }

    private static BigDecimal clientAmount(Map<String, Map<String, Object>> clientState, String section, int line) {
        Map<String, Object> row = clientState.get(section + "|" + line);
        if (row == null) return null;
        if (!(row.get("qty") instanceof BigDecimal qty) || !(row.get("unitPrice") instanceof BigDecimal price)) return null;
        return qty.multiply(price);
    }

    // ───────────────────────── Summary(VK) ─────────────────────────

    private void importSummary(Summary layout, Map<String, Map<String, Object>> clientState, Run run) {
        if (layout == null) {
            run.warnings.add("'Summary(VK)' 시트가 없어 건너뛰었습니다.");
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map<String, Object> existing : summaryTable.load(run.year, run.month)) row.putAll(existing);
        row.put("yearNo", run.year);
        row.put("monthNo", run.month);

        List<String> notFound = new ArrayList<>();
        for (String field : SrfInvoiceSheets.SUMMARY_FIELDS) {
            if (!layout.rows.containsKey(field)) {
                notFound.add(SrfInvoiceSheets.SUMMARY_LABELS.get(field));
                continue;
            }
            Cell cell = layout.cellOf(field);
            BigDecimal value = number(cell);
            if (CLIENT_LINKED.contains(field) && isFormula(cell)) {
                // 엑셀에서도 Client Invoice 를 가리키는 수식이다 — 값이 연동값과 같으면 비워 두어 화면이 계속 따라가게 한다
                BigDecimal linked = clientLinkValue(field, clientState);
                if (same(value, linked, LINK_TOLERANCE)) {
                    value = null;
                } else {
                    run.warnings.add("Summary(VK) '" + SrfInvoiceSheets.SUMMARY_LABELS.get(field) + "' 값 " + fmt(value)
                            + " 이 화면의 Client Invoice 연동값 " + fmt(linked) + " 과 달라 직접 입력값으로 저장했습니다.");
                }
            }
            row.put(field, value);
            run.count("summary", value);
        }
        if (!notFound.isEmpty()) {
            run.warnings.add("Summary(VK): 제목을 찾지 못해 건너뛴 칸 — " + String.join(", ", notFound));
        }

        Sheet sheet = layout.sheet;
        BigDecimal unknownSum = BigDecimal.ZERO;
        for (int blockRow : layout.unknownBlockRows) {
            BigDecimal amount = number(sheet, blockRow, layout.itemCol);
            if (amount != null) unknownSum = unknownSum.add(amount);
            run.warnings.add("Summary(VK) '" + blockLabel(sheet, blockRow) + "' 묶음(" + fmt(amount)
                    + "원)은 화면에 칸이 없어 건너뛰었습니다 — 화면의 TOTAL SERVICE CHARGES 가 그만큼 작게 나옵니다.");
        }
        for (int extraRow : layout.othersExtraRows) {
            BigDecimal amount = number(sheet, extraRow, layout.itemCol);
            if (amount != null && amount.signum() != 0) {
                run.warnings.add("Summary(VK) '3. OTHERS ▸ " + blockLabel(sheet, extraRow) + "' (" + fmt(amount)
                        + "원)은 화면에 칸이 없어 건너뛰었습니다.");
            }
        }
        BigDecimal others0 = effective(row, "others0", clientState);
        BigDecimal others1 = effective(row, "others1", clientState);
        Cell othersCell = layout.cellOf("others0");
        if (isFormula(othersCell) && othersCell.getCellFormula().toUpperCase().contains("SUM")
                && others1 != null && others1.signum() != 0) {
            run.warnings.add("Summary(VK): 이 양식의 '3. OTHERS' 는 아래 1)~4) 의 합계인데, 화면은 '3. OTHERS' 와 '1)' 을 따로 더합니다 "
                    + "— 화면 합계가 다르게 나올 수 있으니 확인해 주세요.");
        }

        run.workbookTotal = layout.totalRow < 0 ? null : number(sheet, layout.totalRow, layout.totalCol);
        run.screenTotal = screenTotal(row, others0, others1);
        if (run.workbookTotal != null && run.screenTotal != null && run.workbookTotal.compareTo(run.screenTotal) != 0) {
            BigDecimal gap = run.workbookTotal.subtract(run.screenTotal);
            run.warnings.add("Summary(VK): 엑셀의 TOTAL SERVICE CHARGES FOR THE MONTH " + fmt(run.workbookTotal)
                    + "원과 화면 계산값 " + fmt(run.screenTotal) + "원이 " + fmt(gap) + "원 다릅니다"
                    + (unknownSum.signum() != 0 && gap.compareTo(unknownSum) == 0 ? " (건너뛴 묶음의 금액과 같습니다)." : "."));
        }
        summaryTable.saveBulk(run.year, run.month, List.of(row), false);
    }

    private static String blockLabel(Sheet sheet, int row) {
        for (int col = 0; col <= 2; col += 1) {
            String value = text(sheet, row, col);
            if (!value.isBlank()) return value.replaceAll("\\s+", " ").trim();
        }
        return "";
    }

    private static BigDecimal effective(Map<String, Object> row, String field, Map<String, Map<String, Object>> clientState) {
        return row.get(field) instanceof BigDecimal stored ? stored : clientLinkValue(field, clientState);
    }

    /**
     * 화면(srf-boiler-invoice-pages.js calculatedCellValue)의 TOTAL SERVICE CHARGES FOR THE MONTH.
     * <pre>1. BSC = (ROUND(단가 × FACTOR, 0) + AFBSCr) × 공급량,  2. ASC = ROUND(단가 × FACTOR, 0) × 공급량,
     * TOTAL = ROUNDUP(1. BSC + 2. ASC + 3. OTHERS + 1), 0)</pre>
     * 값이 하나도 없으면 null.
     */
    static BigDecimal screenTotal(Map<String, Object> row, BigDecimal others0, BigDecimal others1) {
        boolean any = others0 != null || others1 != null;
        for (String field : List.of("bscRate", "bscFactor", "afbscrBsc", "bscSteamAmount", "ascRate", "ascFactor", "ascSteamAmount")) {
            any = any || row.get(field) != null;
        }
        if (!any) return null;
        BigDecimal bsc = nz(row.get("bscRate")).multiply(nz(row.get("bscFactor"))).setScale(0, RoundingMode.HALF_UP)
                .add(nz(row.get("afbscrBsc"))).multiply(nz(row.get("bscSteamAmount")));
        BigDecimal asc = nz(row.get("ascRate")).multiply(nz(row.get("ascFactor"))).setScale(0, RoundingMode.HALF_UP)
                .multiply(nz(row.get("ascSteamAmount")));
        return bsc.add(asc).add(nz(others0)).add(nz(others1)).setScale(0, RoundingMode.CEILING);
    }

    // ───────────────────────── 운영내역 ─────────────────────────

    private void importOperation(Workbook workbook, Run run) {
        Sheet sheet = SrfInvoiceSheets.operationSheet(workbook);
        if (sheet == null) {
            run.warnings.add("'운영내역' 시트가 없어 건너뛰었습니다.");
            return;
        }
        Operation layout = SrfInvoiceSheets.operation(sheet);
        if (layout.headerRow < 0 || layout.dayRows.isEmpty()) {
            run.warnings.add("운영내역: '일자' 머리글이나 일자 줄을 찾지 못해 건너뛰었습니다.");
            return;
        }
        int days = YearMonth.of(run.year, run.month).lengthOfMonth();
        if (layout.powerCol == null) {
            run.warnings.add("운영내역: '전력량' 열을 찾지 못해 전력량을 건너뛰었습니다.");
        } else {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int day = 1; day <= days; day += 1) {
                Integer line = layout.dayRows.get(day);
                if (line == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("usageDate", LocalDate.of(run.year, run.month, day));
                row.put("powerKwh", number(sheet, line, layout.powerCol));
                run.count("operation", row.get("powerKwh"));
                rows.add(row);
            }
            if (!rows.isEmpty()) operationTable.saveBulk(run.year, run.month, rows, false);
        }
        if (!layout.extraColumns.isEmpty()) {
            run.warnings.add("운영내역: 화면에 칸이 없어 건너뛴 열 — " + String.join(", ", layout.extraColumns)
                    + (layout.powerCol != null && layout.extraColumns.stream().anyMatch(name -> name.startsWith("전력량"))
                    ? " (전력량은 첫 열 Main (A) 만 저장했습니다)" : ""));
        }
        // 스팀생산량 · 공정수 · 탈기기급수량 — 운영내역 화면은 복합보일러 생산 데이터를 그대로 보여 준다. 저장하지 않고 비교만 한다.
        Map<Integer, Map<String, Object>> combo = new LinkedHashMap<>();
        for (Map<String, Object> row : comboTable.load(run.year, run.month)) {
            if (row.get("usageDate") instanceof LocalDate date) combo.put(date.getDayOfMonth(), row);
        }
        compareWithCombo(run, layout, combo, days, layout.steamCol, "comboSteam", "스팀생산량");
        compareWithCombo(run, layout, combo, days, layout.processCol, "comboProcess", "공정수 사용량");
        compareWithCombo(run, layout, combo, days, layout.deaeratorCol, "comboDeaerator", "탈기기급수량");
    }

    private void compareWithCombo(Run run, Operation layout, Map<Integer, Map<String, Object>> combo, int days,
                                  Integer col, String field, String label) {
        if (col == null) {
            run.warnings.add("운영내역: '" + label + "' 열을 찾지 못해 복합보일러 생산 데이터와 비교하지 못했습니다.");
            return;
        }
        int different = 0;
        String example = null;
        for (int day = 1; day <= days; day += 1) {
            Integer line = layout.dayRows.get(day);
            if (line == null) continue;
            BigDecimal excel = number(layout.sheet, line, col);
            Map<String, Object> row = combo.get(day);
            BigDecimal stored = row != null && row.get(field) instanceof BigDecimal value ? value : null;
            if (same(excel, stored, COMPARE_TOLERANCE)) continue;
            different += 1;
            if (example == null) example = day + "일 엑셀 " + fmt(excel) + " / 화면 " + fmt(stored);
        }
        if (different > 0) {
            run.warnings.add("운영내역 '" + label + "': " + different + "일치가 복합보일러 생산 데이터(스팀 생산 화면)와 다릅니다 (예: "
                    + example + "). 이 열은 운영내역에서 저장하지 않으므로 화면에는 복합보일러 쪽 값이 보입니다.");
        }
    }

    // ───────────────────────── 월별 운영내역 ─────────────────────────

    private void importMonthly(Workbook workbook, Run run) {
        Sheet sheet = SrfInvoiceSheets.monthlySheet(workbook);
        if (sheet == null) {
            run.warnings.add("'월별 운영내역' 시트가 없어 건너뛰었습니다.");
            return;
        }
        int fiscalYear = SrfInvoiceSheets.fiscalYearOf(run.year, run.month);
        Monthly layout = SrfInvoiceSheets.monthly(sheet, fiscalYear, false);
        if (layout.blockRow < 0) {
            run.warnings.add("월별 운영내역: '" + fiscalYear + "년도' 표를 찾지 못해 건너뛰었습니다 (시트에 있는 표: "
                    + (layout.blocks.isEmpty() ? "없음" : joinYears(layout.blocks.keySet())) + ").");
            return;
        }
        List<String> missing = SrfInvoiceSheets.MONTHLY_FIELDS.stream().filter(f -> !layout.cols.containsKey(f)).toList();
        if (!missing.isEmpty()) {
            run.warnings.add("월별 운영내역: 머리글을 찾지 못해 건너뛴 칸 — " + String.join(", ", missing));
        }
        Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
        for (int year : List.of(fiscalYear, fiscalYear + 1)) {
            for (Map<String, Object> row : monthlyTable.load(year, null)) existing.put(row.get("yearNo") + "|" + row.get("monthNo"), row);
        }
        Map<Integer, List<Map<String, Object>>> byYear = new LinkedHashMap<>();
        layout.monthRows.forEach((month, line) -> {
            int year = SrfInvoiceSheets.calendarYear(fiscalYear, month);
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, Object> before = existing.get(year + "|" + month);
            if (before != null) row.putAll(before);
            row.put("yearNo", year);
            row.put("monthNo", month);
            layout.cols.forEach((field, col) -> {
                BigDecimal value = number(sheet, line, col);
                row.put(field, value);
                run.count("monthly", value);
            });
            byYear.computeIfAbsent(year, k -> new ArrayList<>()).add(row);
        });
        byYear.forEach((year, rows) -> monthlyTable.saveBulk(year, null, rows, false));
        List<Integer> others = layout.blocks.keySet().stream().filter(y -> y != fiscalYear).toList();
        if (!others.isEmpty()) {
            run.warnings.add("월별 운영내역: 이번 기간의 회계연도(" + fiscalYear + "년도, " + fiscalYear + "년 6월~" + (fiscalYear + 1)
                    + "년 5월) 표만 반영했습니다. 건너뛴 표 — " + joinYears(others));
        }
        if (layout.deNoxTable) {
            run.warnings.add("월별 운영내역: '※ De-Nox설비 운영비' 표는 화면에 칸이 없어 건너뛰었습니다.");
        }
    }

    private static String joinYears(Iterable<Integer> years) {
        List<String> names = new ArrayList<>();
        for (int year : years) names.add(year + "년도");
        return String.join(", ", names);
    }

    // ───────────────────────── Accumulated steam volume ─────────────────────────

    private void importAccumulated(Workbook workbook, Run run) {
        Sheet sheet = SrfInvoiceSheets.accumulatedSheet(workbook);
        if (sheet == null) {
            run.warnings.add("'Accumulated steam volume' 시트가 없어 건너뛰었습니다.");
            return;
        }
        Accumulated layout = SrfInvoiceSheets.accumulated(sheet);
        if (!layout.found() || layout.monthRows.isEmpty()) {
            run.warnings.add("Accumulated steam volume: 올해 표(Contract Supply Volume · BSC · ASC)를 찾지 못해 건너뛰었습니다.");
            return;
        }
        int fiscalYear = SrfInvoiceSheets.fiscalYearOf(run.year, run.month);
        if (layout.labelledYear != null && layout.labelledYear != fiscalYear) {
            run.warnings.add("Accumulated steam volume: 표의 YEAR 칸은 " + layout.labelledYear + " 이지만 인보이스 기간("
                    + run.year + "년 " + run.month + "월)이 속한 회계연도 " + fiscalYear + "년 6월~" + (fiscalYear + 1)
                    + "년 5월로 반영했습니다 — YEAR 칸을 확인해 주세요.");
        }
        Map<Integer, List<Map<String, Object>>> byYear = new LinkedHashMap<>();
        layout.monthRows.forEach((month, line) -> {
            int year = SrfInvoiceSheets.calendarYear(fiscalYear, month);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("yearNo", year);
            row.put("monthNo", month);
            row.put("contractVolume", number(sheet, line, layout.contractCol));
            row.put("bscVolume", number(sheet, line, layout.bscCol));
            row.put("ascVolume", number(sheet, line, layout.ascCol));
            for (String field : List.of("contractVolume", "bscVolume", "ascVolume")) run.count("accumulated", row.get(field));
            byYear.computeIfAbsent(year, k -> new ArrayList<>()).add(row);
        });
        byYear.forEach((year, rows) -> accumulatedTable.saveBulk(year, null, rows, false));
        if (layout.historyBlocks > 0) {
            run.warnings.add("Accumulated steam volume: 아래쪽 과거 연도 표 " + layout.historyBlocks
                    + "개는 화면에 칸이 없어 건너뛰었습니다.");
        }
    }

    // ───────────────────────── BSC · Adjustment Factor ─────────────────────────

    private void importBsc(Workbook workbook, Run run) {
        Sheet sheet = SrfInvoiceSheets.bscSheet(workbook);
        if (sheet == null) {
            run.warnings.add("'BSC' 시트가 없어 건너뛰었습니다.");
            return;
        }
        int[] rateCell = SrfInvoiceSheets.bscRateCell(sheet);
        if (rateCell == null) {
            run.warnings.add("BSC: 'Base Service Charge Rate (Won/ton)' 칸을 찾지 못해 건너뛰었습니다.");
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("yearNo", run.year);
        row.put("bscRate", number(sheet, rateCell[0], rateCell[1]));
        run.count("bsc", row.get("bscRate"));
        bscTable.saveBulk(run.year, null, List.of(row), false);
    }

    private void importAdjustment(Workbook workbook, Run run) {
        Sheet sheet = SrfInvoiceSheets.adjustmentSheet(workbook);
        if (sheet == null) {
            run.warnings.add("'Adjustment Factor' 시트가 없어 건너뛰었습니다.");
            return;
        }
        Adjustment layout = SrfInvoiceSheets.adjustment(sheet);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String section : SrfInvoiceSheets.ADJUSTMENT_SECTIONS) {
            Map<String, Integer> items = layout.rows.get(section);
            int[] cols = layout.cols.get(section);
            if (items == null || cols == null || items.isEmpty()) {
                run.warnings.add("Adjustment Factor: '" + section + "' 표를 찾지 못해 건너뛰었습니다.");
                continue;
            }
            items.forEach((code, line) -> {
                if (!ADJUSTMENT_ITEMS.get(section).contains(code)) {
                    run.warnings.add("Adjustment Factor: '" + section + "' 표의 항목 '" + code + "' 는 화면에 칸이 없어 건너뛰었습니다.");
                    return;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("yearNo", run.year);
                row.put("section", section);
                row.put("itemCode", code);
                row.put("coefficient", number(sheet, line, cols[0]));
                row.put("baseValue", number(sheet, line, cols[1]));
                row.put("presentValue", number(sheet, line, cols[2]));
                for (String field : List.of("coefficient", "baseValue", "presentValue")) run.count("adjustment", row.get(field));
                rows.add(row);
            });
        }
        if (!rows.isEmpty()) adjustmentTable.saveBulk(run.year, null, rows, false);
    }

    // ───────────────────────── 사고이력 ─────────────────────────

    /**
     * 사고이력 — 다시 올려도 줄이 늘지 않게 넣는다.
     * 같은 해의 SRF 사고 기록 중 (일자, 사유)가 같은 것이 있으면 그 기록을 고치고,
     * 없으면 엑셀의 No. 로 만든 키({@code srf-accident-record:xlsx-N})로 넣는다(다시 올리면 같은 키를 덮어쓴다).
     */
    private void importIncident(Workbook workbook, Run run) {
        Sheet sheet = SrfInvoiceSheets.incidentSheet(workbook);
        if (sheet == null) {
            run.warnings.add("'NN년 사고이력' 시트가 없어 건너뛰었습니다.");
            return;
        }
        Incident layout = SrfInvoiceSheets.incident(sheet);
        if (!layout.found()) {
            run.warnings.add("사고이력: 머리글(No. · 일자 · 사유)을 찾지 못해 건너뛰었습니다.");
            return;
        }
        int year = layout.year == null ? run.year : layout.year;
        if (year != run.year) {
            run.warnings.add("사고이력: 시트 이름의 연도(" + year + ")가 인보이스 기간의 연도(" + run.year + ")와 다릅니다 — "
                    + year + "년 사고이력으로 반영했습니다.");
        }
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : incidentTable.load(year, null)) {
            String recordKey = String.valueOf(row.get("recordKey"));
            if (recordKey.startsWith(INCIDENT_PREFIX)) byKey.put(recordKey, row);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> used = new java.util.HashSet<>();
        List<Integer> emptied = new ArrayList<>();
        layout.rows.forEach((no, line) -> {
            String date = string(sheet, line, layout.dateCol);
            String reason = string(sheet, line, layout.reasonCol);
            String action = layout.actionCol == null ? null : string(sheet, line, layout.actionCol);
            String downtime = layout.downtimeCol == null ? null : string(sheet, line, layout.downtimeCol);
            String loss = layout.lossCol == null ? null : string(sheet, line, layout.lossCol);
            String note = layout.noteCol == null ? null : string(sheet, line, layout.noteCol);
            String xlsxKey = INCIDENT_PREFIX + "xlsx-" + no;
            if (date == null && reason == null && action == null && downtime == null && loss == null && note == null) {
                if (byKey.containsKey(xlsxKey)) emptied.add(no);
                return;
            }
            Map<String, Object> match = null;
            for (Map<String, Object> candidate : byKey.values()) {
                if (used.contains(String.valueOf(candidate.get("recordKey")))) continue;
                if (key(str(candidate.get("occurDate"))).equals(key(date)) && key(str(candidate.get("reason"))).equals(key(reason))) {
                    match = candidate;
                    break;
                }
            }
            if (match == null && !used.contains(xlsxKey)) match = byKey.get(xlsxKey);
            Map<String, Object> row = new LinkedHashMap<>();
            if (match != null) row.putAll(match);
            row.put("yearNo", year);
            row.putIfAbsent("recordKey", xlsxKey);
            row.put("facility", "SRF");
            row.put("kind", "ACCIDENT");
            row.put("occurDate", limit(date, 100));
            row.put("reason", limit(reason, 1000));
            row.put("actionTaken", limit(action, 1000));
            row.put("downtimeHours", keepIfSameNumber(row.get("downtimeHours"), limit(downtime, 100)));
            row.put("lossAmount", keepIfSameNumber(row.get("lossAmount"), limit(loss, 100)));
            row.put("note", limit(note, 500));
            if (row.get("sortOrder") == null) row.put("sortOrder", no);
            used.add(String.valueOf(row.get("recordKey")));
            for (String field : List.of("occurDate", "reason", "actionTaken", "downtimeHours", "lossAmount", "note")) {
                run.count("incident", row.get(field));
            }
            rows.add(row);
        });
        if (!rows.isEmpty()) incidentTable.saveBulk(year, null, rows, false);
        else run.counts.putIfAbsent("incident", 0);
        if (!emptied.isEmpty()) {
            run.warnings.add("사고이력: 전에 엑셀로 올린 " + emptied + "번 줄이 이번 파일에서는 비어 있습니다 — "
                    + "지워야 하면 사고이력 화면에서 직접 지워 주세요.");
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 저장된 글자("10.50")와 엑셀 값("10.5")이 같은 숫자면 저장된 글자를 그대로 둔다. */
    private static Object keepIfSameNumber(Object stored, String incoming) {
        if (stored == null || incoming == null) return incoming;
        try {
            if (new BigDecimal(String.valueOf(stored).replace(",", "").trim()).compareTo(new BigDecimal(incoming)) == 0) return stored;
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 엑셀 글자를 쓴다
        }
        return incoming;
    }

    // ───────────────────────── 공용 ─────────────────────────

    private static BigDecimal nz(Object value) {
        return value instanceof BigDecimal number ? number : BigDecimal.ZERO;
    }

    /** 빈칸은 0 으로 보고 비교한다. */
    static boolean same(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        return nz(a).subtract(nz(b)).abs().compareTo(tolerance) < 0;
    }

    private static String fmt(BigDecimal value) {
        return value == null ? "없음" : new DecimalFormat("#,##0.######").format(value);
    }
}
