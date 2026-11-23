package com.company.module.steamenergy.service.excel;

import com.company.module.steamenergy.dto.SteamFuelDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamLngDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageResponse;
import com.company.module.steamenergy.legacy.service.HeaderColumnIndex;
import com.company.module.steamenergy.legacy.service.SteamUnitLngLayout;
import com.company.module.steamenergy.legacy.service.SteamUnitLogColumnMap;
import com.company.module.steamenergy.legacy.service.SteamUnitSectionLayout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 네 표의 응답 목록 → 원단위 일지 월 시트("N월일지"). DB 는 모른다 (순수 POI).
 *
 * <p>칸 자리는 {@link SteamUnitLogWorkbookReader} 와 같은 배치 클래스로 찾는다. 서식의 수식 칸은 건드리지 않는다
 * (배치가 돌려주는 칸은 모두 입력 칸이다). 데이터가 없는 날은 비운다.
 */
public final class SteamUnitLogWorkbookWriter {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^1\\.\\s*호기별 생산량당.*");
    private static final Pattern MONTH_TOTAL_PATTERN = Pattern.compile("^\\d{1,2}\\s*월\\s*누계$");
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{1,2}\\s*월$");

    /** 2번 구역 (표 이름:col_index) → 호기 + 값 */
    private static final Map<String, String> USAGE_MACHINE = new LinkedHashMap<>();
    private static final Map<String, Function<SteamUnitDailyUsageResponse, Object>> USAGE_GETTER = new LinkedHashMap<>();
    static {
        usage("usage_m_pm2:1", "PM-2", SteamUnitDailyUsageResponse::getRuntimeHours);
        usage("usage_m_pm2:3", "PM-2", SteamUnitDailyUsageResponse::getNote);
        usage("usage_m_pm3a:1", "PM-3", SteamUnitDailyUsageResponse::getRuntimeHours);
        usage("usage_m_pm3a:3", "PM-3", SteamUnitDailyUsageResponse::getNote);
        usage("usage_m_pm3b:1", "PM-3", SteamUnitDailyUsageResponse::getDisperserRuntimeHours);
        usage("usage_m_pm3b:5", "PM-3", SteamUnitDailyUsageResponse::getVentRuntimeHours);
        usage("usage_m_tm:1", "TM-3", SteamUnitDailyUsageResponse::getRuntimeHours);
        usage("usage_m_tm:4", "TM-4", SteamUnitDailyUsageResponse::getRuntimeHours);
        usage("usage_m_tm:7", "TM-5", SteamUnitDailyUsageResponse::getRuntimeHours);
    }
    private static void usage(String key, String machine, Function<SteamUnitDailyUsageResponse, Object> getter) {
        USAGE_MACHINE.put(key, machine);
        USAGE_GETTER.put(key, getter);
    }
    private static final String TISSUE_RUNTIME_KEY = "usage_m_tissue:1";   // TM-3+4+5 가동시간 합 (서식이 입력 칸일 때만)

    private static final Map<String, Function<SteamLngDailyUsageResponse, BigDecimal>> LNG_GETTER = new LinkedHashMap<>();
    static {
        LNG_GETTER.put("lng_m_boiler:0", SteamLngDailyUsageResponse::getBoiler20t1);
        LNG_GETTER.put("lng_m_boiler:1", SteamLngDailyUsageResponse::getBoiler20t2);
        LNG_GETTER.put("lng_m_boiler:2", SteamLngDailyUsageResponse::getBoiler50t);
        LNG_GETTER.put("lng_m_boiler:3", SteamLngDailyUsageResponse::getBoiler60t);
        LNG_GETTER.put("lng_m_boiler:7", SteamLngDailyUsageResponse::getBoilerCombined);
        LNG_GETTER.put("lng_m_boiler:8", SteamLngDailyUsageResponse::getBoilerNewIncinerator);
        LNG_GETTER.put("lng_m_boiler:5", SteamLngDailyUsageResponse::getBoilerUnitPrice);
        LNG_GETTER.put("lng_m_burner:0", SteamLngDailyUsageResponse::getBurnerPm3);
        LNG_GETTER.put("lng_m_burner:1", SteamLngDailyUsageResponse::getBurnerTm3);
        LNG_GETTER.put("lng_m_burner:2", SteamLngDailyUsageResponse::getBurnerTm4);
        LNG_GETTER.put("lng_m_burner:3", SteamLngDailyUsageResponse::getBurnerTm5);
        LNG_GETTER.put("lng_m_burner:6", SteamLngDailyUsageResponse::getBurnerUnitPrice);
        LNG_GETTER.put("lng_m_mix:0", SteamLngDailyUsageResponse::getMixPm2);
        LNG_GETTER.put("lng_m_mix:1", SteamLngDailyUsageResponse::getMixPm3);
        LNG_GETTER.put("lng_m_mix:3", SteamLngDailyUsageResponse::getMixTm3);
        LNG_GETTER.put("lng_m_mix:4", SteamLngDailyUsageResponse::getMixTm4);
        LNG_GETTER.put("lng_m_mix:5", SteamLngDailyUsageResponse::getMixTm5);
    }

    private static final Map<String, Function<SteamFuelDailyUsageResponse, BigDecimal>> FUEL_GETTER = new LinkedHashMap<>();
    static {
        FUEL_GETTER.put("flow_m_fluid_incinerator:0", SteamFuelDailyUsageResponse::getFluidSteamTon);
        FUEL_GETTER.put("flow_m_fluid_incinerator:1", SteamFuelDailyUsageResponse::getFluidUnitPrice);
        FUEL_GETTER.put("flow_m_fluid_incinerator:3", SteamFuelDailyUsageResponse::getIncineratorSteamTon);
        FUEL_GETTER.put("flow_m_fluid_incinerator:4", SteamFuelDailyUsageResponse::getIncineratorUnitPrice);
        FUEL_GETTER.put("flow_m_combined:0", SteamFuelDailyUsageResponse::getCombinedSteamTon);
        FUEL_GETTER.put("flow_m_combined:1", SteamFuelDailyUsageResponse::getCombinedUnitPrice);
        FUEL_GETTER.put("flow_m_combined:3", SteamFuelDailyUsageResponse::getCombinedDeduction);
        FUEL_GETTER.put("flow_m_boiler:0", SteamFuelDailyUsageResponse::getExtBoilerSteamTon);
        FUEL_GETTER.put("flow_m_boiler:1", SteamFuelDailyUsageResponse::getExtBoilerUnitPrice);
        FUEL_GETTER.put("flow_m_boiler:3", SteamFuelDailyUsageResponse::getExtBoilerDeduction);
    }

    /**
     * 한 달치 데이터를 시트에 채운다.
     *
     * @param unitRows   그 달의 호기별 실적 (어느 순서든)
     * @param commonRows 그 달의 공통 계측값
     * @param lngRows    그 달의 LNG
     * @param fuelRows   그 달의 스팀 구매량
     */
    public void fill(Sheet sheet, int year, int monthNo,
                     List<SteamUnitDailyUsageResponse> unitRows, List<SteamUnitDailyCommonResponse> commonRows,
                     List<SteamLngDailyUsageResponse> lngRows, List<SteamFuelDailyUsageResponse> fuelRows) {
        Map<String, SteamUnitDailyUsageResponse> unitByKey = new LinkedHashMap<>();
        for (SteamUnitDailyUsageResponse r : unitRows) unitByKey.put(r.getUsageDate().getDayOfMonth() + "|" + r.getMachineCode(), r);
        Map<Integer, SteamUnitDailyCommonResponse> commonByDay = new LinkedHashMap<>();
        for (SteamUnitDailyCommonResponse r : commonRows) commonByDay.put(r.getUsageDate().getDayOfMonth(), r);
        Map<Integer, SteamLngDailyUsageResponse> lngByDay = new LinkedHashMap<>();
        for (SteamLngDailyUsageResponse r : lngRows) lngByDay.put(r.getUsageDate().getDayOfMonth(), r);
        Map<Integer, SteamFuelDailyUsageResponse> fuelByDay = new LinkedHashMap<>();
        for (SteamFuelDailyUsageResponse r : fuelRows) fuelByDay.put(r.getUsageDate().getDayOfMonth(), r);

        // 제목 칸도 번호가 아니라 글자로 찾는다 — 표 제목 / 월 누계 표 / 월 요약 표 머리글
        setLabel(sheet, TITLE_PATTERN, false, String.format("1. 호기별 생산량당 스팀사용 원단위 (%d.%d )", year, monthNo));
        setLabel(sheet, MONTH_TOTAL_PATTERN, true, monthNo + "월 누계");
        setLabel(sheet, MONTH_PATTERN, true, monthNo + "월");

        fillUnitSection(sheet, unitByKey, commonByDay);
        fillUsageSection(sheet, unitByKey);
        fillLngSection(sheet, lngByDay);
        fillFuelSection(sheet, fuelByDay);
    }

    private void fillUnitSection(Sheet sheet, Map<String, SteamUnitDailyUsageResponse> unitByKey,
                                 Map<Integer, SteamUnitDailyCommonResponse> commonByDay) {
        HeaderColumnIndex index = SteamUnitSectionLayout.unitHeaderIndex(sheet);
        if (index == null) return;
        Map<String, Map<String, Integer>> machineCols = new LinkedHashMap<>();
        for (String machine : SteamUnitLogWorkbookReader.MACHINES) machineCols.put(machine, SteamUnitLogColumnMap.columns(index, machine));
        Map<String, Integer> rawCols = SteamUnitLogColumnMap.rawColumns(index);
        int[] allCols = SteamUnitLogColumnMap.allColumns(machineCols.get("PM-2"), machineCols.get("PM-3"),
                machineCols.get("TM-3"), machineCols.get("TM-4"), machineCols.get("TM-5"), rawCols);

        for (int day = 1; day <= 31; day += 1) {
            Integer rowIndex = index.rowOfDay(day);
            Row row = rowIndex == null ? null : sheet.getRow(rowIndex);
            if (row == null) continue;
            clearCells(row, allCols);
            for (String machine : SteamUnitLogWorkbookReader.MACHINES) {
                SteamUnitDailyUsageResponse r = unitByKey.get(day + "|" + machine);
                Map<String, Integer> cols = machineCols.get(machine);
                setValueAt(row, cols.get("main_steam"), r == null ? null : r.getMainSteam());
                setValueAt(row, cols.get("coater_steam"), r == null ? null : r.getCoaterSteam());
                setValueAt(row, cols.get("ventilation_steam"), r == null ? null : r.getVentilationSteam());
                setValueAt(row, cols.get("steam"), r == null ? null : r.getSteam());
                setValueAt(row, cols.get("production"), r == null ? null : r.getProductionKg());
                setValueAt(row, cols.get("production_type"), r == null ? null : r.getProductionType());
            }
            SteamUnitDailyCommonResponse c = commonByDay.get(day);
            setValueAt(row, rawCols.get("disperser_total"), c == null ? null : c.getDisperserTotal());
            setValueAt(row, rawCols.get("toc_steam"), c == null ? null : c.getTocSteam());
            setValueAt(row, rawCols.get("pica121_vent"), c == null ? null : c.getPica121Vent());
        }
    }

    private void fillUsageSection(Sheet sheet, Map<String, SteamUnitDailyUsageResponse> unitByKey) {
        SteamUnitSectionLayout layout = SteamUnitSectionLayout.usage(sheet);
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            Row row = sheet.getRow(day.getValue());
            if (row == null) continue;
            for (String table : layout.tables()) {
                for (Map.Entry<Integer, Integer> column : layout.columns(table).entrySet()) {
                    String key = table + ":" + column.getKey();
                    Object value = null;
                    if (USAGE_GETTER.containsKey(key)) {
                        SteamUnitDailyUsageResponse r = unitByKey.get(day.getKey() + "|" + USAGE_MACHINE.get(key));
                        value = r == null ? null : USAGE_GETTER.get(key).apply(r);
                    } else if (TISSUE_RUNTIME_KEY.equals(key)) {
                        value = tissueRuntime(unitByKey, day.getKey());
                    }
                    setValueAt(row, column.getValue(), value);
                }
            }
        }
    }

    /** 화장지 총 합계 가동시간 = TM-3 + TM-4 + TM-5 (하나라도 있을 때) */
    private static BigDecimal tissueRuntime(Map<String, SteamUnitDailyUsageResponse> unitByKey, int day) {
        BigDecimal sum = null;
        for (String machine : List.of("TM-3", "TM-4", "TM-5")) {
            SteamUnitDailyUsageResponse r = unitByKey.get(day + "|" + machine);
            if (r == null || r.getRuntimeHours() == null) continue;
            sum = (sum == null ? BigDecimal.ZERO : sum).add(r.getRuntimeHours());
        }
        return sum;
    }

    private void fillLngSection(Sheet sheet, Map<Integer, SteamLngDailyUsageResponse> lngByDay) {
        SteamUnitLngLayout layout = SteamUnitLngLayout.of(sheet);
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            Row row = sheet.getRow(day.getValue());
            if (row == null) continue;
            SteamLngDailyUsageResponse r = lngByDay.get(day.getKey());
            for (String table : SteamUnitLogWorkbookReader.LNG_TABLES) {
                for (Map.Entry<Integer, Integer> column : layout.columns(table).entrySet()) {
                    Function<SteamLngDailyUsageResponse, BigDecimal> getter = LNG_GETTER.get(table + ":" + column.getKey());
                    setValueAt(row, column.getValue(), (getter == null || r == null) ? null : getter.apply(r));
                }
            }
        }
    }

    private void fillFuelSection(Sheet sheet, Map<Integer, SteamFuelDailyUsageResponse> fuelByDay) {
        SteamUnitSectionLayout layout = SteamUnitSectionLayout.flow(sheet);
        for (Map.Entry<Integer, Integer> day : layout.dayRows().entrySet()) {
            Row row = sheet.getRow(day.getValue());
            if (row == null) continue;
            SteamFuelDailyUsageResponse r = fuelByDay.get(day.getKey());
            for (String table : layout.tables()) {
                for (Map.Entry<Integer, Integer> column : layout.columns(table).entrySet()) {
                    Function<SteamFuelDailyUsageResponse, BigDecimal> getter = FUEL_GETTER.get(table + ":" + column.getKey());
                    setValueAt(row, column.getValue(), (getter == null || r == null) ? null : getter.apply(r));
                }
            }
        }
    }

    // ── 칸 쓰기 ─────────────────────────────────────────────────────────

    /** A열에서 규칙에 맞는 제목 칸을 찾아 글자를 바꾼다. 못 찾으면 그대로 둔다. */
    private static void setLabel(Sheet sheet, Pattern pattern, boolean unitInC, String value) {
        int rowIndex = SteamUnitSectionLayout.findLabelRow(sheet, pattern, unitInC);
        if (rowIndex < 0) return;
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        cellAt(row, 0).setCellValue(value);
    }

    /** 머리글에서 찾은 열(null 이면 건너뜀)에 값을 쓴다. 숫자는 숫자로, 문자는 문자로, 없으면 비운다. */
    private static void setValueAt(Row row, Integer columnIndex, Object value) {
        if (columnIndex == null) return;
        Cell cell = cellAt(row, columnIndex);
        if (value == null || String.valueOf(value).isBlank()) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private static void clearCells(Row row, int... columnIndexes) {
        for (int columnIndex : columnIndexes) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null) cell.setBlank();
        }
    }

    private static Cell cellAt(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return cell != null ? cell : row.createCell(columnIndex);
    }
}
