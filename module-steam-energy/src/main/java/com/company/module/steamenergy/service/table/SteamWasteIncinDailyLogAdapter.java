package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamWasteIncinDailyLogBulkRequest;
import com.company.module.steamenergy.dto.SteamWasteIncinDailyLogResponse;
import com.company.module.steamenergy.dto.SteamWasteIncinDailyLogSaveRequest;
import com.company.module.steamenergy.service.SteamWasteIncinDailyLogService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 폐합성소각로 운전일지 — 표 어댑터 (생성 코드: gen_keyed_table.py). 행을 "필드 이름 → 값" 맵으로 다룬다.
 *
 * <p>Table: steam_waste_incin_daily_log / alias: wasteIncinLog
 */
@Component
public class SteamWasteIncinDailyLogAdapter implements SteamTableAdapter {

    private static final List<String> KEY_FIELDS = List.of("usageDate");
    private static final List<String> VALUE_FIELDS = List.of("burnU1Self", "burnU1Recycle", "burnU1Generated", "burnU1RunHours", "burnU1ExtStock", "burnU2Self", "burnU2Recycle", "burnU2Generated", "burnU2RunHours", "burnU2SelfStock", "burnExtTotal", "steamU1", "steamU2First", "steamU2Second", "steamAgency", "steamPressure", "waterU1First", "waterU1Second", "waterU2First", "waterU2Second", "powerMeter", "powerU1", "powerU2", "powerEp", "flyAshU1", "flyAshU2", "bottomAshU1", "bottomAshU2", "ureaIn", "ureaUse", "ureaStock", "boilerChemIn", "boilerChemUse", "boilerChemStock", "saltIn", "saltUse", "saltFluidized", "saltStock", "causticIn", "causticUse", "causticStock", "carbonIn", "carbonUse", "carbonStock", "oilIn", "oilU1", "oilU2", "oilSludge1", "oilSludge2", "oilStock", "clinkerSideWall", "clinkerDrop", "clinkerIn", "clinkerUse", "clinkerStock", "tmsU1Dust", "tmsU1Co", "tmsU1Nox", "tmsU1Sox", "tmsU1Hcl", "tmsU1Temp", "tmsU2Dust", "tmsU2Co", "tmsU2Nox", "tmsU2Sox", "tmsU2Hcl", "tmsU2Temp", "inboundVendor01", "inboundVendor02", "inboundVendor03", "inboundVendor04", "inboundVendor05", "inboundVendor06", "inboundVendor07", "inboundVendor08", "inboundVendor09", "inboundVendor10", "inboundVendor11", "inboundVendor12", "inboundVendor13", "inboundVendor14", "genWood", "genSaengbul", "genPlastic", "genReject", "genRagger", "genStock");

    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("usageDate", LocalDate.class),
            Map.entry("burnU1Self", BigDecimal.class),
            Map.entry("burnU1Recycle", BigDecimal.class),
            Map.entry("burnU1Generated", BigDecimal.class),
            Map.entry("burnU1RunHours", BigDecimal.class),
            Map.entry("burnU1ExtStock", BigDecimal.class),
            Map.entry("burnU2Self", BigDecimal.class),
            Map.entry("burnU2Recycle", BigDecimal.class),
            Map.entry("burnU2Generated", BigDecimal.class),
            Map.entry("burnU2RunHours", BigDecimal.class),
            Map.entry("burnU2SelfStock", BigDecimal.class),
            Map.entry("burnExtTotal", BigDecimal.class),
            Map.entry("steamU1", BigDecimal.class),
            Map.entry("steamU2First", BigDecimal.class),
            Map.entry("steamU2Second", BigDecimal.class),
            Map.entry("steamAgency", BigDecimal.class),
            Map.entry("steamPressure", String.class),
            Map.entry("waterU1First", BigDecimal.class),
            Map.entry("waterU1Second", BigDecimal.class),
            Map.entry("waterU2First", BigDecimal.class),
            Map.entry("waterU2Second", BigDecimal.class),
            Map.entry("powerMeter", BigDecimal.class),
            Map.entry("powerU1", BigDecimal.class),
            Map.entry("powerU2", BigDecimal.class),
            Map.entry("powerEp", BigDecimal.class),
            Map.entry("flyAshU1", BigDecimal.class),
            Map.entry("flyAshU2", BigDecimal.class),
            Map.entry("bottomAshU1", BigDecimal.class),
            Map.entry("bottomAshU2", BigDecimal.class),
            Map.entry("ureaIn", BigDecimal.class),
            Map.entry("ureaUse", BigDecimal.class),
            Map.entry("ureaStock", BigDecimal.class),
            Map.entry("boilerChemIn", BigDecimal.class),
            Map.entry("boilerChemUse", BigDecimal.class),
            Map.entry("boilerChemStock", BigDecimal.class),
            Map.entry("saltIn", BigDecimal.class),
            Map.entry("saltUse", BigDecimal.class),
            Map.entry("saltFluidized", BigDecimal.class),
            Map.entry("saltStock", BigDecimal.class),
            Map.entry("causticIn", BigDecimal.class),
            Map.entry("causticUse", BigDecimal.class),
            Map.entry("causticStock", BigDecimal.class),
            Map.entry("carbonIn", BigDecimal.class),
            Map.entry("carbonUse", BigDecimal.class),
            Map.entry("carbonStock", BigDecimal.class),
            Map.entry("oilIn", BigDecimal.class),
            Map.entry("oilU1", BigDecimal.class),
            Map.entry("oilU2", BigDecimal.class),
            Map.entry("oilSludge1", BigDecimal.class),
            Map.entry("oilSludge2", BigDecimal.class),
            Map.entry("oilStock", BigDecimal.class),
            Map.entry("clinkerSideWall", BigDecimal.class),
            Map.entry("clinkerDrop", BigDecimal.class),
            Map.entry("clinkerIn", BigDecimal.class),
            Map.entry("clinkerUse", BigDecimal.class),
            Map.entry("clinkerStock", BigDecimal.class),
            Map.entry("tmsU1Dust", BigDecimal.class),
            Map.entry("tmsU1Co", BigDecimal.class),
            Map.entry("tmsU1Nox", BigDecimal.class),
            Map.entry("tmsU1Sox", BigDecimal.class),
            Map.entry("tmsU1Hcl", BigDecimal.class),
            Map.entry("tmsU1Temp", BigDecimal.class),
            Map.entry("tmsU2Dust", BigDecimal.class),
            Map.entry("tmsU2Co", BigDecimal.class),
            Map.entry("tmsU2Nox", BigDecimal.class),
            Map.entry("tmsU2Sox", BigDecimal.class),
            Map.entry("tmsU2Hcl", BigDecimal.class),
            Map.entry("tmsU2Temp", BigDecimal.class),
            Map.entry("inboundVendor01", BigDecimal.class),
            Map.entry("inboundVendor02", BigDecimal.class),
            Map.entry("inboundVendor03", BigDecimal.class),
            Map.entry("inboundVendor04", BigDecimal.class),
            Map.entry("inboundVendor05", BigDecimal.class),
            Map.entry("inboundVendor06", BigDecimal.class),
            Map.entry("inboundVendor07", BigDecimal.class),
            Map.entry("inboundVendor08", BigDecimal.class),
            Map.entry("inboundVendor09", BigDecimal.class),
            Map.entry("inboundVendor10", BigDecimal.class),
            Map.entry("inboundVendor11", BigDecimal.class),
            Map.entry("inboundVendor12", BigDecimal.class),
            Map.entry("inboundVendor13", BigDecimal.class),
            Map.entry("inboundVendor14", BigDecimal.class),
            Map.entry("genWood", BigDecimal.class),
            Map.entry("genSaengbul", BigDecimal.class),
            Map.entry("genPlastic", BigDecimal.class),
            Map.entry("genReject", BigDecimal.class),
            Map.entry("genRagger", BigDecimal.class),
            Map.entry("genStock", BigDecimal.class)
    );

    private static final Map<String, Function<SteamWasteIncinDailyLogResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("usageDate", r -> r.getUsageDate()),
            Map.entry("burnU1Self", r -> r.getBurnU1Self()),
            Map.entry("burnU1Recycle", r -> r.getBurnU1Recycle()),
            Map.entry("burnU1Generated", r -> r.getBurnU1Generated()),
            Map.entry("burnU1RunHours", r -> r.getBurnU1RunHours()),
            Map.entry("burnU1ExtStock", r -> r.getBurnU1ExtStock()),
            Map.entry("burnU2Self", r -> r.getBurnU2Self()),
            Map.entry("burnU2Recycle", r -> r.getBurnU2Recycle()),
            Map.entry("burnU2Generated", r -> r.getBurnU2Generated()),
            Map.entry("burnU2RunHours", r -> r.getBurnU2RunHours()),
            Map.entry("burnU2SelfStock", r -> r.getBurnU2SelfStock()),
            Map.entry("burnExtTotal", r -> r.getBurnExtTotal()),
            Map.entry("steamU1", r -> r.getSteamU1()),
            Map.entry("steamU2First", r -> r.getSteamU2First()),
            Map.entry("steamU2Second", r -> r.getSteamU2Second()),
            Map.entry("steamAgency", r -> r.getSteamAgency()),
            Map.entry("steamPressure", r -> r.getSteamPressure()),
            Map.entry("waterU1First", r -> r.getWaterU1First()),
            Map.entry("waterU1Second", r -> r.getWaterU1Second()),
            Map.entry("waterU2First", r -> r.getWaterU2First()),
            Map.entry("waterU2Second", r -> r.getWaterU2Second()),
            Map.entry("powerMeter", r -> r.getPowerMeter()),
            Map.entry("powerU1", r -> r.getPowerU1()),
            Map.entry("powerU2", r -> r.getPowerU2()),
            Map.entry("powerEp", r -> r.getPowerEp()),
            Map.entry("flyAshU1", r -> r.getFlyAshU1()),
            Map.entry("flyAshU2", r -> r.getFlyAshU2()),
            Map.entry("bottomAshU1", r -> r.getBottomAshU1()),
            Map.entry("bottomAshU2", r -> r.getBottomAshU2()),
            Map.entry("ureaIn", r -> r.getUreaIn()),
            Map.entry("ureaUse", r -> r.getUreaUse()),
            Map.entry("ureaStock", r -> r.getUreaStock()),
            Map.entry("boilerChemIn", r -> r.getBoilerChemIn()),
            Map.entry("boilerChemUse", r -> r.getBoilerChemUse()),
            Map.entry("boilerChemStock", r -> r.getBoilerChemStock()),
            Map.entry("saltIn", r -> r.getSaltIn()),
            Map.entry("saltUse", r -> r.getSaltUse()),
            Map.entry("saltFluidized", r -> r.getSaltFluidized()),
            Map.entry("saltStock", r -> r.getSaltStock()),
            Map.entry("causticIn", r -> r.getCausticIn()),
            Map.entry("causticUse", r -> r.getCausticUse()),
            Map.entry("causticStock", r -> r.getCausticStock()),
            Map.entry("carbonIn", r -> r.getCarbonIn()),
            Map.entry("carbonUse", r -> r.getCarbonUse()),
            Map.entry("carbonStock", r -> r.getCarbonStock()),
            Map.entry("oilIn", r -> r.getOilIn()),
            Map.entry("oilU1", r -> r.getOilU1()),
            Map.entry("oilU2", r -> r.getOilU2()),
            Map.entry("oilSludge1", r -> r.getOilSludge1()),
            Map.entry("oilSludge2", r -> r.getOilSludge2()),
            Map.entry("oilStock", r -> r.getOilStock()),
            Map.entry("clinkerSideWall", r -> r.getClinkerSideWall()),
            Map.entry("clinkerDrop", r -> r.getClinkerDrop()),
            Map.entry("clinkerIn", r -> r.getClinkerIn()),
            Map.entry("clinkerUse", r -> r.getClinkerUse()),
            Map.entry("clinkerStock", r -> r.getClinkerStock()),
            Map.entry("tmsU1Dust", r -> r.getTmsU1Dust()),
            Map.entry("tmsU1Co", r -> r.getTmsU1Co()),
            Map.entry("tmsU1Nox", r -> r.getTmsU1Nox()),
            Map.entry("tmsU1Sox", r -> r.getTmsU1Sox()),
            Map.entry("tmsU1Hcl", r -> r.getTmsU1Hcl()),
            Map.entry("tmsU1Temp", r -> r.getTmsU1Temp()),
            Map.entry("tmsU2Dust", r -> r.getTmsU2Dust()),
            Map.entry("tmsU2Co", r -> r.getTmsU2Co()),
            Map.entry("tmsU2Nox", r -> r.getTmsU2Nox()),
            Map.entry("tmsU2Sox", r -> r.getTmsU2Sox()),
            Map.entry("tmsU2Hcl", r -> r.getTmsU2Hcl()),
            Map.entry("tmsU2Temp", r -> r.getTmsU2Temp()),
            Map.entry("inboundVendor01", r -> r.getInboundVendor01()),
            Map.entry("inboundVendor02", r -> r.getInboundVendor02()),
            Map.entry("inboundVendor03", r -> r.getInboundVendor03()),
            Map.entry("inboundVendor04", r -> r.getInboundVendor04()),
            Map.entry("inboundVendor05", r -> r.getInboundVendor05()),
            Map.entry("inboundVendor06", r -> r.getInboundVendor06()),
            Map.entry("inboundVendor07", r -> r.getInboundVendor07()),
            Map.entry("inboundVendor08", r -> r.getInboundVendor08()),
            Map.entry("inboundVendor09", r -> r.getInboundVendor09()),
            Map.entry("inboundVendor10", r -> r.getInboundVendor10()),
            Map.entry("inboundVendor11", r -> r.getInboundVendor11()),
            Map.entry("inboundVendor12", r -> r.getInboundVendor12()),
            Map.entry("inboundVendor13", r -> r.getInboundVendor13()),
            Map.entry("inboundVendor14", r -> r.getInboundVendor14()),
            Map.entry("genWood", r -> r.getGenWood()),
            Map.entry("genSaengbul", r -> r.getGenSaengbul()),
            Map.entry("genPlastic", r -> r.getGenPlastic()),
            Map.entry("genReject", r -> r.getGenReject()),
            Map.entry("genRagger", r -> r.getGenRagger()),
            Map.entry("genStock", r -> r.getGenStock())
    );

    private static final Map<String, BiConsumer<SteamWasteIncinDailyLogSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("usageDate", (r, v) -> r.setUsageDate((LocalDate) v)),
            Map.entry("burnU1Self", (r, v) -> r.setBurnU1Self((BigDecimal) v)),
            Map.entry("burnU1Recycle", (r, v) -> r.setBurnU1Recycle((BigDecimal) v)),
            Map.entry("burnU1Generated", (r, v) -> r.setBurnU1Generated((BigDecimal) v)),
            Map.entry("burnU1RunHours", (r, v) -> r.setBurnU1RunHours((BigDecimal) v)),
            Map.entry("burnU1ExtStock", (r, v) -> r.setBurnU1ExtStock((BigDecimal) v)),
            Map.entry("burnU2Self", (r, v) -> r.setBurnU2Self((BigDecimal) v)),
            Map.entry("burnU2Recycle", (r, v) -> r.setBurnU2Recycle((BigDecimal) v)),
            Map.entry("burnU2Generated", (r, v) -> r.setBurnU2Generated((BigDecimal) v)),
            Map.entry("burnU2RunHours", (r, v) -> r.setBurnU2RunHours((BigDecimal) v)),
            Map.entry("burnU2SelfStock", (r, v) -> r.setBurnU2SelfStock((BigDecimal) v)),
            Map.entry("burnExtTotal", (r, v) -> r.setBurnExtTotal((BigDecimal) v)),
            Map.entry("steamU1", (r, v) -> r.setSteamU1((BigDecimal) v)),
            Map.entry("steamU2First", (r, v) -> r.setSteamU2First((BigDecimal) v)),
            Map.entry("steamU2Second", (r, v) -> r.setSteamU2Second((BigDecimal) v)),
            Map.entry("steamAgency", (r, v) -> r.setSteamAgency((BigDecimal) v)),
            Map.entry("steamPressure", (r, v) -> r.setSteamPressure((String) v)),
            Map.entry("waterU1First", (r, v) -> r.setWaterU1First((BigDecimal) v)),
            Map.entry("waterU1Second", (r, v) -> r.setWaterU1Second((BigDecimal) v)),
            Map.entry("waterU2First", (r, v) -> r.setWaterU2First((BigDecimal) v)),
            Map.entry("waterU2Second", (r, v) -> r.setWaterU2Second((BigDecimal) v)),
            Map.entry("powerMeter", (r, v) -> r.setPowerMeter((BigDecimal) v)),
            Map.entry("powerU1", (r, v) -> r.setPowerU1((BigDecimal) v)),
            Map.entry("powerU2", (r, v) -> r.setPowerU2((BigDecimal) v)),
            Map.entry("powerEp", (r, v) -> r.setPowerEp((BigDecimal) v)),
            Map.entry("flyAshU1", (r, v) -> r.setFlyAshU1((BigDecimal) v)),
            Map.entry("flyAshU2", (r, v) -> r.setFlyAshU2((BigDecimal) v)),
            Map.entry("bottomAshU1", (r, v) -> r.setBottomAshU1((BigDecimal) v)),
            Map.entry("bottomAshU2", (r, v) -> r.setBottomAshU2((BigDecimal) v)),
            Map.entry("ureaIn", (r, v) -> r.setUreaIn((BigDecimal) v)),
            Map.entry("ureaUse", (r, v) -> r.setUreaUse((BigDecimal) v)),
            Map.entry("ureaStock", (r, v) -> r.setUreaStock((BigDecimal) v)),
            Map.entry("boilerChemIn", (r, v) -> r.setBoilerChemIn((BigDecimal) v)),
            Map.entry("boilerChemUse", (r, v) -> r.setBoilerChemUse((BigDecimal) v)),
            Map.entry("boilerChemStock", (r, v) -> r.setBoilerChemStock((BigDecimal) v)),
            Map.entry("saltIn", (r, v) -> r.setSaltIn((BigDecimal) v)),
            Map.entry("saltUse", (r, v) -> r.setSaltUse((BigDecimal) v)),
            Map.entry("saltFluidized", (r, v) -> r.setSaltFluidized((BigDecimal) v)),
            Map.entry("saltStock", (r, v) -> r.setSaltStock((BigDecimal) v)),
            Map.entry("causticIn", (r, v) -> r.setCausticIn((BigDecimal) v)),
            Map.entry("causticUse", (r, v) -> r.setCausticUse((BigDecimal) v)),
            Map.entry("causticStock", (r, v) -> r.setCausticStock((BigDecimal) v)),
            Map.entry("carbonIn", (r, v) -> r.setCarbonIn((BigDecimal) v)),
            Map.entry("carbonUse", (r, v) -> r.setCarbonUse((BigDecimal) v)),
            Map.entry("carbonStock", (r, v) -> r.setCarbonStock((BigDecimal) v)),
            Map.entry("oilIn", (r, v) -> r.setOilIn((BigDecimal) v)),
            Map.entry("oilU1", (r, v) -> r.setOilU1((BigDecimal) v)),
            Map.entry("oilU2", (r, v) -> r.setOilU2((BigDecimal) v)),
            Map.entry("oilSludge1", (r, v) -> r.setOilSludge1((BigDecimal) v)),
            Map.entry("oilSludge2", (r, v) -> r.setOilSludge2((BigDecimal) v)),
            Map.entry("oilStock", (r, v) -> r.setOilStock((BigDecimal) v)),
            Map.entry("clinkerSideWall", (r, v) -> r.setClinkerSideWall((BigDecimal) v)),
            Map.entry("clinkerDrop", (r, v) -> r.setClinkerDrop((BigDecimal) v)),
            Map.entry("clinkerIn", (r, v) -> r.setClinkerIn((BigDecimal) v)),
            Map.entry("clinkerUse", (r, v) -> r.setClinkerUse((BigDecimal) v)),
            Map.entry("clinkerStock", (r, v) -> r.setClinkerStock((BigDecimal) v)),
            Map.entry("tmsU1Dust", (r, v) -> r.setTmsU1Dust((BigDecimal) v)),
            Map.entry("tmsU1Co", (r, v) -> r.setTmsU1Co((BigDecimal) v)),
            Map.entry("tmsU1Nox", (r, v) -> r.setTmsU1Nox((BigDecimal) v)),
            Map.entry("tmsU1Sox", (r, v) -> r.setTmsU1Sox((BigDecimal) v)),
            Map.entry("tmsU1Hcl", (r, v) -> r.setTmsU1Hcl((BigDecimal) v)),
            Map.entry("tmsU1Temp", (r, v) -> r.setTmsU1Temp((BigDecimal) v)),
            Map.entry("tmsU2Dust", (r, v) -> r.setTmsU2Dust((BigDecimal) v)),
            Map.entry("tmsU2Co", (r, v) -> r.setTmsU2Co((BigDecimal) v)),
            Map.entry("tmsU2Nox", (r, v) -> r.setTmsU2Nox((BigDecimal) v)),
            Map.entry("tmsU2Sox", (r, v) -> r.setTmsU2Sox((BigDecimal) v)),
            Map.entry("tmsU2Hcl", (r, v) -> r.setTmsU2Hcl((BigDecimal) v)),
            Map.entry("tmsU2Temp", (r, v) -> r.setTmsU2Temp((BigDecimal) v)),
            Map.entry("inboundVendor01", (r, v) -> r.setInboundVendor01((BigDecimal) v)),
            Map.entry("inboundVendor02", (r, v) -> r.setInboundVendor02((BigDecimal) v)),
            Map.entry("inboundVendor03", (r, v) -> r.setInboundVendor03((BigDecimal) v)),
            Map.entry("inboundVendor04", (r, v) -> r.setInboundVendor04((BigDecimal) v)),
            Map.entry("inboundVendor05", (r, v) -> r.setInboundVendor05((BigDecimal) v)),
            Map.entry("inboundVendor06", (r, v) -> r.setInboundVendor06((BigDecimal) v)),
            Map.entry("inboundVendor07", (r, v) -> r.setInboundVendor07((BigDecimal) v)),
            Map.entry("inboundVendor08", (r, v) -> r.setInboundVendor08((BigDecimal) v)),
            Map.entry("inboundVendor09", (r, v) -> r.setInboundVendor09((BigDecimal) v)),
            Map.entry("inboundVendor10", (r, v) -> r.setInboundVendor10((BigDecimal) v)),
            Map.entry("inboundVendor11", (r, v) -> r.setInboundVendor11((BigDecimal) v)),
            Map.entry("inboundVendor12", (r, v) -> r.setInboundVendor12((BigDecimal) v)),
            Map.entry("inboundVendor13", (r, v) -> r.setInboundVendor13((BigDecimal) v)),
            Map.entry("inboundVendor14", (r, v) -> r.setInboundVendor14((BigDecimal) v)),
            Map.entry("genWood", (r, v) -> r.setGenWood((BigDecimal) v)),
            Map.entry("genSaengbul", (r, v) -> r.setGenSaengbul((BigDecimal) v)),
            Map.entry("genPlastic", (r, v) -> r.setGenPlastic((BigDecimal) v)),
            Map.entry("genReject", (r, v) -> r.setGenReject((BigDecimal) v)),
            Map.entry("genRagger", (r, v) -> r.setGenRagger((BigDecimal) v)),
            Map.entry("genStock", (r, v) -> r.setGenStock((BigDecimal) v))
    );

    private final SteamWasteIncinDailyLogService service;

    public SteamWasteIncinDailyLogAdapter(SteamWasteIncinDailyLogService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "wasteIncinLog"; }

    @Override
    public String time() { return "day"; }

    @Override
    public List<String> keyFields() { return KEY_FIELDS; }

    @Override
    public List<String> valueFields() { return VALUE_FIELDS; }

    @Override
    public Class<?> typeOf(String field) { return TYPES.get(field); }

    @Override
    public List<Map<String, Object>> load(int year, Integer month) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SteamWasteIncinDailyLogResponse r : service.getList(year, month)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : KEY_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            for (String f : VALUE_FIELDS) row.put(f, GETTERS.get(f).apply(r));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing) {
        SteamWasteIncinDailyLogBulkRequest request = new SteamWasteIncinDailyLogBulkRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setClearMissing(clearMissing);
        for (Map<String, Object> row : rows) {
            SteamWasteIncinDailyLogSaveRequest r = new SteamWasteIncinDailyLogSaveRequest();
            row.forEach((field, value) -> {
                BiConsumer<SteamWasteIncinDailyLogSaveRequest, Object> setter = SETTERS.get(field);
                if (setter != null) setter.accept(r, value);
            });
            request.getRows().add(r);
        }
        return service.saveBulk(request);
    }
}
