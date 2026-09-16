package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SrfBoilerInvoiceWorkbookService {
    private static final String TARGET_SHEET = "Summary(VK)";
    private static final String OPERATION_SHEET = "\uC6B4\uC601\uB0B4\uC5ED";
    private static final String COMBO_PRODUCTION_TABLE = "combo_boiler_production";

    private final TableService tableService;
    private final SheetMetadataLoader metadataLoader;

    public SrfBoilerInvoiceWorkbookService(TableService tableService, SheetMetadataLoader metadataLoader) {
        this.tableService = tableService;
        this.metadataLoader = metadataLoader;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> renderSummary(int year, int month) {
        // Excel \uC758\uC874 \uC81C\uAC70 \u2014 \uC2DC\uD2B8 \uBA54\uD0C0\uB370\uC774\uD130\uB294 classpath JSON \uC5D0\uC11C \uB85C\uB4DC, \uB3D9\uC801 \uAC12\uC740 \uC790\uBC14\uC5D0\uC11C \uCC44\uC6C0.
        Map<String, Object> meta = metadataLoader.load("srf-invoice-summary");
        List<Map<String, Object>> rows = metadataLoader.cloneRows((List<Map<String, Object>>) meta.get("rows"));

        for (Map<String, Object> row : rows) {
            int rowIndex = ((Number) row.get("index")).intValue();
            row.put("titleRow", isWideTitleRow(rowIndex));
            List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("cells");
            for (Map<String, Object> cell : cells) {
                int columnIndex = ((Number) cell.get("col")).intValue();
                String staticValue = String.valueOf(cell.getOrDefault("value", ""));
                Map<String, Object> styleMap = (Map<String, Object>) cell.get("style");
                String format = String.valueOf(cell.getOrDefault("format", ""));
                // Excel \uC758 \uC218\uC2DD \uC140 \uC5EC\uBD80\uB294 \uBA54\uD0C0\uB370\uC774\uD130\uC5D4 \uC5C6\uC74C \u2014 \uC815\uC801 \uB8F0\uB85C \uD310\uC815.
                // \uC785\uB825\uCE78\uACFC \uC790\uB3D9 \uACC4\uC0B0\uCE78\uC758 \uC704\uCE58\uB294 isMarkedInputCell / \uD654\uBA74 \uCE21 calc \uB85C\uC9C1\uC73C\uB85C \uACB0\uC815\uB418\uBA70,
                // \uC5EC\uAE30\uC11C formula=false \uB85C \uB450\uACE0 \uD074\uB77C\uC774\uC5B8\uD2B8 JS \uC758 calculatedCellValue() \uAC00 \uACC4\uC0B0.
                boolean editable = isMarkedInputCell(rowIndex, columnIndex);
                cell.put("row", rowIndex);
                cell.put("col", columnIndex);
                cell.put("value", summaryDisplayValue(staticValue, rowIndex, columnIndex, year, month));
                cell.put("style", SheetMetadataLoader.styleToCss(styleMap));
                cell.put("format", format);
                cell.put("formula", false);
                cell.put("editable", editable);
            }
        }

        // \uCEEC\uB7FC \uD3ED\uC740 \uD074\uB77C\uC774\uC5B8\uD2B8\uAC00 \uB354 \uCEF4\uD329\uD2B8\uD558\uAC8C \uD45C\uC2DC\uD558\uAE38 \uC6D0\uD574 \uBCC4\uB3C4 \uD3ED \uC0AC\uC6A9 (\uBA54\uD0C0 \uD3ED \uBB34\uC2DC).
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < 7; columnIndex += 1) {
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("index", columnIndex);
            column.put("width", compactColumnWidth(columnIndex));
            columns.add(column);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sheet", TARGET_SHEET);
        response.put("year", year);
        response.put("month", month);
        response.put("columns", columns);
        response.put("rows", rows);
        return response;
    }

    /** \uC815\uC801 \uD14D\uC2A4\uD2B8 + \uB3D9\uC801 \uAC12 (\uB0A0\uC9DC, \uC77C\uC218) \uACB0\uD569. */
    private String summaryDisplayValue(String staticValue, int rowIndex, int columnIndex, int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        if (rowIndex == 4 && columnIndex == 3) return formatShortDate(firstDay);
        if (rowIndex == 4 && columnIndex == 5) return formatShortDate(firstDay.withDayOfMonth(firstDay.lengthOfMonth()));
        if (rowIndex == 6 && columnIndex == 3) return formatShortDate(firstDay.withDayOfMonth(firstDay.lengthOfMonth()).plusDays(10));
        if (rowIndex == 8 && columnIndex == 3) return String.valueOf(firstDay.lengthOfMonth());
        // \uC0AC\uC6A9\uC790 \uC785\uB825 \uCE78\uC740 \uBE48 \uAC12\uC73C\uB85C \u2014 \uD074\uB77C\uC774\uC5B8\uD2B8\uAC00 DB \uC758 \uC800\uC7A5\uAC12\uC73C\uB85C \uCC44\uC6C0.
        if (isMarkedInputCell(rowIndex, columnIndex)) return "";
        return staticValue;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> renderOperation(int year, int month) {
        // Excel \uC758\uC874 \uC81C\uAC70 \u2014 \uC2DC\uD2B8 \uBA54\uD0C0\uB370\uC774\uD130(\uC815\uC801 \uBD80\uBD84)\uB294 classpath JSON \uC5D0\uC11C \uB85C\uB4DC.
        // \uB3D9\uC801 \uBD80\uBD84(\uC81C\uBAA9, \uB0A0\uC9DC, combo \uC5F0\uACB0 \uC140)\uC740 \uC790\uBC14 \uB85C\uC9C1\uC73C\uB85C \uCC44\uC6C0.
        String monthKey = monthKey(year, month);
        Map<String, Object> steamValues = comboSteamValues(monthKey);
        Map<String, Object> meta = metadataLoader.load("srf-invoice-operation");
        List<Map<String, Object>> rows = metadataLoader.cloneRows((List<Map<String, Object>>) meta.get("rows"));

        for (Map<String, Object> row : rows) {
            int rowIndex = ((Number) row.get("index")).intValue();
            List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("cells");
            for (Map<String, Object> cell : cells) {
                int columnIndex = ((Number) cell.get("col")).intValue();
                String staticValue = String.valueOf(cell.getOrDefault("value", ""));
                Map<String, Object> styleMap = (Map<String, Object>) cell.get("style");
                String format = String.valueOf(cell.getOrDefault("format", ""));
                boolean formula = isOperationFormulaCell(rowIndex, columnIndex);
                boolean editable = isOperationEditableCell(rowIndex, columnIndex);
                String displayValue = operationDisplayValue(staticValue, rowIndex, columnIndex, year, month, steamValues);
                // \uD074\uB77C\uC774\uC5B8\uD2B8\uAC00 \uAE30\uB300\uD558\uB294 \uD3C9\uBA74 \uD398\uC774\uB85C\uB4DC \uD615\uD0DC\uB85C \uC815\uB9AC
                cell.put("row", rowIndex);
                cell.put("col", columnIndex);
                cell.put("value", displayValue);
                cell.put("style", SheetMetadataLoader.styleToCss(styleMap));
                cell.put("format", format);
                cell.put("formula", formula);
                cell.put("editable", editable);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sheet", OPERATION_SHEET);
        response.put("year", year);
        response.put("month", month);
        response.put("columns", meta.get("columns"));
        response.put("rows", rows);
        response.put("merges", meta.get("merges"));
        return response;
    }

    /**
     * \uC6B4\uC601\uB0B4\uC5ED \uC140\uC758 \uD45C\uC2DC\uAC12 \uACB0\uC815 \u2014 \uB3D9\uC801 \uC140(\uC81C\uBAA9/\uB0A0\uC9DC/combo \uC5F0\uACB0)\uC740 \uC790\uBC14 \uACC4\uC0B0,
     * \uADF8 \uC678 \uC815\uC801 \uB77C\uBCA8\uC740 JSON \uBA54\uD0C0\uB370\uC774\uD130\uC758 value \uB97C \uADF8\uB300\uB85C \uC0AC\uC6A9.
     */
    private String operationDisplayValue(String staticValue, int rowIndex, int columnIndex, int year, int month, Map<String, Object> steamValues) {
        int day = rowIndex - 3;
        int days = LocalDate.of(year, month, 1).lengthOfMonth();
        if (rowIndex == 0 && columnIndex == 2) {
            return "2. " + year + "\uB144 " + month + "\uC6D4 SRF Boiler \uC6B4\uC601\uB0B4\uC5ED";
        }
        if (rowIndex >= 4 && rowIndex <= 34) {
            if (day > days) return "";
            if (columnIndex == 2) return day == 1 ? month + "\uC6D4 " + day + "\uC77C" : String.valueOf(day);
            // \uBCF5\uD569\uBCF4\uC77C\uB7EC \uC2A4\uD300\uC0DD\uC0B0\uC2E4\uC801(combo_boiler_production) \uC758 \uB3D9\uC77C \uC77C\uC790 \uAC12\uC744 \uC6B4\uC601\uB0B4\uC5ED \uC140\uC5D0 \uC5F0\uACB0.
            //   col 3 (\uC2A4\uD300\uC0DD\uC0B0\uB7C9 T/D)  -> comboSteam
            //   col 5 (\uACF5\uC815\uC218 \uC0AC\uC6A9\uB7C9)   -> comboProcess
            //   col 7 (\uD0C8\uAE30\uAE30\uAE09\uC218\uB7C9)    -> comboDeaerator
            if (columnIndex == 3) return String.valueOf(steamValues.getOrDefault("comboSteam-" + day, ""));
            if (columnIndex == 5) return String.valueOf(steamValues.getOrDefault("comboProcess-" + day, ""));
            if (columnIndex == 7) return String.valueOf(steamValues.getOrDefault("comboDeaerator-" + day, ""));
            if (isOperationEditableCell(rowIndex, columnIndex) || isOperationFormulaCell(rowIndex, columnIndex)) return "";
        }
        if (rowIndex == 35 && columnIndex >= 3 && columnIndex <= 11) return "";
        return staticValue;
    }

    private boolean isOperationEditableCell(int rowIndex, int columnIndex) {
        // col 5(공정수), 7(탈기기급수) 는 복합보일러 페이지에서 연결되므로 편집 대상에서 제외.
        return rowIndex >= 4 && rowIndex <= 34 && List.of(9, 10, 11).contains(columnIndex);
    }

    private boolean isOperationFormulaCell(int rowIndex, int columnIndex) {
        if (rowIndex >= 4 && rowIndex <= 34) return List.of(4, 6, 8).contains(columnIndex);
        return rowIndex == 35 && columnIndex >= 3 && columnIndex <= 11;
    }

    private Map<String, Object> comboSteamValues(String monthKey) {
        // 복합보일러 스팀생산실적의 동일 일자 값을 운영내역 셀에 연결하기 위해 필요한 prefix 만 수집.
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map<String, Object> row : tableService.listAllByMonth("table_cell_value", monthKey)) {
            if (!COMBO_PRODUCTION_TABLE.equals(String.valueOf(row.get("table_name")))) continue;
            String rowKey = String.valueOf(row.get("row_key"));
            if (rowKey.startsWith("comboSteam-")
                    || rowKey.startsWith("comboProcess-")
                    || rowKey.startsWith("comboDeaerator-")) {
                values.put(rowKey, row.get("cell_value"));
            }
        }
        return values;
    }

    private String monthKey(int year, int month) {
        return year + "-" + String.format("%02d", month);
    }

    private boolean isMarkedInputCell(int rowIndex, int columnIndex) {
        return (rowIndex == 19 && columnIndex == 3)
                || (rowIndex == 21 && columnIndex == 3)
                || (rowIndex == 17 && columnIndex == 3)
                || (rowIndex == 26 && columnIndex == 3)
                || (rowIndex == 28 && columnIndex == 3)
                || (rowIndex == 30 && columnIndex == 3)
                || (rowIndex == 36 && columnIndex == 3)
                || (rowIndex == 37 && columnIndex == 3)
                || (rowIndex == 43 && columnIndex == 5)
                || (rowIndex == 50 && columnIndex == 3);
    }

    private boolean isWideTitleRow(int rowIndex) {
        return rowIndex == 0 || rowIndex == 2 || rowIndex == 11 || rowIndex == 45 || rowIndex == 48 || rowIndex == 59 || rowIndex == 65;
    }

    private int compactColumnWidth(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> 24;
            case 1 -> 28;
            case 2 -> 255;
            case 3 -> 112;
            case 4 -> 64;
            case 5 -> 112;
            case 6 -> 76;
            default -> 0;
        };
    }

    private String formatShortDate(LocalDate date) {
        return date.toString();
    }
}
