package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceExcelImportResponse;
import com.company.module.steamenergy.service.table.SteamComboDailyProductionAdapter;
import com.company.module.steamenergy.service.table.SteamIncidentAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceAccumulatedAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceAdjustmentAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceBscAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceClientItemAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceMonthlyAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceOperationDailyAdapter;
import com.company.module.steamenergy.service.table.SteamSrfInvoiceSummaryAdapter;
import com.company.module.steamenergy.service.table.SteamTableAdapter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SRF 보일러 인보이스 통합본 가져오기/내보내기 — 옛 양식(2025)과 새 양식(2026, 줄·열이 밀린 것) 모두 글자로 자리를 찾는지,
 * 빈칸이 저장값을 비우는지, 기간이 다르면 막는지, 내려받아 다시 올려도 값이 그대로인지.
 */
class SrfInvoiceExcelTest {

    private SteamSrfInvoiceSummaryAdapter summaryTable;
    private SteamSrfInvoiceClientItemAdapter clientTable;
    private SteamSrfInvoiceOperationDailyAdapter operationTable;
    private SteamSrfInvoiceMonthlyAdapter monthlyTable;
    private SteamSrfInvoiceAccumulatedAdapter accumulatedTable;
    private SteamSrfInvoiceBscAdapter bscTable;
    private SteamSrfInvoiceAdjustmentAdapter adjustmentTable;
    private SteamIncidentAdapter incidentTable;
    private SteamComboDailyProductionAdapter comboTable;

    private Map<String, Map<String, Object>> summary, client, operation, monthly, accumulated, bsc, adjustment, incident, combo;

    private SrfInvoiceExcelImportService importer;
    private SrfInvoiceExcelExportService exporter;

    @BeforeEach
    void setUp() {
        summaryTable = mock(SteamSrfInvoiceSummaryAdapter.class);
        clientTable = mock(SteamSrfInvoiceClientItemAdapter.class);
        operationTable = mock(SteamSrfInvoiceOperationDailyAdapter.class);
        monthlyTable = mock(SteamSrfInvoiceMonthlyAdapter.class);
        accumulatedTable = mock(SteamSrfInvoiceAccumulatedAdapter.class);
        bscTable = mock(SteamSrfInvoiceBscAdapter.class);
        adjustmentTable = mock(SteamSrfInvoiceAdjustmentAdapter.class);
        incidentTable = mock(SteamIncidentAdapter.class);
        comboTable = mock(SteamComboDailyProductionAdapter.class);
        summary = wire(summaryTable, "yearNo", "monthNo");
        client = wire(clientTable, "yearNo", "monthNo", "section", "lineNo");
        operation = wire(operationTable, "usageDate");
        monthly = wire(monthlyTable, "yearNo", "monthNo");
        accumulated = wire(accumulatedTable, "yearNo", "monthNo");
        bsc = wire(bscTable, "yearNo");
        adjustment = wire(adjustmentTable, "yearNo", "section", "itemCode");
        incident = wire(incidentTable, "yearNo", "recordKey");
        combo = wire(comboTable, "usageDate");
        importer = new SrfInvoiceExcelImportService(summaryTable, clientTable, operationTable, monthlyTable,
                accumulatedTable, bscTable, adjustmentTable, incidentTable, comboTable);
        exporter = new SrfInvoiceExcelExportService(summaryTable, clientTable, operationTable, monthlyTable,
                accumulatedTable, bscTable, adjustmentTable, incidentTable, comboTable);
    }

    // ───────────────────────── 메모리 표 (어댑터 대역) ─────────────────────────

    /** 어댑터 mock 을 메모리 맵에 잇는다 — saveBulk 는 행 전체 업서트, load 는 연(월) 범위. */
    private static Map<String, Map<String, Object>> wire(SteamTableAdapter adapter, String... keyFields) {
        Map<String, Map<String, Object>> store = new TreeMap<>();
        when(adapter.load(anyInt(), any())).thenAnswer(call -> {
            int year = call.getArgument(0);
            Integer month = call.getArgument(1);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : store.values()) {
                if (row.get("usageDate") instanceof LocalDate date) {
                    if (date.getYear() == year && (month == null || date.getMonthValue() == month)) rows.add(new LinkedHashMap<>(row));
                } else if (Integer.valueOf(year).equals(row.get("yearNo"))
                        && (month == null || !row.containsKey("monthNo") || month.equals(row.get("monthNo")))) {
                    rows.add(new LinkedHashMap<>(row));
                }
            }
            return rows;
        });
        when(adapter.saveBulk(anyInt(), any(), anyList(), anyBoolean())).thenAnswer(call -> {
            List<Map<String, Object>> rows = call.getArgument(2);
            for (Map<String, Object> row : rows) {
                StringBuilder key = new StringBuilder();
                for (String field : keyFields) key.append(row.get(field)).append('|');
                store.put(key.toString(), new LinkedHashMap<>(row));
            }
            return SteamBulkSaveResponse.of(rows.size(), 0, 0);
        });
        return store;
    }

    /** 비교용 — 숫자는 꼬리 0 을 떼어 글자로. */
    private static String dump(Map<String, Map<String, Object>> store) {
        StringBuilder out = new StringBuilder();
        store.forEach((key, row) -> {
            out.append(key).append(" → ");
            new TreeMap<>(row).forEach((field, value) -> out.append(field).append('=')
                    .append(value instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString() : value).append(' '));
            out.append('\n');
        });
        return out.toString();
    }

    private static void assertNumber(String expected, Object actual) {
        assertNotNull(actual, "값이 비어 있다 (기대 " + expected + ")");
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual), "기대 " + expected + " / 실제 " + actual);
    }

    // ───────────────────────── 시험용 통합본 ─────────────────────────

    private static void put(Sheet sheet, int rowIndex, int colIndex, Object value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(colIndex);
        if (value instanceof String text && text.startsWith("=")) cell.setCellFormula(text.substring(1));
        else if (value instanceof String text) cell.setCellValue(text);
        else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            CellStyle style = sheet.getWorkbook().createCellStyle();
            style.setDataFormat(sheet.getWorkbook().getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(style);
        }
    }

    private static Cell at(Workbook workbook, String sheetName, int row, int col) {
        return workbook.getSheet(sheetName).getRow(row).getCell(col);
    }

    /**
     * 통합본을 만든다.
     *
     * @param newLayout true 면 2026년 양식 — Summary 에 "4. OTHERS (NOx)" 묶음이 끼어 아래가 8줄 밀리고,
     *                  운영내역에 둘째 줄 머리글과 De-NOx · 액체산소 열이 생기고, Client Invoice 에 4. Other Charge 묶음이 붙는다
     */
    private static Workbook workbook(boolean newLayout, int year, int month) {
        Workbook wb = new HSSFWorkbook();
        int fiscalYear = month >= 6 ? year : year - 1;
        String incidentName = String.format("%02d년 사고이력", year % 100);
        for (String name : List.of("Summary(VK)", "운영내역", "BSC", "Adjustment Factor", "Accumulated steam volume",
                "Client Invoice", "월별 운영내역", incidentName)) {
            wb.createSheet(name);
        }
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        // ── Client Invoice
        Sheet c = wb.getSheet("Client Invoice");
        put(c, 0, 0, "깨끗한 나라(KleanNara) INVOICE :");
        put(c, 0, 3, first);
        put(c, 0, 4, "~");
        put(c, 0, 5, last);
        String[] headers = {"구          분", "세부 항목", "단위", "양", "단   가", "청구금액", "비                    고"};
        for (int i = 0; i < headers.length; i += 1) put(c, 4, i, headers[i]);
        put(c, 7, 0, "총 청구 금액");
        String[] utilities = {" · Electricity (전력)", " · LNG (도시가스)", " · 보충수 (순수)", " · Process Water (공정수)", " · 약품희석수",
                " · Water (생활용수)", " · Process Water (공정수)", " · 1st Soft Water (보충수)", " · 2nd Soften Water (청수/연수)",
                " · Water (생활용수)", " ·  "};
        double[] qty = {650961, 24659, 16465, 12985, 1895, 23};
        double[] price = {178, 830, 1206, 526, 539, 1206, 346, 346, 346, 346};
        put(c, 8, 0, "1. Utilities\n깨끗한 나라 → 베올리아(비용 청구)");
        for (int i = 0; i < utilities.length; i += 1) {
            int r = 8 + i;
            put(c, r, 1, utilities[i]);
            if (i < qty.length) put(c, r, 3, qty[i]);
            if (i < price.length) put(c, r, 4, price[i]);
            put(c, r, 5, "=D" + (r + 1) + "*E" + (r + 1));
        }
        put(c, 19, 1, "Sub total");
        put(c, 19, 5, "=SUM(F9:F19)");
        put(c, 20, 0, "2. Other Charge\n깨끗한 나라 → 베올리아(비용 청구)\n");
        put(c, 20, 2, "ton");
        put(c, 20, 5, "=D21*E21");
        put(c, 21, 5, "=D22*E22");
        put(c, 22, 1, "Sub total");
        put(c, 22, 5, "=SUM(F21:F22)");
        put(c, 23, 0, "3. Other Charge\n깨끗한 나라 ← 베올리아(비용 청구)\n");
        put(c, 23, 5, "=D24*E24");
        put(c, 24, 5, "=D25*E25");
        put(c, 25, 1, "Sub total");
        put(c, 25, 5, "=SUM(F24:F25)");
        if (newLayout) {
            put(c, 26, 0, "4. Other Charge \n(O&M of NOx Reduction Facility)");
            put(c, 26, 1, " · 위탁운영 관리비용");
            put(c, 26, 3, 1);
            put(c, 26, 4, 3100000);
            put(c, 26, 5, "=D27*E27");
            put(c, 27, 1, " · 산소 ");
            put(c, 27, 3, 3687);
            put(c, 27, 4, 155);
            put(c, 27, 5, "=D28*E28");
            put(c, 28, 1, "Sub total");
            put(c, 28, 5, "=SUM(F27:F28)");
        }

        // ── BSC
        Sheet b = wb.getSheet("BSC");
        put(b, 9, 4, "Base Service Charge Rate (Won/ton)");
        put(b, 9, 7, "Guaranted Volume\n(Steam ton/year)");
        put(b, 10, 2, "DP.1 Steam");
        put(b, 10, 4, 18100);

        // ── Summary(VK) — 새 양식은 "3. OTHERS" 아래부터 8줄 밀린다
        Sheet s = wb.getSheet("Summary(VK)");
        int shift = newLayout ? 8 : 0;
        put(s, 0, 0, "MONTHLY INVOICE SUMMARY (KleanNara SRF Boiler)");
        put(s, 4, 1, "Invoice for the period");
        put(s, 4, 3, first);
        put(s, 4, 4, "to");
        put(s, 4, 5, last);
        put(s, 11, 0, "CHARGES");
        put(s, 13, 1, "1. BSC");
        put(s, 13, 3, "=(ROUND(D16*D18,0)+D20)*D22");
        put(s, 15, 1, "BASE SERVICE CHARGE");
        put(s, 15, 3, "=BSC!E11");
        put(s, 15, 4, "Won/ton steam");
        put(s, 17, 1, "ESCALATION FACTOR");
        put(s, 17, 3, newLayout ? 1.185061857826637 : 1.16530665130552);
        put(s, 19, 1, "AFBSCr - BSC");
        put(s, 19, 3, 0);
        put(s, 19, 4, "Won/ton steam");
        put(s, 21, 1, "STEAM SUPPLY AMOUNT");
        put(s, 21, 3, newLayout ? 38176 : 39000);
        put(s, 21, 4, "tons/month");
        put(s, 24, 1, "2. ASC");
        put(s, 24, 3, "=ROUND(D27*D29,0)*D31");
        put(s, 26, 1, "ADDITIONAL SERVICE CHARGE");
        put(s, 26, 3, 11500);
        put(s, 26, 4, "Won/ton steam");
        put(s, 28, 1, "ESCALATION FACTOR");
        put(s, 28, 3, newLayout ? 1.2802029186389778 : 1.2502685070462398);
        put(s, 30, 1, "STEAM SUPPLY AMOUNT");
        if (!newLayout) put(s, 30, 3, 1990);          // 새 양식은 빈칸
        put(s, 30, 4, "tons/month");
        if (newLayout) {
            put(s, 33, 1, "3. OTHERS ");
            put(s, 33, 3, "=SUM(D36:D39)");
            put(s, 35, 1, "1) shortfall payment");
            put(s, 35, 3, "=-'Client Invoice'!F24");
            put(s, 36, 1, "2) ");
            put(s, 37, 1, "3) ");
            put(s, 38, 1, "4) ");
            put(s, 40, 1, "4. OTHERS (O&M of NOx Reduction Facility)");
            put(s, 40, 3, "=SUM(D43:D44)");
            put(s, 42, 1, "1) Fixed Cost");
            put(s, 42, 3, "='Client Invoice'!F27");
            put(s, 43, 1, "2) Oxygen");
            put(s, 43, 3, "='Client Invoice'!F28");
            put(s, 47, 1, "TOTAL SERVICE CHARGES FOR THE MONTH");
            put(s, 47, 5, "=ROUNDUP(D14+D25+D34+D41,0)");
        } else {
            put(s, 33, 1, "3. OTHERS");
            put(s, 33, 3, "='Client Invoice'!F24");
            put(s, 35, 1, "1) ");
            put(s, 35, 3, "='Client Invoice'!F25");
            put(s, 36, 1, "2)");
            put(s, 37, 1, "3)");
            put(s, 39, 1, "TOTAL SERVICE CHARGES FOR THE MONTH");
            put(s, 39, 5, "=ROUNDUP(D14+D25+D34+D36,0)");
        }
        for (int r = 35; r <= 37; r += 1) put(s, r, 4, "Won");
        put(s, 39 + shift, 6, "Won");
        put(s, 41 + shift, 1, "VAT at (10%)");
        put(s, 41 + shift, 6, "Won");
        put(s, 43 + shift, 6, "Won");
        put(s, 45 + shift, 0, "TOTAL AMOUNT PAYABLE BY KLEANNARA");
        put(s, 45 + shift, 6, "Won");
        put(s, 48 + shift, 0, "DEDUCTIONS");
        put(s, 50 + shift, 1, "LIQUIDATED DAMAGE");
        if (newLayout) put(s, 50 + shift, 3, 777);
        put(s, 50 + shift, 4, "Won");
        put(s, 52 + shift, 1, "ASC REFUND(Annual)");
        put(s, 52 + shift, 4, "Won");
        put(s, 54 + shift, 1, "UTILITIES DEDUCTION COSTS");
        put(s, 54 + shift, 3, "='Client Invoice'!F20");
        put(s, 54 + shift, 4, "Won");
        put(s, 56 + shift, 1, "OTHER DEDUCTION COSTS ");
        put(s, 56 + shift, 3, "='Client Invoice'!F21");
        put(s, 56 + shift, 4, "Won");
        put(s, 58 + shift, 1, "OTHER DEDUCTION COSTS ");
        put(s, 58 + shift, 3, "='Client Invoice'!F22");
        put(s, 58 + shift, 4, "Won");
        put(s, 59 + shift, 0, "NET CASH");
        put(s, 61 + shift, 1, "VAT at (10%)");
        put(s, 63 + shift, 6, "Won");
        put(s, 65 + shift, 0, "NET CASH PAYABLE BY KLEANNARA");

        // ── 운영내역 — 새 양식은 머리글이 한 줄 더 있고 De-NOx · 액체산소 열이 있다
        Sheet o = wb.getSheet("운영내역");
        put(o, 0, 2, "2. " + year + "년 " + month + "월 보일러 운영내역");
        put(o, 2, 2, "일자");
        put(o, 2, 3, " 스팀생산량");
        put(o, 2, 5, "공정수 사용량");
        put(o, 2, 6, "보충수(순수)\n 사용량");
        put(o, 2, 7, "탈기기급수량");
        put(o, 2, 8, "급수량-보충수");
        put(o, 2, 11, "전력량");
        put(o, 3, 3, "(T/D)");
        put(o, 3, 11, "(Kw/D)");
        if (newLayout) {
            put(o, 2, 9, "약품희석수");
            put(o, 2, 14, "액체산소");
            put(o, 4, 9, "Main (A)");
            put(o, 4, 11, "Main (A)");
            put(o, 4, 12, "De-NOx \n총 사용량(B)");
            put(o, 4, 14, "입고량(A)");
        }
        int firstDay = newLayout ? 5 : 4;
        for (int day = 1; day <= 31; day += 1) {
            int r = firstDay + day - 1;
            if (day == 1) put(o, r, 2, month + "월 1일");
            else put(o, r, 2, day);
            put(o, r, 3, 1000 + day);
            put(o, r, 4, "=D" + (r + 1) + "/24");
            put(o, r, 5, 500 + day);
            put(o, r, 7, 1200 + day);
            if (day != 3) put(o, r, 11, 20000 + day + 0.00000000009);   // 3일은 빈칸
            if (newLayout) {
                put(o, r, 9, 100);
                put(o, r, 12, 5);
                put(o, r, 14, 7);
            }
        }
        put(o, firstDay + 31, 2, "누 계");
        put(o, firstDay + 31, 3, "=SUM(D" + (firstDay + 1) + ":D" + (firstDay + 31) + ")");

        // ── Adjustment Factor
        Sheet a = wb.getSheet("Adjustment Factor");
        String[][] tables = {
                {"2. Adjustment Factor for the Base Service Charge", "a", "b", "c", "d", "e", "e:bottom", "e:fly", "f", "g"},
                {"2-1. Adjustment Factor for the Base Service Charge (rebasing)", "a", "b", "c", "d", "e:bottom", "e:fly", "f", "g", "h"},
                {"3. Adjustment Factor for the Additional Service Charge", "b", "c", "d", "e", "f", "g"}};
        int ar = 6;
        for (int t = 0; t < tables.length; t += 1) {
            put(a, ar, 1, tables[t][0]);
            put(a, ar + 5, 1, "Item");
            put(a, ar + 5, 3, "Value of KleanNara");
            put(a, ar + 6, 3, "Coefficient");
            put(a, ar + 6, 4, "Base");
            put(a, ar + 6, 5, "Present");
            for (int i = 1; i < tables[t].length; i += 1) {
                int r = ar + 6 + i;
                String[] item = tables[t][i].split(":");
                put(a, r, 1, item[0]);
                put(a, r, 2, item.length > 1 ? "Waste disposal - " + (item[1].equals("bottom") ? "Bottom ash" : "Fly ash") : "Definition " + item[0]);
                put(a, r, 3, (t + 1) / 100.0 + i / 1000.0);
                if (!item[0].equals("a")) {
                    put(a, r, 4, 100 + i);
                    put(a, r, 5, 110 + i + t);
                }
            }
            put(a, ar + 6 + tables[t].length, 1, "AFBSCpy");
            ar += 20;
        }

        // ── Accumulated steam volume — YEAR 칸은 회계연도
        Sheet acc = wb.getSheet("Accumulated steam volume");
        put(acc, 2, 1, "YEAR");
        put(acc, 2, 2, "MONTH");
        put(acc, 2, 3, "Contract \nSupply Volume");
        put(acc, 2, 4, "STEAM SUPPLY VOLUME");
        put(acc, 3, 4, "BSC");
        put(acc, 3, 5, "ASC");
        put(acc, 3, 6, "Total");
        String[] months = {"JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR", "APR", "MAY"};
        for (int i = 0; i < 12; i += 1) {
            int r = 4 + i;
            if (i == 0) put(acc, r, 1, fiscalYear);
            if (i == 7) put(acc, r, 1, fiscalYear + 1);
            put(acc, r, 2, months[i]);
            put(acc, r, 3, 38000 + i * 100);
            if (i <= 4) put(acc, r, 4, i == 4 ? 38176 : 37000 + i);     // 10월까지만 실적
            if (i == 2) put(acc, r, 5, 224);
            put(acc, r, 6, "=SUM(E" + (r + 1) + ":F" + (r + 1) + ")");
        }
        put(acc, 16, 2, "TOTAL");
        put(acc, 19, 1, "YEAR");
        put(acc, 19, 2, "MONTH");
        put(acc, 19, 3, "STEAM SUPPLY VOLUME");
        put(acc, 20, 1, 2021);
        put(acc, 20, 2, "JUN");
        put(acc, 20, 3, 43896);

        // ── 월별 운영내역 — 회계연도 표 두 개
        Sheet m = wb.getSheet("월별 운영내역");
        String nth = newLayout ? "8차연도" : "7차연도";
        String[] items = {"스팀구매량", "스팀단가", "지급액", "전력사용량", "전력단가", "사용비용", "LNG사용량", "LNG단가", "사용비용",
                "공정수사용량", "처리단가", "사용비용", "청수사용량", "처리단가", "사용비용", "연수사용량", "처리단가", "사용비용",
                "생활용수", "처리단가", "사용비용", "총 계", "특약 \n미달금액\n(지급금)", nth + "\nBSC물량 정산금\n(지급금)",
                "SRF이익 \n분배금\n(차감금)", "보일러 \n검사수수료\n(차감금)", nth + " 단가\n초과 지급분\n(차감금)",
                "대기초과배출부과금\n(차감금)", "폐기물\n처분부담금\n(차감금)", "사용비용", "스팀운영단가"};
        if (newLayout) put(m, 0, 33, "※ De-Nox설비 운영비");
        int mr = 1;
        for (int block = 0; block < 2; block += 1) {
            int blockYear = fiscalYear - block;
            put(m, mr, 0, blockYear + "년도");
            put(m, mr, 1, "스팀 구매비용");
            for (int i = 0; i < items.length; i += 1) put(m, mr + 1, 1 + i, items[i]);
            for (int i = 0; i < 12; i += 1) {
                int r = mr + 2 + i;
                int mon = i <= 6 ? i + 6 : i - 6;
                put(m, r, 0, mon == 1 ? "'" + String.format("%02d", (blockYear + 1) % 100) + "년 1월" : mon + "월");
                boolean filled = block == 1 || i <= 4;      // 올해 표는 10월까지만
                if (filled) {
                    put(m, r, 1, 37000 + i + block);
                    put(m, r, 3, 800000000 + i * 1000);
                    put(m, r, 4, 600000 + i);
                    put(m, r, 7, 13000 + i);
                    put(m, r, 10, 15000 + i);
                    put(m, r, 13, 16000 + i);
                    put(m, r, 16, 1300 + i);
                    put(m, r, 19, 14 + i);
                    if (i == 2) put(m, r, 28, 553750);
                }
                put(m, r, 2, "=D" + (r + 1) + "/B" + (r + 1));
                put(m, r, 5, 178);
                put(m, r, 6, "=E" + (r + 1) + "*F" + (r + 1));
                put(m, r, 8, 830);
                put(m, r, 11, 526);
                put(m, r, 14, 1206);
                put(m, r, 17, 539);
                put(m, r, 20, 1206);
            }
            put(m, mr + 14, 0, "합계");
            mr += 18;
        }

        // ── 사고이력
        Sheet inc = wb.getSheet(incidentName);
        put(inc, 1, 1, "'" + String.format("%02d", year % 100) + "년 복합보일러 설비 사고 이력");
        String[] incHeaders = {"No.", "일 자", "사 유", "조치내역", "운휴시간(Hr)", "손실금액(백만원)", "비 고"};
        for (int i = 0; i < incHeaders.length; i += 1) put(inc, 3, 1 + i, incHeaders[i]);
        for (int no = 1; no <= 8; no += 1) put(inc, 3 + no, 1, no);
        put(inc, 4, 2, LocalDate.of(year, month, 27));
        put(inc, 4, 3, "1호기 상부 헤더 수냉벽 핀홀 발생 누수 운휴");
        put(inc, 4, 4, "누수처 용접보강");
        put(inc, 4, 5, 10.5);
        put(inc, 4, 6, 1.48299745);
        put(inc, 5, 2, "3/13~14");
        put(inc, 5, 3, "2호기 뱅크튜브존 폐색");
        put(inc, 12, 1, "빌생횟수");
        put(inc, 12, 2, "=COUNTA(C5:C12)");

        // 수식 칸에 계산 결과를 넣어 둔다 (가져오기는 저장된 결과를 읽는다)
        wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
        return wb;
    }

    private Map<String, Object> summaryRow(int year, int month) {
        return summary.get(year + "|" + month + "|");
    }

    // ───────────────────────── 가져오기 ─────────────────────────

    @Test
    @DisplayName("옛 양식(2025): 제목으로 줄을 찾아 Summary 를 넣는다 — Client Invoice 를 가리키는 수식 칸은 비워 연동을 살린다")
    void importsOldLayout() {
        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(workbook(false, 2025, 10), null, null);

        assertEquals(2025, result.getYear());
        assertEquals(10, result.getMonth());
        assertEquals(2025, result.getFiscalYear());
        Map<String, Object> row = summaryRow(2025, 10);
        assertNumber("18100", row.get("bscRate"));             // =BSC!E11 의 계산 결과
        assertNumber("1.165307", row.get("bscFactor"));        // DECIMAL(18,6)
        assertNumber("39000", row.get("bscSteamAmount"));
        assertNumber("11500", row.get("ascRate"));
        assertNumber("1990", row.get("ascSteamAmount"));
        assertNull(row.get("others0"));
        assertNull(row.get("utilitiesDeduction"));             // ='Client Invoice'!F20 — 연동값과 같다
        assertNull(row.get("liquidatedDamage"));
        assertEquals(0, result.getWorkbookTotal().compareTo(result.getScreenTotal()), "옛 양식은 화면 계산과 합계가 같다");
        assertTrue(result.getWarnings().stream().noneMatch(w -> w.contains("제목을 찾지 못해")), result.getWarnings().toString());
    }

    @Test
    @DisplayName("새 양식(2026): 8줄 밀린 DEDUCTIONS 도 제목으로 찾고, 4. OTHERS (NOx) 묶음은 건너뛰며 경고한다")
    void importsNewLayoutWithShiftedRows() {
        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(workbook(true, 2026, 10), null, null);

        Map<String, Object> row = summaryRow(2026, 10);
        assertNumber("1.185062", row.get("bscFactor"));
        assertNumber("38176", row.get("bscSteamAmount"));
        assertNumber("777", row.get("liquidatedDamage"));      // 옛 양식 r50 → 새 양식 r58
        assertNull(row.get("utilitiesDeduction"));
        List<String> warnings = result.getWarnings();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("4. OTHERS (O&M of NOx Reduction Facility)") && w.contains("3,671,485")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Client Invoice") && w.contains("4. Other Charge")), warnings.toString());
        // 합계 차이 = 건너뛴 NOx 묶음
        assertEquals(0, new BigDecimal("3671485").compareTo(result.getWorkbookTotal().subtract(result.getScreenTotal())));
    }

    @Test
    @DisplayName("Client Invoice: 구분 제목으로 줄을 찾아 유틸리티 10줄과 Other Charge 줄을 넣는다")
    void importsClientLines() {
        importer.importWorkbook(workbook(true, 2026, 10), null, null);

        assertNumber("650961", client.get("2026|10|utilities|0|").get("qty"));
        assertNumber("178", client.get("2026|10|utilities|0|").get("unitPrice"));
        assertNull(client.get("2026|10|utilities|6|").get("qty"));
        assertNumber("346", client.get("2026|10|utilities|9|").get("unitPrice"));
        assertEquals("ton", client.get("2026|10|other-out|0|").get("unitLabel"));
        assertNull(client.get("2026|10|other-in|1|").get("qty"));
        assertFalse(client.containsKey("2026|10|utilities|10|"), "11번째 줄은 화면에 없다");
        assertFalse(client.containsKey("2026|10|other-out|2|"), "엑셀에 없는 셋째 줄은 건드리지 않는다");
    }

    @Test
    @DisplayName("운영내역: 두 양식 모두 '전력량' 열과 일자 줄을 찾고, 새 양식의 De-NOx · 액체산소 열은 경고만 한다")
    void importsOperationPowerOnBothLayouts() {
        importer.importWorkbook(workbook(false, 2025, 10), null, null);
        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(workbook(true, 2026, 10), null, null);

        assertNumber("20001", operation.get("2025-10-01|").get("powerKwh"));     // 0.00000000009 는 버린다
        assertNumber("20031", operation.get("2025-10-31|").get("powerKwh"));
        assertNumber("20001", operation.get("2026-10-01|").get("powerKwh"));
        assertNumber("20031", operation.get("2026-10-31|").get("powerKwh"));
        String skipped = result.getWarnings().stream().filter(w -> w.startsWith("운영내역: 화면에 칸이 없어")).findFirst().orElse("");
        assertTrue(skipped.contains("약품희석수 Main (A)") && skipped.contains("전력량 De-NOx 총 사용량(B)") && skipped.contains("액체산소"), skipped);
        // 스팀생산량 등은 저장하지 않고 복합보일러 데이터와 비교만 한다
        verify(comboTable, never()).saveBulk(anyInt(), any(), anyList(), anyBoolean());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.startsWith("운영내역 '스팀생산량'") && w.contains("31일치")),
                result.getWarnings().toString());
    }

    @Test
    @DisplayName("빈칸은 저장값을 비운다 — Summary 의 ASC 공급량, 운영내역 3일 전력량, Accumulated 11월 실적")
    void blankCellsClearStoredValues() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("yearNo", 2026);
        before.put("monthNo", 10);
        before.put("ascSteamAmount", new BigDecimal("1990"));
        before.put("ascRefundAnnual", new BigDecimal("5000"));
        summary.put("2026|10|", before);
        operation.put("2026-10-03|", new LinkedHashMap<>(Map.of("usageDate", LocalDate.of(2026, 10, 3), "powerKwh", new BigDecimal("999"))));
        accumulated.put("2026|11|", new LinkedHashMap<>(Map.of("yearNo", 2026, "monthNo", 11, "bscVolume", new BigDecimal("123"))));

        importer.importWorkbook(workbook(true, 2026, 10), null, null);

        assertTrue(summaryRow(2026, 10).containsKey("ascSteamAmount"));
        assertNull(summaryRow(2026, 10).get("ascSteamAmount"));
        assertNull(summaryRow(2026, 10).get("ascRefundAnnual"));
        assertNull(operation.get("2026-10-03|").get("powerKwh"));
        assertNull(accumulated.get("2026|11|").get("bscVolume"));
        assertNumber("38500", accumulated.get("2026|11|").get("contractVolume"));
    }

    @Test
    @DisplayName("기간이 다르면 막는다 — 아무 표도 건드리지 않는다")
    void rejectsPeriodMismatch() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> importer.importWorkbook(workbook(true, 2026, 10), 2026, 9));

        assertTrue(error.getMessage().contains("2026년 10월") && error.getMessage().contains("2026년 9월"), error.getMessage());
        verify(summaryTable, never()).saveBulk(anyInt(), any(), anyList(), anyBoolean());
        verify(clientTable, never()).saveBulk(anyInt(), any(), anyList(), anyBoolean());
        verify(operationTable, never()).saveBulk(anyInt(), any(), anyList(), anyBoolean());
        // 같은 기간을 보내면 통과
        assertEquals(10, importer.importWorkbook(workbook(true, 2026, 10), 2026, 10).getMonth());
    }

    @Test
    @DisplayName("회계연도 시트: 월별 운영내역 · Accumulated 는 6월이 속한 해의 실제 연/월로 들어간다")
    void fiscalYearSheetsUseCalendarKeys() {
        Workbook wb = workbook(true, 2026, 10);
        at(wb, "Accumulated steam volume", 4, 1).setCellValue(2025);       // 통합본의 YEAR 칸이 작년 그대로인 경우
        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(wb, null, null);

        assertNumber("38176", accumulated.get("2026|10|").get("bscVolume"));
        assertNumber("38000", accumulated.get("2026|6|").get("contractVolume"));
        assertNumber("39100", accumulated.get("2027|5|").get("contractVolume"));
        assertNumber("224", accumulated.get("2026|8|").get("ascVolume"));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.startsWith("Accumulated") && w.contains("YEAR 칸은 2025")),
                result.getWarnings().toString());

        assertNumber("37004", monthly.get("2026|10|").get("steamQty"));
        assertNumber("178", monthly.get("2026|10|").get("powerUnit"));
        assertNumber("553750", monthly.get("2026|8|").get("etcAirOver"));
        assertNull(monthly.get("2027|1|").get("steamQty"));
        assertNull(monthly.get("2027|1|").get("steamUnit"), "#DIV/0! 은 빈칸");
        assertNumber("1206", monthly.get("2027|5|").get("lifeUnit"));
        assertFalse(monthly.containsKey("2025|10|"), "작년 표는 건너뛴다");
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.startsWith("월별 운영내역") && w.contains("2025년도")));
    }

    @Test
    @DisplayName("연 단위 시트: BSC 단가와 Adjustment Factor 세 표를 항목 글자로 넣는다")
    void importsYearlySheets() {
        importer.importWorkbook(workbook(true, 2026, 10), null, null);

        assertNumber("18100", bsc.get("2026|").get("bscRate"));
        assertNumber("0.011", adjustment.get("2026|afbsc|a|").get("coefficient"));
        assertNull(adjustment.get("2026|afbsc|a|").get("baseValue"));
        assertNumber("106", adjustment.get("2026|afbsc|e-bottom|").get("baseValue"));
        assertTrue(adjustment.containsKey("2026|afbsc-rebase|h|"));
        assertFalse(adjustment.containsKey("2026|afbsc-rebase|e|"));
        assertNumber("118", adjustment.get("2026|afasc|g|").get("presentValue"));
        assertEquals(9 + 9 + 6, adjustment.size());
    }

    @Test
    @DisplayName("사고이력: 다시 올려도 줄이 늘지 않는다")
    void incidentImportIsIdempotent() {
        importer.importWorkbook(workbook(true, 2026, 10), null, null);
        String first = dump(incident);
        importer.importWorkbook(workbook(true, 2026, 10), null, null);

        assertEquals(2, incident.size());
        assertEquals(first, dump(incident));
        Map<String, Object> row = incident.get("2026|srf-accident-record:xlsx-1|");
        assertEquals("SRF", row.get("facility"));
        assertEquals("ACCIDENT", row.get("kind"));
        assertEquals("2026-10-27", row.get("occurDate"));
        assertEquals("10.5", row.get("downtimeHours"));
        assertEquals("1.48299745", row.get("lossAmount"));
        assertEquals("3/13~14", incident.get("2026|srf-accident-record:xlsx-2|").get("occurDate"));
    }

    @Test
    @DisplayName("시트나 제목이 없으면 그 부분만 건너뛰고 경고한다")
    void missingSheetIsWarningNotError() {
        Workbook wb = workbook(true, 2026, 10);
        wb.removeSheetAt(wb.getSheetIndex("BSC"));
        at(wb, "운영내역", 2, 11).setCellValue("전기");

        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(wb, null, null);

        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("'BSC' 시트가 없어")), result.getWarnings().toString());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("'전력량' 열을 찾지 못해")), result.getWarnings().toString());
        assertTrue(bsc.isEmpty());
        assertTrue(operation.isEmpty());
        assertNotNull(summaryRow(2026, 10));
    }

    @Test
    @DisplayName("Summary 연동 칸: 수식 결과가 Client Invoice 연동값과 다르거나 손으로 넣은 숫자면 그 값을 넣는다")
    void linkedCellsStoreOverrides() {
        Workbook wb = workbook(false, 2025, 10);
        at(wb, "Summary(VK)", 54, 3).setCellValue(123456);              // 수식은 그대로, 저장된 결과만 다르다
        Cell typed = at(wb, "Summary(VK)", 56, 3);
        typed.removeFormula();
        typed.setCellValue(0);                                          // 손으로 넣은 0

        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(wb, null, null);

        assertNumber("123456", summaryRow(2025, 10).get("utilitiesDeduction"));
        assertNumber("0", summaryRow(2025, 10).get("otherDeduction1"));
        assertNull(summaryRow(2025, 10).get("otherDeduction2"));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("UTILITIES DEDUCTION COSTS") && w.contains("직접 입력값")),
                result.getWarnings().toString());
    }

    // ───────────────────────── 내보내기 ─────────────────────────

    @Test
    @DisplayName("내보내기: 새 양식에서 가져온 값을 옛 양식 서식에 채우고 다시 올려도 표가 그대로다")
    void exportThenImportKeepsTables() {
        for (int day = 1; day <= 31; day += 1) {
            Map<String, Object> production = new LinkedHashMap<>();
            production.put("usageDate", LocalDate.of(2026, 10, day));
            production.put("comboSteam", new BigDecimal(900 + day));
            production.put("comboProcess", new BigDecimal(400 + day));
            production.put("comboDeaerator", new BigDecimal(1100 + day));
            combo.put(production.get("usageDate") + "|", production);
        }
        importer.importWorkbook(workbook(true, 2026, 10), null, null);
        client.get("2026|10|other-in|0|").putAll(Map.of("itemLabel", "미달 정산", "qty", new BigDecimal("2"), "unitPrice", new BigDecimal("1500.5")));
        summaryRow(2026, 10).put("otherDeduction2", new BigDecimal("4321"));      // 직접 넣은 값
        String before = dump(summary) + dump(client) + dump(operation) + dump(monthly) + dump(accumulated)
                + dump(bsc) + dump(adjustment) + dump(incident);

        Workbook template = workbook(false, 2025, 10);                             // 서식은 옛 양식 · 2025년 10월
        exporter.fill(template, 2026, 10);

        assertEquals(LocalDate.of(2026, 10, 1), at(template, "Summary(VK)", 4, 3).getLocalDateTimeCellValue().toLocalDate());
        assertEquals(LocalDate.of(2026, 10, 31), at(template, "Client Invoice", 0, 5).getLocalDateTimeCellValue().toLocalDate());
        assertEquals(38176.0, at(template, "Summary(VK)", 21, 3).getNumericCellValue());
        assertEquals("BSC!E11", at(template, "Summary(VK)", 15, 3).getCellFormula(), "값이 같은 수식은 그대로 둔다");
        assertEquals("'Client Invoice'!F20", at(template, "Summary(VK)", 54, 3).getCellFormula(), "연동 칸은 수식을 둔다");
        assertEquals(4321.0, at(template, "Summary(VK)", 58, 3).getNumericCellValue());
        assertEquals(20001.0, at(template, "운영내역", 4, 11).getNumericCellValue());
        assertEquals(901.0, at(template, "운영내역", 4, 3).getNumericCellValue(), "스팀생산량은 복합보일러 데이터");
        assertEquals("2. 2026년 10월 보일러 운영내역", at(template, "운영내역", 0, 2).getStringCellValue());
        assertEquals("2026년도", at(template, "월별 운영내역", 1, 0).getStringCellValue());
        assertEquals(2026.0, at(template, "Accumulated steam volume", 4, 1).getNumericCellValue());
        assertNotNull(template.getSheet("26년 사고이력"));

        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(template, null, null);

        assertEquals(2026, result.getYear());
        assertEquals(before, dump(summary) + dump(client) + dump(operation) + dump(monthly) + dump(accumulated)
                + dump(bsc) + dump(adjustment) + dump(incident));
        assertTrue(result.getWarnings().stream().noneMatch(w -> w.startsWith("운영내역 '")), "내려받은 파일은 복합보일러 값과 같다");
    }

    @Test
    @DisplayName("내보내기: DB 에 없는 달은 입력 칸을 비운다 (서식에 남은 다른 달 값을 싣지 않는다)")
    void exportOfEmptyMonthBlanksInputs() {
        Workbook template = workbook(false, 2025, 10);
        exporter.fill(template, 2026, 2);

        SteamSrfInvoiceExcelImportResponse result = importer.importWorkbook(template, null, null);

        assertEquals(2, result.getMonth());
        assertNull(summaryRow(2026, 2).get("bscSteamAmount"));
        assertNull(summaryRow(2026, 2).get("bscRate"));
        assertNull(client.get("2026|2|utilities|0|").get("qty"));
        assertEquals(28, operation.size());
        assertTrue(operation.values().stream().allMatch(row -> row.get("powerKwh") == null));
        assertEquals(0, result.getImported());
    }
}
