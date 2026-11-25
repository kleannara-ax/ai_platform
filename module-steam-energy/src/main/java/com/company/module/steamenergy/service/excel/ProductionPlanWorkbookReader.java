package com.company.module.steamenergy.service.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 생산계획 엑셀("생산계획" 시트) 읽기 — 운영 최적화 화면의 호기별 생산량·가동시간.
 *
 * <p>칸 위치를 고정하지 않고 <b>글자로</b> 찾는다 (열·행 순서가 바뀌어도 읽는다).
 * <ul>
 *   <li>시트: 이름에 "생산계획" 이 든 보이는 시트. 없으면 월 머리글이 있는 첫 시트.</li>
 *   <li>월 머리글 줄: "25년 12월", "1월" … "12월", "27년 1월" 처럼 월 글자가 가장 많은 줄.
 *       연도가 적힌 칸을 기준으로 앞뒤 칸의 연도를 정하고, 연도가 한 칸도 없으면 화면에서 고른 연도를 쓴다.</li>
 *   <li>머리글 바로 아랫줄의 "예상"/"실적" 을 구분으로 읽는다.</li>
 *   <li>호기 묶음: "제지2"·"제지3" → PM-2·PM-3, "화장지3~5" → TM-3~5. 다른 묶음(계, 가공, 생리대)은 건너뛴다.</li>
 *   <li>묶음 안의 줄: "생산량"·"점보롤" → 생산량(톤), "가동시간" → 가동시간(Hr). "생산성" 줄은 읽지 않는다.</li>
 * </ul>
 * DB 를 건드리지 않는다 — 읽은 값만 돌려준다.
 */
public final class ProductionPlanWorkbookReader {

    /** 한 호기·한 달의 생산계획. 값이 없는 항목은 null. */
    public record PlanRow(int year, int month, String machineCode, BigDecimal productionTon,
                          BigDecimal runtimeHours, String planKind) {
    }

    /** 읽은 결과 — 시트 이름과 값이 있는 (연·월·호기) 줄. */
    public record Result(String sheetName, List<PlanRow> rows) {
    }

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.KOREA);
    private static final Pattern MONTH = Pattern.compile("^'?(?:(\\d{2,4})년)?(\\d{1,2})월$");
    private static final Pattern PAPER = Pattern.compile("^제지(?:#)?([23])(?:호기)?$");
    private static final Pattern TISSUE = Pattern.compile("^화(?:장지|\\)초지)(?:#)?([345])(?:호기)?$");
    private static final int HEADER_SCAN_ROWS = 20;
    private static final int MAX_ROW = 400;
    private static final int MAX_COL = 80;
    private static final int LABEL_COLS = 6;

    private ProductionPlanWorkbookReader() {
    }

    /**
     * @param baseYear 머리글에 연도가 한 칸도 없을 때 첫 월 칸에 쓰는 연도
     * @throws IllegalArgumentException 생산계획 시트나 월 머리글을 찾지 못했을 때
     */
    public static Result read(Workbook workbook, int baseYear) {
        Sheet sheet = findSheet(workbook);
        if (sheet == null) {
            throw new IllegalArgumentException("생산계획 시트를 찾을 수 없습니다. 월 머리글(1월, 2월 …)이 있는 시트가 필요합니다.");
        }
        int headerRow = findHeaderRow(sheet);
        Map<Integer, int[]> monthCols = monthColumns(sheet, headerRow, baseYear);
        Map<Integer, String> kinds = kindColumns(sheet, headerRow + 1, monthCols);

        // (호기 → 월 칸 → [생산량, 가동시간])
        Map<String, Map<Integer, BigDecimal[]>> values = new LinkedHashMap<>();
        String machine = null;
        int groupCol = -1;
        for (int r = headerRow + 1; r <= Math.min(sheet.getLastRowNum(), MAX_ROW); r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            // 묶음 이름 칸: 앞쪽 몇 칸 중 "제지2" 같은 이름이 처음 나온 칸을 묶음 칸으로 삼는다.
            if (groupCol < 0) {
                for (int c = 0; c < LABEL_COLS; c += 1) {
                    if (machineCode(key(text(row.getCell(c)))) != null) {
                        groupCol = c;
                        break;
                    }
                }
            }
            if (groupCol >= 0) {
                String group = key(text(row.getCell(groupCol)));
                if (!group.isEmpty()) machine = machineCode(group);   // 다른 묶음이 시작되면 null
            }
            if (machine == null) continue;
            int metric = metricOf(row, groupCol);
            if (metric < 0) continue;
            for (Map.Entry<Integer, int[]> e : monthCols.entrySet()) {
                BigDecimal v = number(row.getCell(e.getKey()), metric == 0 ? 6 : 4);
                if (v == null) continue;
                values.computeIfAbsent(machine, k -> new LinkedHashMap<>())
                        .computeIfAbsent(e.getKey(), k -> new BigDecimal[2])[metric] = v;
            }
        }

        List<PlanRow> rows = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, BigDecimal[]>> m : values.entrySet()) {
            for (Map.Entry<Integer, BigDecimal[]> c : m.getValue().entrySet()) {
                int[] ym = monthCols.get(c.getKey());
                rows.add(new PlanRow(ym[0], ym[1], m.getKey(), c.getValue()[0], c.getValue()[1], kinds.get(c.getKey())));
            }
        }
        rows.sort((a, b) -> a.year() != b.year() ? Integer.compare(a.year(), b.year())
                : a.month() != b.month() ? Integer.compare(a.month(), b.month())
                : a.machineCode().compareTo(b.machineCode()));
        return new Result(sheet.getSheetName(), rows);
    }

    /** 이름에 "생산계획" 이 든 보이는 시트. 없으면 월 머리글이 있는 첫 보이는 시트. */
    private static Sheet findSheet(Workbook workbook) {
        Sheet fallback = null;
        for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
            if (workbook.getSheetVisibility(i) != SheetVisibility.VISIBLE) continue;
            Sheet sheet = workbook.getSheetAt(i);
            boolean hasHeader = bestHeaderRow(sheet)[1] >= 3;
            if (!hasHeader) continue;
            if (key(sheet.getSheetName()).contains("생산계획")) return sheet;
            if (fallback == null) fallback = sheet;
        }
        return fallback;
    }

    private static int findHeaderRow(Sheet sheet) {
        int[] best = bestHeaderRow(sheet);
        if (best[1] < 3) {
            throw new IllegalArgumentException("'" + sheet.getSheetName() + "' 시트에서 월 머리글(1월, 2월 …)을 찾을 수 없습니다.");
        }
        return best[0];
    }

    /** [줄 번호, 그 줄의 월 칸 수] — 월 글자가 가장 많은 줄. */
    private static int[] bestHeaderRow(Sheet sheet) {
        int bestRow = -1, bestCount = 0;
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS); r += 1) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int count = 0;
            for (int c = 0; c < MAX_COL; c += 1) {
                if (MONTH.matcher(key(text(row.getCell(c)))).matches()) count += 1;
            }
            if (count > bestCount) {
                bestCount = count;
                bestRow = r;
            }
        }
        return new int[]{bestRow, bestCount};
    }

    /** 월 칸 → [연, 월]. 연도가 적힌 칸을 기준으로 앞뒤 칸의 연도를 정한다. */
    private static Map<Integer, int[]> monthColumns(Sheet sheet, int headerRow, int baseYear) {
        Row row = sheet.getRow(headerRow);
        List<int[]> cols = new ArrayList<>();   // [칸, 적힌 연도(없으면 0), 월]
        for (int c = 0; c < MAX_COL; c += 1) {
            Matcher m = MONTH.matcher(key(text(row.getCell(c))));
            if (!m.matches()) continue;
            int month = Integer.parseInt(m.group(2));
            if (month < 1 || month > 12) continue;
            int year = 0;
            if (m.group(1) != null) {
                year = Integer.parseInt(m.group(1));
                if (year < 100) year += 2000;
            }
            cols.add(new int[]{c, year, month});
        }
        int[] years = new int[cols.size()];
        int firstAnchor = -1;
        for (int i = 0; i < cols.size(); i += 1) {
            if (cols.get(i)[1] > 0) {
                firstAnchor = i;
                break;
            }
        }
        if (firstAnchor < 0) {
            years[0] = baseYear;
            firstAnchor = 0;
        } else {
            years[firstAnchor] = cols.get(firstAnchor)[1];
            // 기준 칸의 왼쪽: 월이 커지면(1월 ← 12월) 한 해 앞이다.
            for (int i = firstAnchor - 1; i >= 0; i -= 1) {
                years[i] = cols.get(i)[2] >= cols.get(i + 1)[2] ? years[i + 1] - 1 : years[i + 1];
            }
        }
        // 기준 칸의 오른쪽: 연도가 적혀 있으면 그 값, 아니면 월이 줄어들 때(12월 → 1월) 한 해 뒤다.
        for (int i = firstAnchor + 1; i < cols.size(); i += 1) {
            if (cols.get(i)[1] > 0) years[i] = cols.get(i)[1];
            else years[i] = cols.get(i)[2] <= cols.get(i - 1)[2] ? years[i - 1] + 1 : years[i - 1];
        }
        Map<Integer, int[]> result = new LinkedHashMap<>();
        for (int i = 0; i < cols.size(); i += 1) {
            result.put(cols.get(i)[0], new int[]{years[i], cols.get(i)[2]});
        }
        return result;
    }

    private static Map<Integer, String> kindColumns(Sheet sheet, int rowIndex, Map<Integer, int[]> monthCols) {
        Map<Integer, String> kinds = new LinkedHashMap<>();
        Row row = sheet.getRow(rowIndex);
        if (row == null) return kinds;
        for (Integer c : monthCols.keySet()) {
            String v = key(text(row.getCell(c)));
            if (v.equals("예상") || v.equals("실적") || v.equals("계획")) kinds.put(c, v);
        }
        return kinds;
    }

    /** "제지2" → PM-2, "화장지4" → TM-4. 해당 없으면 null. */
    private static String machineCode(String label) {
        Matcher paper = PAPER.matcher(label);
        if (paper.matches()) return "PM-" + paper.group(1);
        Matcher tissue = TISSUE.matcher(label);
        if (tissue.matches()) return "TM-" + tissue.group(1);
        return null;
    }

    /** 줄의 항목: 0 = 생산량(톤), 1 = 가동시간(Hr), -1 = 읽지 않는 줄. */
    private static int metricOf(Row row, int groupCol) {
        for (int c = Math.max(groupCol, 0); c < Math.max(groupCol, 0) + LABEL_COLS; c += 1) {
            String label = key(text(row.getCell(c)));
            if (label.isEmpty()) continue;
            if (label.contains("생산성")) return -1;
            if (label.contains("가동시간")) return 1;
            if (label.contains("생산량") || label.contains("점보롤")) return 0;
        }
        return -1;
    }

    private static BigDecimal number(Cell cell, int scale) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type != CellType.NUMERIC) return null;
        double v = cell.getNumericCellValue();
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    private static String text(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) return FORMATTER.formatCellValue(cell).trim();
        return cell.getCachedFormulaResultType() == CellType.STRING ? cell.getStringCellValue().trim() : "";
    }

    /** 공백·줄바꿈을 지운 글자 — "제지 2" 와 "제지2" 를 같게 본다. */
    private static String key(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[\\s\\u00A0]+", "");
    }
}
