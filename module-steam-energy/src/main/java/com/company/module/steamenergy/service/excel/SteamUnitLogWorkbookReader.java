package com.company.module.steamenergy.service.excel;

import com.company.module.steamenergy.dto.SteamFuelDailyUsageSaveRequest;
import com.company.module.steamenergy.dto.SteamLngDailyUsageSaveRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonSaveRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageSaveRequest;
import com.company.module.steamenergy.legacy.service.HeaderColumnIndex;
import com.company.module.steamenergy.legacy.service.SteamUnitLngLayout;
import com.company.module.steamenergy.legacy.service.SteamUnitLogColumnMap;
import com.company.module.steamenergy.legacy.service.SteamUnitSectionLayout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 원단위 일지 월 시트("N월일지") → 네 표의 저장 요청. DB 는 모른다 (순수 POI).
 *
 * <p>열은 제목으로 찾는다 — 1번 구역은 {@link HeaderColumnIndex}+{@link SteamUnitLogColumnMap},
 * 2·4번 구역은 {@link SteamUnitSectionLayout}, 3번 LNG 구역은 {@link SteamUnitLngLayout}.
 * 세 배치 클래스는 예전 셀 저장소의 (표 이름, col_index) 로 칸을 돌려주므로 여기서 새 표의 항목으로 바꾼다.
 *
 * <p>읽기 정책(예전 가져오기와 같다): 수식 칸은 건너뛴다. 단, 디스퍼져 총량·TOC·PICA 는 원본에서 수식 칸이고
 * 그 계산 결과가 유일한 데이터원이라 캐시된 숫자를 읽는다. "-" 는 0 으로 본다.
 */
public final class SteamUnitLogWorkbookReader {

    private final DataFormatter dataFormatter = new DataFormatter();

    /** 2번 구역 (표 이름:col_index) → 호기 + 항목 */
    private static final Map<String, BiConsumer<SteamUnitDailyUsageSaveRequest, BigDecimal>> USAGE_FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> USAGE_MACHINE = new LinkedHashMap<>();
    static {
        usage("usage_m_pm2:1", "PM-2", SteamUnitDailyUsageSaveRequest::setRuntimeHours);
        usage("usage_m_pm3a:1", "PM-3", SteamUnitDailyUsageSaveRequest::setRuntimeHours);
        usage("usage_m_pm3b:1", "PM-3", SteamUnitDailyUsageSaveRequest::setDisperserRuntimeHours);
        usage("usage_m_pm3b:5", "PM-3", SteamUnitDailyUsageSaveRequest::setVentRuntimeHours);
        usage("usage_m_tm:1", "TM-3", SteamUnitDailyUsageSaveRequest::setRuntimeHours);
        usage("usage_m_tm:4", "TM-4", SteamUnitDailyUsageSaveRequest::setRuntimeHours);
        usage("usage_m_tm:7", "TM-5", SteamUnitDailyUsageSaveRequest::setRuntimeHours);
        // usage_m_pm2:3 / usage_m_pm3a:3 = 비고(문자) — 아래 NOTE_FIELDS. usage_m_tissue:1 = TM 합(계산) — 읽지 않는다.
    }
    private static final Map<String, String> NOTE_FIELDS = Map.of("usage_m_pm2:3", "PM-2", "usage_m_pm3a:3", "PM-3");

    private static void usage(String key, String machine, BiConsumer<SteamUnitDailyUsageSaveRequest, BigDecimal> setter) {
        USAGE_FIELDS.put(key, setter);
        USAGE_MACHINE.put(key, machine);
    }

    /** 3번 LNG 구역 (표 이름:col_index) → 항목 */
    static final Map<String, BiConsumer<SteamLngDailyUsageSaveRequest, BigDecimal>> LNG_FIELDS = new LinkedHashMap<>();
    static {
        LNG_FIELDS.put("lng_m_boiler:0", SteamLngDailyUsageSaveRequest::setBoiler20t1);
        LNG_FIELDS.put("lng_m_boiler:1", SteamLngDailyUsageSaveRequest::setBoiler20t2);
        LNG_FIELDS.put("lng_m_boiler:2", SteamLngDailyUsageSaveRequest::setBoiler50t);
        LNG_FIELDS.put("lng_m_boiler:3", SteamLngDailyUsageSaveRequest::setBoiler60t);
        LNG_FIELDS.put("lng_m_boiler:7", SteamLngDailyUsageSaveRequest::setBoilerCombined);
        LNG_FIELDS.put("lng_m_boiler:8", SteamLngDailyUsageSaveRequest::setBoilerNewIncinerator);
        LNG_FIELDS.put("lng_m_boiler:5", SteamLngDailyUsageSaveRequest::setBoilerUnitPrice);
        LNG_FIELDS.put("lng_m_burner:0", SteamLngDailyUsageSaveRequest::setBurnerPm3);
        LNG_FIELDS.put("lng_m_burner:1", SteamLngDailyUsageSaveRequest::setBurnerTm3);
        LNG_FIELDS.put("lng_m_burner:2", SteamLngDailyUsageSaveRequest::setBurnerTm4);
        LNG_FIELDS.put("lng_m_burner:3", SteamLngDailyUsageSaveRequest::setBurnerTm5);
        LNG_FIELDS.put("lng_m_burner:6", SteamLngDailyUsageSaveRequest::setBurnerUnitPrice);
        LNG_FIELDS.put("lng_m_mix:0", SteamLngDailyUsageSaveRequest::setMixPm2);
        LNG_FIELDS.put("lng_m_mix:1", SteamLngDailyUsageSaveRequest::setMixPm3);
        LNG_FIELDS.put("lng_m_mix:3", SteamLngDailyUsageSaveRequest::setMixTm3);
        LNG_FIELDS.put("lng_m_mix:4", SteamLngDailyUsageSaveRequest::setMixTm4);
        LNG_FIELDS.put("lng_m_mix:5", SteamLngDailyUsageSaveRequest::setMixTm5);
    }
    static final List<String> LNG_TABLES = List.of("lng_m_boiler", "lng_m_burner", "lng_m_mix");

    /** 4번 구역 (표 이름:col_index) → 항목 */
    static final Map<String, BiConsumer<SteamFuelDailyUsageSaveRequest, BigDecimal>> FUEL_FIELDS = new LinkedHashMap<>();
    static {
        FUEL_FIELDS.put("flow_m_fluid_incinerator:0", SteamFuelDailyUsageSaveRequest::setFluidSteamTon);
        FUEL_FIELDS.put("flow_m_fluid_incinerator:1", SteamFuelDailyUsageSaveRequest::setFluidUnitPrice);
        FUEL_FIELDS.put("flow_m_fluid_incinerator:3", SteamFuelDailyUsageSaveRequest::setIncineratorSteamTon);
        FUEL_FIELDS.put("flow_m_fluid_incinerator:4", SteamFuelDailyUsageSaveRequest::setIncineratorUnitPrice);
        FUEL_FIELDS.put("flow_m_combined:0", SteamFuelDailyUsageSaveRequest::setCombinedSteamTon);
        FUEL_FIELDS.put("flow_m_combined:1", SteamFuelDailyUsageSaveRequest::setCombinedUnitPrice);
        FUEL_FIELDS.put("flow_m_combined:3", SteamFuelDailyUsageSaveRequest::setCombinedDeduction);
        FUEL_FIELDS.put("flow_m_boiler:0", SteamFuelDailyUsageSaveRequest::setExtBoilerSteamTon);
        FUEL_FIELDS.put("flow_m_boiler:1", SteamFuelDailyUsageSaveRequest::setExtBoilerUnitPrice);
        FUEL_FIELDS.put("flow_m_boiler:3", SteamFuelDailyUsageSaveRequest::setExtBoilerDeduction);
    }

    public static final List<String> MACHINES = List.of("PM-2", "PM-3", "TM-3", "TM-4", "TM-5");

    public UnitLogSheetData read(Sheet sheet, int year, int monthNo) {
        int days = YearMonth.of(year, monthNo).lengthOfMonth();
        Map<String, SteamUnitDailyUsageSaveRequest> unitByKey = new LinkedHashMap<>();   // "day|machine"
        Map<Integer, SteamUnitDailyCommonSaveRequest> commonByDay = new LinkedHashMap<>();
        Map<Integer, SteamLngDailyUsageSaveRequest> lngByDay = new LinkedHashMap<>();
        Map<Integer, SteamFuelDailyUsageSaveRequest> fuelByDay = new LinkedHashMap<>();
        List<String> notFound = new ArrayList<>();

        // ── 1번 구역: 호기별 스팀량·생산량 + 공통 계측값 ──
        HeaderColumnIndex headerIndex = SteamUnitSectionLayout.unitHeaderIndex(sheet);
        if (headerIndex == null) {
            notFound.add("1번 구역 머리글('일자')");
        } else {
            Map<String, Map<String, Integer>> machineCols = new LinkedHashMap<>();
            for (String machine : MACHINES) machineCols.put(machine, SteamUnitLogColumnMap.columns(headerIndex, machine));
            Map<String, Integer> rawCols = SteamUnitLogColumnMap.rawColumns(headerIndex);
            for (int day = 1; day <= days; day += 1) {
                Integer rowIndex = headerIndex.rowOfDay(day);
                Row row = rowIndex == null ? null : sheet.getRow(rowIndex);
                if (row == null) continue;
                for (String machine : MACHINES) readUnitRow(row, day, machine, machineCols.get(machine), unitByKey, year, monthNo);
                readCommonRow(row, day, rawCols, commonByDay, year, monthNo);
            }
        }

        // ── 2번 구역: 가동시간·비고 ──
        SteamUnitSectionLayout usage = SteamUnitSectionLayout.usage(sheet);
        notFound.addAll(usage.notFound());
        for (Map.Entry<Integer, Integer> day : usage.dayRows().entrySet()) {
            if (day.getKey() > days) continue;
            Row row = sheet.getRow(day.getValue());
            if (row == null) continue;
            for (String table : usage.tables()) {
                for (Map.Entry<Integer, Integer> column : usage.columns(table).entrySet()) {
                    String key = table + ":" + column.getKey();
                    Cell cell = row.getCell(column.getValue());
                    if (isFormula(cell)) continue;
                    if (USAGE_FIELDS.containsKey(key)) {
                        BigDecimal value = readNumber(cell);
                        if (value == null) continue;
                        USAGE_FIELDS.get(key).accept(unitRow(unitByKey, day.getKey(), USAGE_MACHINE.get(key), year, monthNo), value);
                    } else if (NOTE_FIELDS.containsKey(key)) {
                        String text = readString(cell);
                        if (text == null) continue;
                        unitRow(unitByKey, day.getKey(), NOTE_FIELDS.get(key), year, monthNo).setNote(text);
                    }
                }
            }
        }

        // ── 3번 구역: LNG ──
        SteamUnitLngLayout lng = SteamUnitLngLayout.of(sheet);
        notFound.addAll(lng.notFound());
        for (Map.Entry<Integer, Integer> day : lng.dayRows().entrySet()) {
            if (day.getKey() > days) continue;
            Row row = sheet.getRow(day.getValue());
            if (row == null) continue;
            for (String table : LNG_TABLES) {
                for (Map.Entry<Integer, Integer> column : lng.columns(table).entrySet()) {
                    BiConsumer<SteamLngDailyUsageSaveRequest, BigDecimal> setter = LNG_FIELDS.get(table + ":" + column.getKey());
                    if (setter == null) continue;
                    Cell cell = row.getCell(column.getValue());
                    if (isFormula(cell)) continue;
                    BigDecimal value = readNumber(cell);
                    if (value == null) continue;
                    setter.accept(lngByDay.computeIfAbsent(day.getKey(), d -> lngRow(year, monthNo, d)), value);
                }
            }
        }

        // ── 4번 구역: 유동상·소각로·복합·외부 ──
        SteamUnitSectionLayout flow = SteamUnitSectionLayout.flow(sheet);
        notFound.addAll(flow.notFound());
        for (Map.Entry<Integer, Integer> day : flow.dayRows().entrySet()) {
            if (day.getKey() > days) continue;
            Row row = sheet.getRow(day.getValue());
            if (row == null) continue;
            for (String table : flow.tables()) {
                for (Map.Entry<Integer, Integer> column : flow.columns(table).entrySet()) {
                    BiConsumer<SteamFuelDailyUsageSaveRequest, BigDecimal> setter = FUEL_FIELDS.get(table + ":" + column.getKey());
                    if (setter == null) continue;
                    Cell cell = row.getCell(column.getValue());
                    if (isFormula(cell)) continue;
                    BigDecimal value = readNumber(cell);
                    if (value == null) continue;
                    setter.accept(fuelByDay.computeIfAbsent(day.getKey(), d -> fuelRow(year, monthNo, d)), value);
                }
            }
        }

        return new UnitLogSheetData(sheet.getSheetName(), year, monthNo,
                new ArrayList<>(unitByKey.values()), new ArrayList<>(commonByDay.values()),
                new ArrayList<>(lngByDay.values()), new ArrayList<>(fuelByDay.values()), notFound);
    }

    private void readUnitRow(Row row, int day, String machine, Map<String, Integer> cols,
                             Map<String, SteamUnitDailyUsageSaveRequest> unitByKey, int year, int monthNo) {
        boolean any = false;
        BigDecimal mainSteam = null, coaterSteam = null, ventilationSteam = null, steam = null, production = null;
        String productionType = null;
        for (Map.Entry<String, Integer> e : cols.entrySet()) {
            Cell cell = row.getCell(e.getValue());
            if (isFormula(cell)) continue;
            switch (e.getKey()) {
                case "main_steam" -> mainSteam = readNumber(cell);
                case "coater_steam" -> coaterSteam = readNumber(cell);
                case "ventilation_steam" -> ventilationSteam = readNumber(cell);
                case "steam" -> steam = readNumber(cell);
                case "production" -> production = readNumber(cell);
                case "production_type" -> productionType = readString(cell);
                default -> { continue; }
            }
            any = any || mainSteam != null || coaterSteam != null || ventilationSteam != null || steam != null
                    || production != null || productionType != null;
        }
        if (!any) return;
        SteamUnitDailyUsageSaveRequest r = unitRow(unitByKey, day, machine, year, monthNo);
        if (mainSteam != null) r.setMainSteam(mainSteam);
        if (coaterSteam != null) r.setCoaterSteam(coaterSteam);
        if (ventilationSteam != null) r.setVentilationSteam(ventilationSteam);
        if (steam != null) r.setSteam(steam);
        if (production != null) r.setProductionKg(production);
        if (productionType != null) r.setProductionType(productionType);
    }

    private void readCommonRow(Row row, int day, Map<String, Integer> rawCols,
                               Map<Integer, SteamUnitDailyCommonSaveRequest> commonByDay, int year, int monthNo) {
        BigDecimal disperser = null, toc = null, pica = null;
        if (rawCols.containsKey("disperser_total")) disperser = readNumberAllowFormula(row.getCell(rawCols.get("disperser_total")));
        if (rawCols.containsKey("toc_steam")) toc = readNumberAllowFormula(row.getCell(rawCols.get("toc_steam")));
        if (rawCols.containsKey("pica121_vent")) pica = readNumberAllowFormula(row.getCell(rawCols.get("pica121_vent")));
        if (disperser == null && toc == null && pica == null) return;
        SteamUnitDailyCommonSaveRequest r = new SteamUnitDailyCommonSaveRequest();
        r.setUsageDate(LocalDate.of(year, monthNo, day));
        r.setDisperserTotal(disperser);
        r.setTocSteam(toc);
        r.setPica121Vent(pica);
        commonByDay.put(day, r);
    }

    private static SteamUnitDailyUsageSaveRequest unitRow(Map<String, SteamUnitDailyUsageSaveRequest> map, int day,
                                                          String machine, int year, int monthNo) {
        return map.computeIfAbsent(day + "|" + machine, k -> {
            SteamUnitDailyUsageSaveRequest r = new SteamUnitDailyUsageSaveRequest();
            r.setUsageDate(LocalDate.of(year, monthNo, day));
            r.setMachineCode(machine);
            return r;
        });
    }

    private static SteamLngDailyUsageSaveRequest lngRow(int year, int monthNo, int day) {
        SteamLngDailyUsageSaveRequest r = new SteamLngDailyUsageSaveRequest();
        r.setUsageDate(LocalDate.of(year, monthNo, day));
        return r;
    }

    private static SteamFuelDailyUsageSaveRequest fuelRow(int year, int monthNo, int day) {
        SteamFuelDailyUsageSaveRequest r = new SteamFuelDailyUsageSaveRequest();
        r.setUsageDate(LocalDate.of(year, monthNo, day));
        return r;
    }

    // ── 셀 읽기 (예전 가져오기와 같은 규칙) ──────────────────────────

    private static boolean isFormula(Cell cell) {
        return cell != null && cell.getCellType() == CellType.FORMULA;
    }

    private BigDecimal readNumberAllowFormula(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA) {
            if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
            }
            return null;
        }
        return readNumber(cell);
    }

    private BigDecimal readNumber(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }
        String text = dataFormatter.formatCellValue(cell).replace(",", "").trim();
        if (text.isEmpty()) return null;
        if ("-".equals(text)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        String text = dataFormatter.formatCellValue(cell).trim();
        return text.isEmpty() ? null : text;
    }
}
