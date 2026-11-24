package com.company.module.steamenergy.legacy.service;

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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.cell;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.cellOrCreate;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.isFormula;
import static com.company.module.steamenergy.legacy.service.SrfInvoiceSheets.normalize;

/**
 * SRF 보일러 인보이스 통합본 내보내기 — 원본 서식({@code 원본/4. KleanNara SRF Boiler AOT invoice(…).xls})을 DB 값으로 채운다.
 *
 * <p>자리는 가져오기와 같은 {@link SrfInvoiceSheets} 가 글자로 찾는다. 값은 <b>수식이 아닌 칸</b>에만 쓴다.
 * 화면의 입력 칸이 서식에서는 수식인 경우(예: Summary 의 BASE SERVICE CHARGE {@code =BSC!E11},
 * 월별 운영내역 이번 달 줄의 {@code ='Client Invoice'!D9})는 수식을 계산해 보고 DB 값과 같으면 수식을 그대로 두고,
 * 다를 때만 DB 값으로 바꿔 쓴다 — 내려받은 파일을 다시 올려도 DB 가 바뀌지 않게 하기 위해서다.
 * Summary 의 Client Invoice 연동 칸은 DB 가 비어 있으면(연동) 수식을 두고, 직접 넣은 값이 있으면 그 값을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class SrfInvoiceExcelExportService {

    private static final String FILE_PREFIX = "4.";

    private final SteamSrfInvoiceSummaryAdapter summaryTable;
    private final SteamSrfInvoiceClientItemAdapter clientTable;
    private final SteamSrfInvoiceOperationDailyAdapter operationTable;
    private final SteamSrfInvoiceMonthlyAdapter monthlyTable;
    private final SteamSrfInvoiceAccumulatedAdapter accumulatedTable;
    private final SteamSrfInvoiceBscAdapter bscTable;
    private final SteamSrfInvoiceAdjustmentAdapter adjustmentTable;
    private final SteamIncidentAdapter incidentTable;
    private final SteamComboDailyProductionAdapter comboTable;

    private volatile Path cachedTemplate;

    public String fileName(int year, int month) {
        return "KleanNara SRF Boiler AOT invoice(" + year + "년 " + month + "월).xls";
    }

    public byte[] exportWorkbook(int year, int month) throws IOException {
        Path template = resolveTemplate();
        try (InputStream input = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(input);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill(workbook, year, month);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private Path resolveTemplate() throws IOException {
        Path current = cachedTemplate;
        if (current != null && Files.exists(current)) return current;
        // 작업 디렉터리에 기대지 않고 '원본' 폴더를 찾아 올라간다 (OriginalWorkbookFiles 주석 참고)
        Path resolved = OriginalWorkbookFiles.find(
                name -> name.startsWith(FILE_PREFIX) && name.endsWith(".xls"), "SRF 보일러 인보이스");
        cachedTemplate = resolved;
        return resolved;
    }

    /** 서식 통합본에 그 달의 DB 값을 채운다 (시트가 없으면 그 시트만 건너뛴다). */
    void fill(Workbook workbook, int year, int month) {
        Filler filler = new Filler(workbook);
        fillSummary(workbook, filler, year, month);
        fillClient(workbook, filler, year, month);
        fillOperation(workbook, filler, year, month);
        fillMonthly(workbook, filler, year, month);
        fillAccumulated(workbook, filler, year, month);
        fillBsc(workbook, filler, year);
        fillAdjustment(workbook, filler, year);
        fillIncident(workbook, filler, year);
        filler.finish();
    }

    // ───────────────────────── 칸 쓰기 ─────────────────────────

    /** 수식이 아닌 칸은 바로 쓰고, 수식 칸은 모아 두었다가 계산값과 비교해 정한다. */
    private static final class Filler {
        private record Pending(Cell cell, BigDecimal value, boolean linked) {}

        private final Workbook workbook;
        private final List<Pending> pending = new ArrayList<>();

        Filler(Workbook workbook) {
            this.workbook = workbook;
        }

        void number(Sheet sheet, int row, int col, BigDecimal value) {
            number(sheet, row, col, value, false);
        }

        /** @param linked 비어 있으면 서식의 수식(다른 시트 연동)을 그대로 두는 칸 */
        void number(Sheet sheet, int row, int col, BigDecimal value, boolean linked) {
            Cell existing = cell(sheet, row, col);
            if (existing == null && value == null) return;
            Cell target = cellOrCreate(sheet, row, col);
            if (isFormula(target)) {
                pending.add(new Pending(target, normalize(value), linked));
                return;
            }
            write(target, value);
        }

        void text(Sheet sheet, int row, int col, String value) {
            Cell existing = cell(sheet, row, col);
            if (existing == null && value == null) return;
            Cell target = cellOrCreate(sheet, row, col);
            if (isFormula(target)) target.setBlank();
            if (value == null) target.setBlank();
            else target.setCellValue(value);
        }

        void date(Sheet sheet, int row, int col, LocalDate value) {
            Cell target = cellOrCreate(sheet, row, col);
            if (!isFormula(target)) target.setCellValue(value);
        }

        private static void write(Cell target, BigDecimal value) {
            if (value == null) target.setBlank();
            else target.setCellValue(value.doubleValue());
        }

        void finish() {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.setIgnoreMissingWorkbooks(true);
            List<Pending> replace = new ArrayList<>();
            for (Pending p : pending) {
                if (p.linked()) {
                    if (p.value() != null) replace.add(p);   // 직접 넣은 값 — 연동 수식 대신 쓴다
                    continue;
                }
                BigDecimal evaluated = p.value() == null ? null : evaluate(evaluator, p.cell());
                if (evaluated == null || evaluated.compareTo(p.value()) != 0) replace.add(p);
            }
            for (Pending p : replace) {
                // 수식 지우기 — HSSF 는 removeFormula() 뒤에 빈칸으로 바꾸면 예외가 나므로 빈칸으로 바꿔 수식을 지운다
                p.cell().setBlank();
                if (p.value() != null) p.cell().setCellValue(p.value().doubleValue());
            }
            // 저장된 계산 결과를 새 값에 맞춘다 — 다시 올릴 때는 수식 칸의 저장된 결과를 읽는다
            evaluator.clearAllCachedResultValues();
            for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
                for (Row row : workbook.getSheetAt(i)) {
                    for (Cell c : row) {
                        if (!isFormula(c)) continue;
                        try {
                            evaluator.evaluateFormulaCell(c);
                        } catch (RuntimeException ignored) {
                            // 다른 파일을 가리키거나 POI 가 모르는 함수 — 서식에 저장된 결과를 그대로 둔다
                        }
                    }
                }
            }
            workbook.setForceFormulaRecalculation(true);
        }

        private static BigDecimal evaluate(FormulaEvaluator evaluator, Cell target) {
            try {
                CellValue value = evaluator.evaluate(target);
                if (value == null || value.getCellType() != CellType.NUMERIC) return null;
                double number = value.getNumberValue();
                return Double.isFinite(number) ? normalize(BigDecimal.valueOf(number)) : null;
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    private static BigDecimal bd(Object value) {
        return value instanceof BigDecimal number ? number : null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // ───────────────────────── Summary(VK) · Client Invoice ─────────────────────────

    private void fillSummary(Workbook workbook, Filler filler, int year, int month) {
        Sheet sheet = SrfInvoiceSheets.summarySheet(workbook);
        if (sheet == null) return;
        Summary layout = SrfInvoiceSheets.summary(sheet);
        YearMonth period = YearMonth.of(year, month);
        if (layout.periodRow >= 0 && layout.periodStartCol >= 0) filler.date(sheet, layout.periodRow, layout.periodStartCol, period.atDay(1));
        if (layout.periodRow >= 0 && layout.periodEndCol >= 0) filler.date(sheet, layout.periodRow, layout.periodEndCol, period.atEndOfMonth());
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map<String, Object> stored : summaryTable.load(year, month)) row.putAll(stored);
        for (String field : SrfInvoiceSheets.SUMMARY_FIELDS) {
            Integer line = layout.rows.get(field);
            if (line == null) continue;
            filler.number(sheet, line, layout.colOf(field), bd(row.get(field)),
                    SrfInvoiceExcelImportService.CLIENT_LINKED.contains(field));
        }
    }

    private void fillClient(Workbook workbook, Filler filler, int year, int month) {
        Sheet sheet = SrfInvoiceSheets.clientSheet(workbook);
        if (sheet == null) return;
        Client layout = SrfInvoiceSheets.client(sheet);
        if (!layout.found()) return;
        YearMonth period = YearMonth.of(year, month);
        if (layout.periodRow >= 0) {
            filler.date(sheet, layout.periodRow, layout.periodStartCol, period.atDay(1));
            filler.date(sheet, layout.periodRow, layout.periodEndCol, period.atEndOfMonth());
        }
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        for (Map<String, Object> row : clientTable.load(year, month)) stored.put(row.get("section") + "|" + row.get("lineNo"), row);
        for (String section : List.of(SrfInvoiceSheets.UTILITIES, SrfInvoiceSheets.OTHER_OUT, SrfInvoiceSheets.OTHER_IN)) {
            List<Integer> lines = layout.lines.get(section);
            if (lines == null) continue;
            int count = Math.min(lines.size(), SrfInvoiceSheets.CLIENT_CAPACITY.get(section));
            for (int i = 0; i < count; i += 1) {
                int line = lines.get(i);
                Map<String, Object> row = stored.getOrDefault(section + "|" + i, Map.of());
                filler.number(sheet, line, layout.qtyCol, bd(row.get("qty")));
                filler.number(sheet, line, layout.priceCol, bd(row.get("unitPrice")));
                if (layout.noteCol != null) filler.text(sheet, line, layout.noteCol, str(row.get("note")));
                if (!SrfInvoiceSheets.UTILITIES.equals(section)) {
                    if (layout.labelCol != null) filler.text(sheet, line, layout.labelCol, str(row.get("itemLabel")));
                    if (layout.unitCol != null) filler.text(sheet, line, layout.unitCol, str(row.get("unitLabel")));
                }
            }
        }
    }

    // ───────────────────────── 운영내역 ─────────────────────────

    private void fillOperation(Workbook workbook, Filler filler, int year, int month) {
        Sheet sheet = SrfInvoiceSheets.operationSheet(workbook);
        if (sheet == null) return;
        Operation layout = SrfInvoiceSheets.operation(sheet);
        if (layout.headerRow < 0) return;
        if (layout.titleCell != null) {
            String title = SrfInvoiceSheets.text(sheet, layout.titleCell[0], layout.titleCell[1]);
            filler.text(sheet, layout.titleCell[0], layout.titleCell[1],
                    title.replaceFirst("\\d{4}\\s*년\\s*\\d{1,2}\\s*월", year + "년 " + month + "월"));
        }
        Map<Integer, BigDecimal> power = new LinkedHashMap<>();
        for (Map<String, Object> row : operationTable.load(year, month)) {
            if (row.get("usageDate") instanceof LocalDate date) power.put(date.getDayOfMonth(), bd(row.get("powerKwh")));
        }
        // 스팀생산량 · 공정수 · 탈기기급수량은 운영내역 화면과 같이 복합보일러 생산 데이터에서 가져온다
        Map<Integer, Map<String, Object>> combo = new LinkedHashMap<>();
        for (Map<String, Object> row : comboTable.load(year, month)) {
            if (row.get("usageDate") instanceof LocalDate date) combo.put(date.getDayOfMonth(), row);
        }
        int days = YearMonth.of(year, month).lengthOfMonth();
        layout.dayRows.forEach((day, line) -> {
            boolean inMonth = day <= days;
            Map<String, Object> production = inMonth ? combo.getOrDefault(day, Map.of()) : Map.of();
            if (layout.powerCol != null) filler.number(sheet, line, layout.powerCol, inMonth ? power.get(day) : null);
            if (layout.steamCol != null) filler.number(sheet, line, layout.steamCol, bd(production.get("comboSteam")));
            if (layout.processCol != null) filler.number(sheet, line, layout.processCol, bd(production.get("comboProcess")));
            if (layout.deaeratorCol != null) filler.number(sheet, line, layout.deaeratorCol, bd(production.get("comboDeaerator")));
            Cell dateCell = cell(sheet, line, layout.dateCol);
            if (day == 1 && dateCell != null && dateCell.getCellType() == CellType.STRING
                    && dateCell.getStringCellValue().contains("월")) {
                dateCell.setCellValue(month + "월 1일");
            }
        });
    }

    // ───────────────────────── 월별 운영내역 · Accumulated ─────────────────────────

    private void fillMonthly(Workbook workbook, Filler filler, int year, int month) {
        Sheet sheet = SrfInvoiceSheets.monthlySheet(workbook);
        if (sheet == null) return;
        int fiscalYear = SrfInvoiceSheets.fiscalYearOf(year, month);
        Monthly layout = SrfInvoiceSheets.monthly(sheet, fiscalYear, true);
        if (layout.blockRow < 0) return;
        // 서식에 그 회계연도 표가 없으면 맨 위 표를 그 연도로 바꿔 쓴다
        filler.text(sheet, layout.blockRow, layout.labelCol, fiscalYear + "년도");
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        for (int y : List.of(fiscalYear, fiscalYear + 1)) {
            for (Map<String, Object> row : monthlyTable.load(y, null)) stored.put(row.get("yearNo") + "|" + row.get("monthNo"), row);
        }
        layout.monthRows.forEach((m, line) -> {
            Map<String, Object> row = stored.getOrDefault(SrfInvoiceSheets.calendarYear(fiscalYear, m) + "|" + m, Map.of());
            layout.cols.forEach((field, col) -> filler.number(sheet, line, col, bd(row.get(field))));
            // 지급액 = 스팀구매량 × 스팀단가 (화면의 계산과 같다) — 서식에서 수식인 달은 그대로 둔다
            if (layout.paidCol != null && !isFormula(cell(sheet, line, layout.paidCol))) {
                BigDecimal qty = bd(row.get("steamQty"));
                BigDecimal unit = bd(row.get("steamUnit"));
                filler.number(sheet, line, layout.paidCol,
                        qty == null || unit == null ? null : qty.multiply(unit).setScale(0, RoundingMode.HALF_UP));
            }
            Cell label = cell(sheet, line, layout.labelCol);
            if (m == 1 && label != null && label.getCellType() == CellType.STRING && label.getStringCellValue().contains("년")) {
                label.setCellValue("'" + String.format("%02d", (fiscalYear + 1) % 100) + "년 1월");
            }
        });
    }

    private void fillAccumulated(Workbook workbook, Filler filler, int year, int month) {
        Sheet sheet = SrfInvoiceSheets.accumulatedSheet(workbook);
        if (sheet == null) return;
        Accumulated layout = SrfInvoiceSheets.accumulated(sheet);
        if (!layout.found()) return;
        int fiscalYear = SrfInvoiceSheets.fiscalYearOf(year, month);
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        for (int y : List.of(fiscalYear, fiscalYear + 1)) {
            for (Map<String, Object> row : accumulatedTable.load(y, null)) stored.put(row.get("yearNo") + "|" + row.get("monthNo"), row);
        }
        layout.monthRows.forEach((m, line) -> {
            int calendarYear = SrfInvoiceSheets.calendarYear(fiscalYear, m);
            if (layout.yearCol != null && (m == 6 || m == 1)) {
                filler.number(sheet, line, layout.yearCol, BigDecimal.valueOf(calendarYear));
            }
            Map<String, Object> row = stored.getOrDefault(calendarYear + "|" + m, Map.of());
            filler.number(sheet, line, layout.contractCol, bd(row.get("contractVolume")));
            filler.number(sheet, line, layout.bscCol, bd(row.get("bscVolume")));
            filler.number(sheet, line, layout.ascCol, bd(row.get("ascVolume")));
        });
    }

    // ───────────────────────── BSC · Adjustment Factor ─────────────────────────

    private void fillBsc(Workbook workbook, Filler filler, int year) {
        Sheet sheet = SrfInvoiceSheets.bscSheet(workbook);
        if (sheet == null) return;
        int[] rateCell = SrfInvoiceSheets.bscRateCell(sheet);
        if (rateCell == null) return;
        BigDecimal rate = null;
        for (Map<String, Object> row : bscTable.load(year, null)) rate = bd(row.get("bscRate"));
        filler.number(sheet, rateCell[0], rateCell[1], rate);
    }

    private void fillAdjustment(Workbook workbook, Filler filler, int year) {
        Sheet sheet = SrfInvoiceSheets.adjustmentSheet(workbook);
        if (sheet == null) return;
        Adjustment layout = SrfInvoiceSheets.adjustment(sheet);
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        for (Map<String, Object> row : adjustmentTable.load(year, null)) stored.put(row.get("section") + "|" + row.get("itemCode"), row);
        layout.rows.forEach((section, items) -> {
            int[] cols = layout.cols.get(section);
            items.forEach((code, line) -> {
                if (!SrfInvoiceExcelImportService.ADJUSTMENT_ITEMS.get(section).contains(code)) return;
                Map<String, Object> row = stored.getOrDefault(section + "|" + code, Map.of());
                filler.number(sheet, line, cols[0], bd(row.get("coefficient")));
                filler.number(sheet, line, cols[1], bd(row.get("baseValue")));
                filler.number(sheet, line, cols[2], bd(row.get("presentValue")));
            });
        });
    }

    // ───────────────────────── 사고이력 ─────────────────────────

    private void fillIncident(Workbook workbook, Filler filler, int year) {
        Sheet sheet = SrfInvoiceSheets.incidentSheet(workbook);
        if (sheet == null) return;
        Incident layout = SrfInvoiceSheets.incident(sheet);
        if (!layout.found()) return;
        String yy = String.format("%02d", year % 100);
        String sheetName = yy + "년 사고이력";
        if (!sheetName.equals(sheet.getSheetName()) && workbook.getSheet(sheetName) == null) {
            workbook.setSheetName(workbook.getSheetIndex(sheet), sheetName);
        }
        if (layout.titleCell != null) {
            String title = SrfInvoiceSheets.text(sheet, layout.titleCell[0], layout.titleCell[1]);
            filler.text(sheet, layout.titleCell[0], layout.titleCell[1], title.replaceFirst("\\d{2,4}\\s*년", yy + "년"));
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : incidentTable.load(year, null)) {
            if (String.valueOf(row.get("recordKey")).startsWith(SrfInvoiceExcelImportService.INCIDENT_PREFIX)) records.add(row);
        }
        records.sort(Comparator
                .comparing((Map<String, Object> row) -> row.get("sortOrder") instanceof Integer order ? order : Integer.MAX_VALUE)
                .thenComparing(row -> String.valueOf(row.get("recordKey"))));
        int index = 0;
        for (int line : layout.rows.values()) {
            Map<String, Object> row = index < records.size() ? records.get(index) : Map.of();
            index += 1;
            filler.text(sheet, line, layout.dateCol, str(row.get("occurDate")));
            filler.text(sheet, line, layout.reasonCol, str(row.get("reason")));
            if (layout.actionCol != null) filler.text(sheet, line, layout.actionCol, str(row.get("actionTaken")));
            if (layout.downtimeCol != null) numberOrText(filler, sheet, line, layout.downtimeCol, str(row.get("downtimeHours")));
            if (layout.lossCol != null) numberOrText(filler, sheet, line, layout.lossCol, str(row.get("lossAmount")));
            if (layout.noteCol != null) filler.text(sheet, line, layout.noteCol, str(row.get("note")));
        }
    }

    /** 운휴시간 · 손실금액은 글자로 저장돼 있다 — 숫자로 읽히면 숫자로 써서 합계 수식이 살게 한다. */
    private static void numberOrText(Filler filler, Sheet sheet, int row, int col, String value) {
        if (value != null && !value.isBlank()) {
            try {
                filler.number(sheet, row, col, new BigDecimal(value.replace(",", "").trim()));
                return;
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 글자 그대로
            }
        }
        filler.text(sheet, row, col, value == null || value.isBlank() ? null : value);
    }
}
