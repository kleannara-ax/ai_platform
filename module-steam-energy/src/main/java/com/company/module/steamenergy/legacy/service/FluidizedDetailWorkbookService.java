package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import com.company.module.steamenergy.legacy.service.sheet.SheetData;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 세부 운영내역 시트 렌더링 — Excel 파일 의존성 제거판.
 *
 * 동작:
 * - 시트 템플릿(정적 셀/수식/스타일/머지/폭/높이)을 classpath JSON 에서 로드.
 *   (개발 시 tools/extract-detail-sheet.py 로 추출 — 원본 Excel 변경 시 재실행)
 * - DB 의 table_cell_value 입력값을 시트 모델에 적용.
 * - SUM/AVERAGE/IFERROR/산술 수식은 자바 평가기로 평가.
 * - 응답은 평가된 셀 텍스트 (정수/소수 포맷팅 포함) + 머지 정보.
 */
@Service
public class FluidizedDetailWorkbookService {
    private static final String MAIN_TABLE = "fluidized_detail_main";
    private static final String FLOW_TABLE = "flow_m_fluid_incinerator";
    private static final String KNE_EXTRA_COST_KEY = "KNE_EXTRA_COST";
    private static final String MAIN_RANGE = "A1:AK96";
    private static final String SUMMARY_RANGE = "G100:J102";
    private static final String EMPTY_RANGE = "A1:A1";
    private static final int[] INVENTORY_ROWS = {20, 23, 26, 29, 32, 35, 38, 48};
    private static final int[][] INVENTORY_ROW_GROUPS = {
            {18, 19, 20},
            {21, 22, 23},
            {24, 25, 26},
            {27, 28, 29},
            {30, 31, 32},
            {33, 34, 35},
            {36, 37, 38},
            {46, 47, 48}
    };

    private final TableService tableService;
    /** 시트 템플릿 — 한 번 로드 후 immutable 공유. 각 요청은 snapshot() 으로 mutable 사본 받음. */
    private final SheetData sheetTemplate;

    // ─── 결과 캐시 ──────────────────────────────────────────────────────
    // 동일 (monthKey, viewKey) 의 짧은 시간 내 반복 요청 시 응답 즉시 반환.
    // 데이터 변경(POST/PATCH/DELETE) 시 invalidateRenderCache(monthKey) 로 무효화.
    private static final long CACHE_TTL_MS = 30_000L;
    private final ConcurrentHashMap<String, CacheEntry> renderCache = new ConcurrentHashMap<>();
    private record CacheEntry(long expiresAt, Map<String, Object> payload) {}

    public void invalidateRenderCache(String monthKey) {
        if (monthKey == null || monthKey.isBlank()) {
            renderCache.clear();
            return;
        }
        renderCache.keySet().removeIf(key -> key.startsWith(monthKey + "|"));
    }

    public FluidizedDetailWorkbookService(TableService tableService) {
        this.tableService = tableService;
        this.sheetTemplate = SheetData.load("fluidized-detail");
    }

    public Map<String, Object> renderMonth(String monthKey) {
        return renderMonth(monthKey, "all");
    }

    public Map<String, Object> renderMonth(String monthKey, String viewKey) {
        String cacheKey = monthKey + "|" + (viewKey == null ? "all" : viewKey);
        CacheEntry hit = renderCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAt() > now) {
            return hit.payload();
        }
        Map<String, Object> fresh = renderMonthUncached(monthKey, viewKey);
        renderCache.put(cacheKey, new CacheEntry(now + CACHE_TTL_MS, fresh));
        return fresh;
    }

    private Map<String, Object> renderMonthUncached(String monthKey, String viewKey) {
        SheetData sheet = sheetTemplate.snapshot();
        List<Map<String, Object>> rows = tableService.listAllByMonth("table_cell_value", monthKey);
        String mainRange = resolveMainRange(viewKey);
        boolean includeSummary = shouldIncludeSummary(viewKey);

        clearEditableInputs(sheet);
        clearLinkedFlowInputs(sheet);
        applyStoredInputs(sheet, rows);
        applyLinkedFlowInputs(sheet, rows);
        applyOperationCalculations(sheet, monthKey);
        applySrfUsageFromBurnRow(sheet, monthKey);
        applyInventoryCarryOver(sheet, monthKey);
        applySrfIncome(sheet, rows);
        applyOperationTotals(sheet, monthKey, rows);

        return Map.of(
                "main", extractSection(sheet, mainRange),
                "summary", includeSummary ? extractSection(sheet, SUMMARY_RANGE) : emptySection(EMPTY_RANGE)
        );
    }

    private String resolveMainRange(String viewKey) {
        if (viewKey == null) return MAIN_RANGE;
        return switch (viewKey) {
            case "operation" -> "A3:AK17";
            case "consumables" -> "A3:AK44";
            case "srf" -> "A3:AK64";
            case "power-water" -> "A3:AK80";
            case "other" -> "A3:AK96";
            default -> MAIN_RANGE;
        };
    }

    private boolean shouldIncludeSummary(String viewKey) {
        return viewKey == null || "all".equals(viewKey);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  시트 데이터 변환 단계 — POI Sheet 대신 SheetData 사용
    // ─────────────────────────────────────────────────────────────────────

    private void clearEditableInputs(SheetData sheet) {
        for (int rowIndex = 1; rowIndex <= 102; rowIndex += 1) {
            for (int colIndex = 1; colIndex <= 37; colIndex += 1) {
                String colLabel = columnLabel(colIndex);
                if (!isEditableCell(rowIndex, colLabel)) continue;
                sheet.setBlank(colLabel + rowIndex);
            }
        }
    }

    private void applyStoredInputs(SheetData sheet, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (!MAIN_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            String cellRef = String.valueOf(row.get("row_key"));
            if (KNE_EXTRA_COST_KEY.equals(cellRef)) continue;
            int[] colRow = parseCellRef(cellRef);
            if (colRow == null) continue;
            String colLabel = columnLabel(colRow[0]);
            int rowNumber = colRow[1];
            if (!isEditableCell(rowNumber, colLabel)) continue;

            Object rawValue = row.get("cell_value");
            String text = rawValue == null ? "" : String.valueOf(rawValue).trim();
            if (text.isEmpty()) {
                sheet.setBlank(cellRef);
                continue;
            }
            String numericText = text.replace(",", "");
            try {
                sheet.set(cellRef, Double.parseDouble(numericText));
            } catch (NumberFormatException ignored) {
                sheet.set(cellRef, text);
            }
        }
    }

    private void clearLinkedFlowInputs(SheetData sheet) {
        for (int day = 1; day <= 31; day += 1) {
            sheet.setBlank(columnLabel(day + 2) + "7");
        }
    }

    private void applyLinkedFlowInputs(SheetData sheet, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (!FLOW_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            if (toInt(row.get("col_index")) != 0) continue;
            int day = toInt(row.get("row_key"));
            if (day < 1 || day > 31) continue;
            setCellValue(sheet, columnLabel(day + 2) + "7", row.get("cell_value"));
        }
    }

    private void applyInventoryCarryOver(SheetData sheet, String monthKey) {
        Map<Integer, Double> startingByRow = resolveStartingInventory(monthKey);
        int daysInMonth = daysInMonth(monthKey);

        for (int[] group : INVENTORY_ROW_GROUPS) {
            int inboundRow = group[0];
            int usageRow = group[1];
            int inventoryRow = group[2];

            Double starting = startingByRow.get(inventoryRow);
            boolean hasActivity = hasInventoryActivity(sheet, inboundRow, usageRow, daysInMonth);
            if (starting == null && !hasActivity) {
                clearInventoryRow(sheet, inventoryRow, daysInMonth);
                continue;
            }
            if (starting == null) starting = 0d;

            double running = starting;
            double total = 0;
            int count = 0;

            for (int day = 1; day <= daysInMonth; day += 1) {
                String colLabel = columnLabel(day + 2);
                Double inbound = sheet.evaluateNumeric(colLabel + inboundRow);
                Double usage = sheet.evaluateNumeric(colLabel + usageRow);
                running = running + valueOrZero(inbound) - valueOrZero(usage);
                sheet.set(colLabel + inventoryRow, running);
                total += running;
                count += 1;
            }

            clearInventoryTrailingDays(sheet, inventoryRow, daysInMonth);
            if (count > 0) {
                sheet.set("AH" + inventoryRow, total);
                sheet.set("AI" + inventoryRow, total / count);
            } else {
                sheet.setBlank("AH" + inventoryRow);
                sheet.setBlank("AI" + inventoryRow);
            }
        }
    }

    private Map<Integer, Double> resolveStartingInventory(String monthKey) {
        String previousMonthKey = previousMonthKey(monthKey);
        if (previousMonthKey == null) return Map.of();
        List<Map<String, Object>> historyRows = tableService.listCellRowsByTableNameThroughMonth(MAIN_TABLE, previousMonthKey);
        if (historyRows.isEmpty()) return Map.of();

        Map<String, List<Map<String, Object>>> rowsByMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : historyRows) {
            String rowMonth = String.valueOf(row.get("month"));
            rowsByMonth.computeIfAbsent(rowMonth, key -> new ArrayList<>()).add(row);
        }
        if (rowsByMonth.isEmpty()) return Map.of();

        YearMonth cursor = YearMonth.parse(rowsByMonth.keySet().iterator().next());
        YearMonth target = YearMonth.parse(previousMonthKey);
        Map<Integer, Double> endingByRow = new LinkedHashMap<>();

        while (!cursor.isAfter(target)) {
            String cursorMonth = cursor.toString();
            List<Map<String, Object>> monthRows = rowsByMonth.getOrDefault(cursorMonth, Collections.emptyList());
            endingByRow = calculateMonthEnding(cursorMonth, monthRows, endingByRow);
            cursor = cursor.plusMonths(1);
        }
        return endingByRow;
    }

    private void applyOperationCalculations(SheetData sheet, String monthKey) {
        int daysInMonth = daysInMonth(monthKey);
        for (int day = 1; day <= daysInMonth; day += 1) {
            String colLabel = columnLabel(day + 2);
            boolean hasRunInput = hasAnyInput(sheet, colLabel + "4");
            if (!hasRunInput) {
                sheet.setBlank(colLabel + "5");
            } else {
                sheet.set(colLabel + "5", 24d - valueOrZero(sheet.evaluateNumeric(colLabel + "4")));
            }
            Double steamQty = sheet.evaluateNumeric(colLabel + "6");
            Double steamUnit = sheet.evaluateNumeric(colLabel + "11");
            Double stopHours = sheet.evaluateNumeric(colLabel + "5");
            Double fixedUnit = sheet.evaluateNumeric(colLabel + "13");
            setNumericOrBlank(sheet, colLabel + "12", steamQty == null ? null : steamQty * valueOrZero(steamUnit));
            setNumericOrBlank(sheet, colLabel + "14", stopHours == null ? null : stopHours * valueOrZero(fixedUnit));
        }
        for (int day = daysInMonth + 1; day <= 31; day += 1) {
            String colLabel = columnLabel(day + 2);
            sheet.setBlank(colLabel + "5");
            sheet.setBlank(colLabel + "12");
            sheet.setBlank(colLabel + "14");
        }
    }

    private void applySrfUsageFromBurnRow(SheetData sheet, String monthKey) {
        // 일별 SRF 소각량(row 9, SRF(톤))을 SRF 사용 행(row 47)에 복사.
        // 반입 행(row 46)은 사용자가 직접 입력한 값을 유지.
        int daysInMonth = daysInMonth(monthKey);
        for (int day = 1; day <= daysInMonth; day += 1) {
            String colLabel = columnLabel(day + 2);
            Double srfBurn = sheet.evaluateNumeric(colLabel + "9");
            setNumericOrBlank(sheet, colLabel + "47", srfBurn);
        }
        for (int day = daysInMonth + 1; day <= 31; day += 1) {
            sheet.setBlank(columnLabel(day + 2) + "47");
        }
    }

    /** custom 행 합계를 section → 합 으로 계산. */
    private Map<String, Double> sumCustomRowsBySection(List<Map<String, Object>> rows, int daysInMonth) {
        Map<String, Double> totals = new LinkedHashMap<>();
        if (rows == null) return totals;
        for (Map<String, Object> row : rows) {
            if (!MAIN_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            String rowKey = String.valueOf(row.get("row_key"));
            if (rowKey == null || !rowKey.startsWith("custom|")) continue;
            String[] parts = rowKey.split("\\|");
            if (parts.length != 4) continue;
            String section = parts[1];
            String suffix = parts[3];
            if (!suffix.startsWith("d")) continue;
            try {
                int day = Integer.parseInt(suffix.substring(1));
                if (day < 1 || day > daysInMonth) continue;
            } catch (NumberFormatException ignored) {
                continue;
            }
            Double v = parseNumeric(String.valueOf(row.get("cell_value")));
            if (v == null) continue;
            totals.merge(section, v, Double::sum);
        }
        return totals;
    }

    /** 섹션 → 소계 셀 매핑. 각 섹션의 custom 행 합을 해당 AH 소계 셀에 더한다. */
    private static final Map<String, Integer> SECTION_TOTAL_ROWS = Map.of(
            "kne",   17,
            "srf",   64,
            "power", 73,
            "water", 80,
            "other", 95
    );

    private void applyOperationTotals(SheetData sheet, String monthKey, List<Map<String, Object>> dbRows) {
        for (int row : List.of(5, 7, 12, 14, 16, 46, 47)) {
            double total = 0d;
            int count = 0;
            for (int day = 1; day <= 31; day += 1) {
                Double value = sheet.evaluateNumeric(columnLabel(day + 2) + row);
                if (value == null) continue;
                total += value;
                count += 1;
            }
            if (count > 0) {
                sheet.set("AH" + row, total);
                int averageCount = row == 14 ? daysInMonth(monthKey) : count;
                sheet.set("AI" + row, averageCount > 0 ? total / averageCount : 0d);
            } else {
                sheet.setBlank("AH" + row);
                sheet.setBlank("AI" + row);
            }
        }
        double operationCost = valueOrZero(sheet.evaluateNumeric("AH12"));
        double fixedCost = valueOrZero(sheet.evaluateNumeric("AH14"));
        double improvementCost = valueOrZero(sheet.evaluateNumeric("AK15"));
        sheet.set("AK12", operationCost);
        sheet.set("AK14", fixedCost);
        clearExtraCostGeneralCells(sheet);
        sheet.setBlank("AJ17");
        // 추가비용 행 (16) 전체 비움 — UI 에서 제거되었으므로 export 에서도 표시 안함.
        for (int col = columnNumber("A"); col <= columnNumber("AK"); col += 1) {
            sheet.setBlank(columnLabel(col) + "16");
        }
        sheet.set("AK17", operationCost + fixedCost + improvementCost);

        // 섹션별 custom 합을 AK(비용) 소계 셀에 더한다 (kne/srf/power/water/other).
        // (이전: AH 합계 칸 → 사용자 요구로 AK 비용 칸으로 변경)
        Map<String, Double> customTotals = sumCustomRowsBySection(dbRows, daysInMonth(monthKey));
        SECTION_TOTAL_ROWS.forEach((section, rowNum) -> {
            Double extra = customTotals.get(section);
            if (extra == null || extra == 0d) return;
            String ref = "AK" + rowNum;
            double base = valueOrZero(sheet.evaluateNumeric(ref));
            sheet.set(ref, base + extra);
        });
    }

    /**
     * SRF 수입금(세부 운영내역 row 46, AK46)을 SRF입고내역(fluidized_srf_inbound) 데이터로부터 자동 계산해 주입한다.
     * 원본 엑셀 AK46 = -'4. SRF입고내역'!M38 (외부 시트 참조) 이므로 렌더에서 평가 불가 → 요약 페이지와 동일 로직으로 산출.
     * AK46 을 채우면 템플릿 수식 AK64(SRF·폐기물 소계), AK96(총비용) 이 이를 반영해 재평가된다.
     */
    private void applySrfIncome(SheetData sheet, List<Map<String, Object>> rows) {
        double cost = computeSrfInboundCost(rows);
        if (cost != 0d) {
            sheet.set("AK46", -cost); // 비용 → 수입(차감) 방향으로 부호 반전
        }
    }

    /** 요약 서비스 calculateSrfInboundCost 와 동일: 거래처별 (월 입고량합계 × 단가) / 1000 의 합. */
    private double computeSrfInboundCost(List<Map<String, Object>> rows) {
        double[] vendorQty = new double[11];
        double[] vendorRate = new double[11];
        for (Map<String, Object> row : rows) {
            if (!"fluidized_srf_inbound".equals(String.valueOf(row.get("table_name")))) continue;
            int colIndex = toInt(row.get("col_index"));
            if (colIndex < 1 || colIndex > 10) continue;
            String rowKey = String.valueOf(row.get("row_key"));
            double value = parseDoubleSafe(String.valueOf(row.get("cell_value")));
            if ("rate".equals(rowKey)) {
                vendorRate[colIndex] = value;
            } else if (rowKey != null && rowKey.startsWith("entry:")) {
                vendorQty[colIndex] += value;
            }
        }
        double total = 0d;
        for (int vendor = 1; vendor <= 10; vendor += 1) {
            total += vendorQty[vendor] * vendorRate[vendor] / 1000d;
        }
        return total;
    }

    private double parseDoubleSafe(String raw) {
        if (raw == null) return 0d;
        String t = raw.replace(",", "").trim();
        if (t.isEmpty()) return 0d;
        try { return Double.parseDouble(t); }
        catch (NumberFormatException ignored) { return 0d; }
    }

    private void clearExtraCostGeneralCells(SheetData sheet) {
        for (int col = columnNumber("C"); col <= columnNumber("AJ"); col += 1) {
            sheet.setBlank(columnLabel(col) + "16");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  과거 월 누적 재고 계산 (DB 만 사용 — Excel 무관, 기존 로직 보존)
    // ─────────────────────────────────────────────────────────────────────

    private Map<Integer, Double> calculateMonthEnding(
            String monthKey,
            List<Map<String, Object>> rows,
            Map<Integer, Double> previousEndingByRow
    ) {
        int monthDays = daysInMonth(monthKey);
        String lastDayColumn = columnLabel(monthDays + 2);
        Map<Integer, Double> result = new LinkedHashMap<>();

        for (int inventoryRow : INVENTORY_ROWS) {
            Double savedEnding = findSavedInventoryEnding(rows, lastDayColumn, inventoryRow);
            if (savedEnding != null) result.put(inventoryRow, savedEnding);
        }
        for (int[] group : INVENTORY_ROW_GROUPS) {
            int inboundRow = group[0];
            int usageRow = group[1];
            int inventoryRow = group[2];
            if (result.containsKey(inventoryRow)) continue;
            Double starting = previousEndingByRow.get(inventoryRow);
            boolean hasActivity = hasSavedInventoryActivity(rows, inboundRow, usageRow);
            if (starting == null && !hasActivity) continue;
            if (starting == null) starting = 0d;

            double running = starting;
            for (int day = 1; day <= monthDays; day += 1) {
                String colLabel = columnLabel(day + 2);
                running = running
                        + valueOrZero(findSavedNumericCell(rows, colLabel + inboundRow))
                        - valueOrZero(findSavedNumericCell(rows, colLabel + usageRow));
            }
            result.put(inventoryRow, running);
        }
        return result;
    }

    private Double findSavedInventoryEnding(List<Map<String, Object>> rows, String lastDayColumn, int inventoryRow) {
        return findSavedNumericCell(rows, lastDayColumn + inventoryRow);
    }

    private Double findSavedNumericCell(List<Map<String, Object>> rows, String cellRef) {
        for (Map<String, Object> row : rows) {
            if (!MAIN_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            if (!cellRef.equals(String.valueOf(row.get("row_key")))) continue;
            if (toInt(row.get("col_index")) != 0) continue;
            Double value = parseNumeric(String.valueOf(row.get("cell_value")));
            if (value != null) return value;
        }
        return null;
    }

    private void clearInventoryRow(SheetData sheet, int inventoryRow, int daysInMonth) {
        for (int day = 1; day <= daysInMonth; day += 1) {
            sheet.setBlank(columnLabel(day + 2) + inventoryRow);
        }
        clearInventoryTrailingDays(sheet, inventoryRow, daysInMonth);
        sheet.setBlank("AH" + inventoryRow);
        sheet.setBlank("AI" + inventoryRow);
        sheet.setBlank("AK" + inventoryRow);
    }

    private void clearInventoryTrailingDays(SheetData sheet, int inventoryRow, int daysInMonth) {
        for (int day = daysInMonth + 1; day <= 31; day += 1) {
            sheet.setBlank(columnLabel(day + 2) + inventoryRow);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  공통 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    private void setCellValue(SheetData sheet, String cellRef, Object rawValue) {
        String text = rawValue == null ? "" : String.valueOf(rawValue).trim();
        if (text.isEmpty()) { sheet.setBlank(cellRef); return; }
        String numericText = text.replace(",", "");
        try { sheet.set(cellRef, Double.parseDouble(numericText)); }
        catch (NumberFormatException ignored) { sheet.set(cellRef, text); }
    }

    private void setNumericOrBlank(SheetData sheet, String cellRef, Double value) {
        if (value == null) { sheet.setBlank(cellRef); return; }
        sheet.set(cellRef, value);
    }

    private Double parseNumeric(String value) {
        if (value == null) return null;
        String raw = value.replace(",", "").trim();
        if (raw.isEmpty()) return null;
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException ignored) { return null; }
    }

    private double valueOrZero(Double value) { return value == null ? 0d : value; }

    private boolean isZeroText(String value) {
        if (value == null) return false;
        String raw = value.replace(",", "").trim();
        if ("-".equals(raw)) return true;
        try { return Double.parseDouble(raw) == 0d; }
        catch (NumberFormatException ignored) { return false; }
    }

    private String previousMonthKey(String monthKey) {
        if (monthKey == null || !monthKey.matches("\\d{4}-\\d{2}")) return null;
        int year = Integer.parseInt(monthKey.substring(0, 4));
        int month = Integer.parseInt(monthKey.substring(5, 7));
        if (month == 1) return (year - 1) + "-12";
        return year + "-" + String.format("%02d", month - 1);
    }

    private int daysInMonth(String monthKey) {
        if (monthKey == null || !monthKey.matches("\\d{4}-\\d{2}")) return 31;
        return YearMonth.parse(monthKey).lengthOfMonth();
    }

    private int toInt(Object value) {
        if (value == null) return -1;
        try { return Integer.parseInt(String.valueOf(value).trim()); }
        catch (NumberFormatException ignored) { return -1; }
    }

    /** "AH103" → {col=34, row=103}, 1-based. 잘못된 ref 는 null. */
    private int[] parseCellRef(String ref) {
        try {
            return SheetData.parseRef(ref);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> extractSection(SheetData sheet, String rangeRef) {
        int colon = rangeRef.indexOf(':');
        String fromAddr = colon < 0 ? rangeRef : rangeRef.substring(0, colon);
        String toAddr = colon < 0 ? rangeRef : rangeRef.substring(colon + 1);
        int[] f = SheetData.parseRef(fromAddr);
        int[] t = SheetData.parseRef(toAddr);
        int c1 = Math.min(f[0], t[0]), c2 = Math.max(f[0], t[0]);
        int r1 = Math.min(f[1], t[1]), r2 = Math.max(f[1], t[1]);

        Map<String, String> cells = new LinkedHashMap<>();
        for (int r = r1; r <= r2; r += 1) {
            for (int c = c1; c <= c2; c += 1) {
                String addr = SheetData.columnLetter(c) + r;
                String value = formatCellValue(sheet, addr);
                if (!value.isEmpty()) cells.put(addr, value);
            }
        }

        List<String> merges = new ArrayList<>();
        for (Map<String, Object> m : sheet.merges()) {
            String from = String.valueOf(m.get("from"));
            String to = String.valueOf(m.get("to"));
            int[] mf = SheetData.parseRef(from);
            int[] mt = SheetData.parseRef(to);
            if (mf[1] < r1 || mt[1] > r2) continue;
            if (mf[0] < c1 || mt[0] > c2) continue;
            merges.add(from + ":" + to);
        }
        return Map.of("range", rangeRef, "cells", cells, "merges", merges);
    }

    private Map<String, Object> emptySection(String rangeRef) {
        return Map.of("range", rangeRef, "cells", Map.of(), "merges", List.of());
    }

    /** 셀 값을 화면 표시용 문자열로 포맷팅 (포맷 코드 또는 기본 정수/소수). */
    private String formatCellValue(SheetData sheet, String addr) {
        Object value = sheet.evaluate(addr);
        if (value == null) return "";
        if (value instanceof String s) return s.trim();
        if (value instanceof Number num) {
            double d = num.doubleValue();
            String format = sheet.formatOf(addr);
            return formatNumberByExcelFormat(d, format);
        }
        return String.valueOf(value).trim();
    }

    /** 단순화한 Excel number-format 적용. 정수형 또는 소수 자리 결정. */
    private String formatNumberByExcelFormat(double value, String format) {
        // 0 : 정수, 0.00 : 2자리, #,##0 : 천단위 정수 등
        if (format == null || format.isEmpty() || "General".equals(format)) {
            // 기본: 정수면 정수로, 아니면 소수 자릿수 자동 (최대 4)
            if (Math.floor(value) == value) {
                return Long.toString((long) value);
            }
            return String.format("%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        // 콤마 천단위 + 소수 자릿수
        int decimals = 0;
        int dotIdx = format.indexOf('.');
        if (dotIdx >= 0) {
            int end = dotIdx + 1;
            while (end < format.length() && (format.charAt(end) == '0' || format.charAt(end) == '#')) end += 1;
            decimals = end - dotIdx - 1;
        }
        boolean grouped = format.contains(",");
        String pattern = (grouped ? "#,##0" : "0") + (decimals > 0 ? "." + "0".repeat(decimals) : "");
        return new java.text.DecimalFormat(pattern).format(value);
    }

    private boolean hasSavedInventoryActivity(List<Map<String, Object>> rows, int inboundRow, int usageRow) {
        for (Map<String, Object> row : rows) {
            if (!MAIN_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            if (toInt(row.get("col_index")) != 0) continue;
            String cellRef = String.valueOf(row.get("row_key"));
            int[] colRow = parseCellRef(cellRef);
            if (colRow == null) continue;
            int rowNumber = colRow[1];
            if (rowNumber != inboundRow && rowNumber != usageRow) continue;
            int colNumber = colRow[0];
            if (colNumber < columnNumber("C") || colNumber > columnNumber("AG")) continue;
            String rawValue = String.valueOf(row.get("cell_value"));
            if (parseNumeric(rawValue) != null || isZeroText(rawValue)) return true;
        }
        return false;
    }

    private boolean hasInventoryActivity(SheetData sheet, int inboundRow, int usageRow, int daysInMonth) {
        for (int day = 1; day <= daysInMonth; day += 1) {
            String colLabel = columnLabel(day + 2);
            if (hasNumericInput(sheet, colLabel + inboundRow) || hasNumericInput(sheet, colLabel + usageRow)) return true;
        }
        return false;
    }

    private boolean hasNumericInput(SheetData sheet, String cellRef) {
        Object v = sheet.evaluate(cellRef);
        if (v == null) return false;
        if (v instanceof Number) return true;
        String s = String.valueOf(v);
        return parseNumeric(s) != null || isZeroText(s);
    }

    private boolean hasAnyInput(SheetData sheet, String cellRef) {
        Object v = sheet.evaluate(cellRef);
        if (v == null) return false;
        if (v instanceof Number) return true;
        return !String.valueOf(v).trim().isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  편집 가능 셀 정책 (기존 로직 그대로 보존)
    // ─────────────────────────────────────────────────────────────────────

    private boolean isEditableCell(int row, String colLabel) {
        if (row == 2) return false;
        if (row == 1 || row == 3 || row == 100 || row == 101) return false;
        if (row == 10) return false;
        if (row == 43 || row == 44) return false;
        if (row == 16) return false;
        if ("AJ".equals(colLabel)) return isDirectUnitPriceRow(row);
        if ("AK".equals(colLabel)) return isDirectCostRow(row);
        if (columnNumber(colLabel) >= columnNumber("AH")) return false;
        if (row >= 102) return false;
        if ("A".equals(colLabel) || "B".equals(colLabel)) return false;
        if (row == 5) return false;
        if (row == 7) return false;
        if (row == 12 || row == 14) return false;
        if (row == 20 || row == 23 || row == 26 || row == 29 || row == 32 || row == 35 || row == 38 || row == 45 || row == 47 || row == 48) return false;
        if (row == 63 || row == 64 || row == 73 || row == 80 || row == 93 || row == 94 || row == 95 || row == 96) return false;
        return true;
    }

    private boolean isDirectUnitPriceRow(int row) {
        return switch (row) {
            case 6, 18, 21, 22, 24, 25, 27, 28, 30, 33, 36, 40, 46,
                    49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62,
                    65, 71, 72, 74, 76, 77, 78, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92 -> true;
            default -> false;
        };
    }

    private boolean isDirectCostRow(int row) {
        return row == 15 || row == 16;
    }

    private int columnNumber(String label) {
        int result = 0;
        for (int index = 0; index < label.length(); index += 1) {
            result = result * 26 + (label.charAt(index) - 'A' + 1);
        }
        return result;
    }

    private String columnLabel(int oneBasedIndex) {
        return SheetData.columnLetter(oneBasedIndex);
    }
}
