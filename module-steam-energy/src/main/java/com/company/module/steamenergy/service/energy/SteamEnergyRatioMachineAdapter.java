package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyRatioMachineResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioMachineSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyRatioMachineService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 연료비 배분비율(호기별) — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_ratio_machine / alias: ratioMachine
 */
@Component
public class SteamEnergyRatioMachineAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyRatioMachineResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("fuelPm2", r -> r.getFuelPm2()),
            Map.entry("fuelPm3", r -> r.getFuelPm3()),
            Map.entry("fuelPaperSubtotal", r -> r.getFuelPaperSubtotal()),
            Map.entry("fuelTm3", r -> r.getFuelTm3()),
            Map.entry("fuelTm4", r -> r.getFuelTm4()),
            Map.entry("fuelTm5", r -> r.getFuelTm5()),
            Map.entry("fuelTissueSubtotal", r -> r.getFuelTissueSubtotal()),
            Map.entry("fuelSteamProdTon", r -> r.getFuelSteamProdTon()),
            Map.entry("usagePm2", r -> r.getUsagePm2()),
            Map.entry("usagePm3", r -> r.getUsagePm3()),
            Map.entry("usageTm3", r -> r.getUsageTm3()),
            Map.entry("usageTm4", r -> r.getUsageTm4()),
            Map.entry("usageTm5", r -> r.getUsageTm5()),
            Map.entry("usageTotalProdSteam", r -> r.getUsageTotalProdSteam()),
            Map.entry("usageEtc", r -> r.getUsageEtc()),
            Map.entry("prodPm2Kg", r -> r.getProdPm2Kg()),
            Map.entry("prodPm3Kg", r -> r.getProdPm3Kg()),
            Map.entry("prodPaperSubtotalKg", r -> r.getProdPaperSubtotalKg()),
            Map.entry("prodTm3Kg", r -> r.getProdTm3Kg()),
            Map.entry("prodTm4Kg", r -> r.getProdTm4Kg()),
            Map.entry("prodTm5Kg", r -> r.getProdTm5Kg()),
            Map.entry("prodTissueSubtotalKg", r -> r.getProdTissueSubtotalKg())
    );
    private static final Map<String, BiConsumer<SteamEnergyRatioMachineSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("fuelPm2", (r, v) -> r.setFuelPm2((BigDecimal) v)),
            Map.entry("fuelPm3", (r, v) -> r.setFuelPm3((BigDecimal) v)),
            Map.entry("fuelPaperSubtotal", (r, v) -> r.setFuelPaperSubtotal((BigDecimal) v)),
            Map.entry("fuelTm3", (r, v) -> r.setFuelTm3((BigDecimal) v)),
            Map.entry("fuelTm4", (r, v) -> r.setFuelTm4((BigDecimal) v)),
            Map.entry("fuelTm5", (r, v) -> r.setFuelTm5((BigDecimal) v)),
            Map.entry("fuelTissueSubtotal", (r, v) -> r.setFuelTissueSubtotal((BigDecimal) v)),
            Map.entry("fuelSteamProdTon", (r, v) -> r.setFuelSteamProdTon((BigDecimal) v)),
            Map.entry("usagePm2", (r, v) -> r.setUsagePm2((BigDecimal) v)),
            Map.entry("usagePm3", (r, v) -> r.setUsagePm3((BigDecimal) v)),
            Map.entry("usageTm3", (r, v) -> r.setUsageTm3((BigDecimal) v)),
            Map.entry("usageTm4", (r, v) -> r.setUsageTm4((BigDecimal) v)),
            Map.entry("usageTm5", (r, v) -> r.setUsageTm5((BigDecimal) v)),
            Map.entry("usageTotalProdSteam", (r, v) -> r.setUsageTotalProdSteam((BigDecimal) v)),
            Map.entry("usageEtc", (r, v) -> r.setUsageEtc((BigDecimal) v)),
            Map.entry("prodPm2Kg", (r, v) -> r.setProdPm2Kg((BigDecimal) v)),
            Map.entry("prodPm3Kg", (r, v) -> r.setProdPm3Kg((BigDecimal) v)),
            Map.entry("prodPaperSubtotalKg", (r, v) -> r.setProdPaperSubtotalKg((BigDecimal) v)),
            Map.entry("prodTm3Kg", (r, v) -> r.setProdTm3Kg((BigDecimal) v)),
            Map.entry("prodTm4Kg", (r, v) -> r.setProdTm4Kg((BigDecimal) v)),
            Map.entry("prodTm5Kg", (r, v) -> r.setProdTm5Kg((BigDecimal) v)),
            Map.entry("prodTissueSubtotalKg", (r, v) -> r.setProdTissueSubtotalKg((BigDecimal) v))
    );

    private final SteamEnergyRatioMachineService service;

    public SteamEnergyRatioMachineAdapter(SteamEnergyRatioMachineService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "ratioMachine"; }

    @Override
    public boolean monthly() { return true; }

    @Override
    public java.util.Set<String> fields() { return SETTERS.keySet(); }

    @Override
    public List<EnergyRow> load(int year) {
        return service.getList(year).stream().map(r -> {
            Map<String, Object> values = new LinkedHashMap<>();
            GETTERS.forEach((field, getter) -> values.put(field, getter.apply(r)));
            return new EnergyRow(r.getYearNo(), r.getMonthNo(), values);
        }).toList();
    }

    @Override
    public void save(int yearNo, Integer monthNo, Map<String, Object> values) {
        SteamEnergyRatioMachineSaveRequest request = new SteamEnergyRatioMachineSaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyRatioMachineSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
