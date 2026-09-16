package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.db.TableService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SteamUnitExcelExportService {
    private static final int DAILY_START_ROW = 5;
    private static final int DAILY_END_ROW = 35;
    private static final String TEMPLATE_FILE = "1. 원단위 일지 누계(2025년).xls";

    private final TableService tableService;

    public SteamUnitExcelExportService(TableService tableService) {
        this.tableService = tableService;
    }

    public byte[] exportWorkbook(int year) throws IOException {
        Path templatePath = resolveTemplatePath();
        try (InputStream inputStream = Files.newInputStream(templatePath);
             HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            workbook.setForceFormulaRecalculation(true);

            for (int month = 1; month <= 12; month += 1) {
                String monthKey = String.format("%04d-%02d", year, month);
                Sheet sheet = workbook.getSheet(month + "월일지");
                if (sheet == null) {
                    continue;
                }
                fillMonthlySheet(sheet, year, month, monthKey);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void fillMonthlySheet(Sheet sheet, int year, int monthNo, String monthKey) {
        List<Map<String, Object>> unitRows = tableService.listAllByMonth("unit_usage", monthKey);
        List<Map<String, Object>> rawRows = tableService.listAllByMonth("unit_usage_raw", monthKey);
        List<Map<String, Object>> cellRows = tableService.listAllByMonth("table_cell_value", monthKey);

        Map<String, Map<String, Object>> unitByKey = new HashMap<>();
        for (Map<String, Object> row : unitRows) {
            unitByKey.put(row.get("machine_no") + ":" + row.get("day"), row);
        }

        Map<String, Map<String, Object>> rawByDay = new HashMap<>();
        for (Map<String, Object> row : rawRows) {
            rawByDay.put(String.valueOf(row.get("day")), row);
        }

        Map<String, Object> cellByKey = new HashMap<>();
        for (Map<String, Object> row : cellRows) {
            cellByKey.put(cellKey(row.get("table_name"), row.get("row_key"), row.get("col_index")), row.get("cell_value"));
        }

        setString(sheet, 0, 0, String.format("1. 호기별 생산량당 스팀사용 원단위 (%d.%d )", year, monthNo));
        setString(sheet, 154, 0, monthNo + "월 누계");
        setString(sheet, 168, 0, monthNo + "월");

        fillUnitSection(sheet, unitByKey, rawByDay);
        fillUsageSection(sheet, cellByKey);
        fillLngSection(sheet, cellByKey);
        fillFlowSection(sheet, cellByKey);
    }

    private void fillUnitSection(Sheet sheet, Map<String, Map<String, Object>> unitByKey, Map<String, Map<String, Object>> rawByDay) {
        for (int rowIndex = DAILY_START_ROW; rowIndex <= DAILY_END_ROW; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int day = rowIndex - DAILY_START_ROW + 1;
            clearCells(row, 1, 2, 4, 6, 7, 8, 9, 10, 12, 14, 18, 19, 21, 22, 24, 25, 34, 35);

            Map<String, Object> pm2 = unitByKey.get("PM-2:" + day);
            Map<String, Object> pm3 = unitByKey.get("PM-3:" + day);
            Map<String, Object> tm3 = unitByKey.get("TM-3:" + day);
            Map<String, Object> tm4 = unitByKey.get("TM-4:" + day);
            Map<String, Object> tm5 = unitByKey.get("TM-5:" + day);
            Map<String, Object> raw = rawByDay.get(String.valueOf(day));

            setNumber(row, 1, pm2, "main_steam");
            setNumber(row, 2, pm2, "coater_steam");
            setNumber(row, 4, pm2, "production");
            setString(row, 6, pm2, "production_type");

            setNumber(row, 7, pm3, "main_steam");
            setNumber(row, 8, pm3, "coater_steam");
            setNumber(row, 9, raw, "disperser_total");
            setNumber(row, 10, pm3, "ventilation_steam");
            setNumber(row, 12, pm3, "production");
            setString(row, 14, pm3, "production_type");

            setNumber(row, 18, tm3, "steam");
            setNumber(row, 19, tm3, "production");
            setNumber(row, 21, tm4, "steam");
            setNumber(row, 22, tm4, "production");
            setNumber(row, 24, tm5, "steam");
            setNumber(row, 25, tm5, "production");

            setNumber(row, 34, raw, "toc_steam");
            setNumber(row, 35, raw, "pica121_vent");
        }
    }

    private void fillUsageSection(Sheet sheet, Map<String, Object> cellByKey) {
        for (int rowIndex = 42; rowIndex <= 72; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String rowKey = String.format("%02d", rowIndex - 41);
            clearCells(row, 2, 4, 6, 8, 10, 13, 19, 22, 25, 28);

            setValue(row, 2, cellByKey.get(cellKey("usage_m_pm2", rowKey, 1)));
            setValue(row, 4, cellByKey.get(cellKey("usage_m_pm2", rowKey, 3)));
            setValue(row, 6, cellByKey.get(cellKey("usage_m_pm3a", rowKey, 1)));
            setValue(row, 8, cellByKey.get(cellKey("usage_m_pm3a", rowKey, 3)));
            setValue(row, 10, cellByKey.get(cellKey("usage_m_pm3b", rowKey, 1)));
            setValue(row, 13, cellByKey.get(cellKey("usage_m_pm3b", rowKey, 5)));
            setValue(row, 19, cellByKey.get(cellKey("usage_m_tm", rowKey, 1)));
            setValue(row, 22, cellByKey.get(cellKey("usage_m_tm", rowKey, 4)));
            setValue(row, 25, cellByKey.get(cellKey("usage_m_tm", rowKey, 7)));
            setValue(row, 28, cellByKey.get(cellKey("usage_m_tissue", rowKey, 1)));
        }
    }

    private void fillLngSection(Sheet sheet, Map<String, Object> cellByKey) {
        for (int rowIndex = 80; rowIndex <= 110; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String rowKey = String.format("%02d", rowIndex - 79);
            clearCells(row, 1, 2, 3, 4, 6, 8, 9, 10, 11, 14, 19, 20, 22, 23, 24);

            setValue(row, 1, cellByKey.get(cellKey("lng_m_boiler", rowKey, 0)));
            setValue(row, 2, cellByKey.get(cellKey("lng_m_boiler", rowKey, 1)));
            setValue(row, 3, cellByKey.get(cellKey("lng_m_boiler", rowKey, 2)));
            setValue(row, 4, cellByKey.get(cellKey("lng_m_boiler", rowKey, 3)));
            setValue(row, 6, cellByKey.get(cellKey("lng_m_boiler", rowKey, 5)));

            setValue(row, 8, cellByKey.get(cellKey("lng_m_burner", rowKey, 0)));
            setValue(row, 9, cellByKey.get(cellKey("lng_m_burner", rowKey, 1)));
            setValue(row, 10, cellByKey.get(cellKey("lng_m_burner", rowKey, 2)));
            setValue(row, 11, cellByKey.get(cellKey("lng_m_burner", rowKey, 3)));
            setValue(row, 14, cellByKey.get(cellKey("lng_m_burner", rowKey, 6)));

            setValue(row, 19, cellByKey.get(cellKey("lng_m_mix", rowKey, 0)));
            setValue(row, 20, cellByKey.get(cellKey("lng_m_mix", rowKey, 1)));
            setValue(row, 22, cellByKey.get(cellKey("lng_m_mix", rowKey, 3)));
            setValue(row, 23, cellByKey.get(cellKey("lng_m_mix", rowKey, 4)));
            setValue(row, 24, cellByKey.get(cellKey("lng_m_mix", rowKey, 5)));
        }
    }

    private void fillFlowSection(Sheet sheet, Map<String, Object> cellByKey) {
        for (int rowIndex = 119; rowIndex <= 149; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String rowKey = String.format("%02d", rowIndex - 118);
            clearCells(row, 1, 2, 4, 5, 7, 8, 10, 12, 13, 15);

            setValue(row, 1, cellByKey.get(cellKey("flow_m_fluid_incinerator", rowKey, 0)));
            setValue(row, 2, cellByKey.get(cellKey("flow_m_fluid_incinerator", rowKey, 1)));
            setValue(row, 4, cellByKey.get(cellKey("flow_m_fluid_incinerator", rowKey, 3)));
            setValue(row, 5, cellByKey.get(cellKey("flow_m_fluid_incinerator", rowKey, 4)));

            setValue(row, 7, cellByKey.get(cellKey("flow_m_combined", rowKey, 0)));
            setValue(row, 8, cellByKey.get(cellKey("flow_m_combined", rowKey, 1)));
            setValue(row, 10, cellByKey.get(cellKey("flow_m_combined", rowKey, 3)));

            setValue(row, 12, cellByKey.get(cellKey("flow_m_boiler", rowKey, 0)));
            setValue(row, 13, cellByKey.get(cellKey("flow_m_boiler", rowKey, 1)));
            setValue(row, 15, cellByKey.get(cellKey("flow_m_boiler", rowKey, 3)));
        }
    }

    private void setNumber(Row row, int columnIndex, Map<String, Object> data, String key) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        Object value = data == null ? null : data.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(new BigDecimal(String.valueOf(value)).doubleValue());
    }

    private void setString(Row row, int columnIndex, Map<String, Object> data, String key) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        Object value = data == null ? null : data.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private void setString(Sheet sheet, int rowIndex, int columnIndex, String value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        cell.setCellValue(value);
    }

    private void setValue(Row row, int columnIndex, Object value) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
            return;
        }

        String text = String.valueOf(value).replace(",", "").trim();
        try {
            cell.setCellValue(new BigDecimal(text).doubleValue());
        } catch (NumberFormatException exception) {
            cell.setCellValue(text);
        }
    }

    private void clearCells(Row row, int... columnIndexes) {
        for (int columnIndex : columnIndexes) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null) {
                cell.setBlank();
            }
        }
    }

    private String cellKey(Object tableName, Object rowKey, Object colIndex) {
        return String.valueOf(tableName) + ":" + String.valueOf(rowKey) + ":" + String.valueOf(colIndex);
    }

    private Path resolveTemplatePath() {
        Path direct = Path.of("").toAbsolutePath().resolve(TEMPLATE_FILE);
        if (Files.exists(direct)) {
            return direct;
        }
        throw new IllegalStateException("Excel template file not found: " + TEMPLATE_FILE);
    }
}
