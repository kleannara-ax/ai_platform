package com.company.module.steamenergy.service.energy;

import com.company.module.steamenergy.dto.SteamEnergyAccountingPlanResponse;
import com.company.module.steamenergy.dto.SteamEnergyAccountingPlanSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyAccountingPlanService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 회계비용 계획 — 셀 브리지용 표 어댑터 (생성 코드: energy_specs.py → gen_energy_all.py)
 *
 * <p>Table: steam_energy_accounting_plan / alias: accountingPlan
 */
@Component
public class SteamEnergyAccountingPlanAdapter implements EnergyTableAdapter {

    private static final Map<String, Function<SteamEnergyAccountingPlanResponse, Object>> GETTERS = Map.ofEntries(
            Map.entry("compositeSteamBuy", r -> r.getCompositeSteamBuy()),
            Map.entry("zescoSteamBuy", r -> r.getZescoSteamBuy()),
            Map.entry("lngBuy", r -> r.getLngBuy()),
            Map.entry("incineratorCost", r -> r.getIncineratorCost()),
            Map.entry("fluidCost", r -> r.getFluidCost()),
            Map.entry("zescoOffset", r -> r.getZescoOffset()),
            Map.entry("zescoPenalty", r -> r.getZescoPenalty()),
            Map.entry("zescoAdvanceRepay", r -> r.getZescoAdvanceRepay()),
            Map.entry("compositeLngWater", r -> r.getCompositeLngWater()),
            Map.entry("compositeInvest", r -> r.getCompositeInvest()),
            Map.entry("compositeIncome", r -> r.getCompositeIncome()),
            Map.entry("landLease", r -> r.getLandLease()),
            Map.entry("paperFuel", r -> r.getPaperFuel()),
            Map.entry("livingFuel", r -> r.getLivingFuel()),
            Map.entry("fuelVsPlan", r -> r.getFuelVsPlan()),
            Map.entry("paperProdTon", r -> r.getPaperProdTon()),
            Map.entry("tissueProdTon", r -> r.getTissueProdTon()),
            Map.entry("steamProdTon", r -> r.getSteamProdTon()),
            Map.entry("lngBurnerCost", r -> r.getLngBurnerCost()),
            Map.entry("compositeNet", r -> r.getCompositeNet()),
            Map.entry("zescoNet", r -> r.getZescoNet())
    );
    private static final Map<String, BiConsumer<SteamEnergyAccountingPlanSaveRequest, Object>> SETTERS = Map.ofEntries(
            Map.entry("compositeSteamBuy", (r, v) -> r.setCompositeSteamBuy((BigDecimal) v)),
            Map.entry("zescoSteamBuy", (r, v) -> r.setZescoSteamBuy((BigDecimal) v)),
            Map.entry("lngBuy", (r, v) -> r.setLngBuy((BigDecimal) v)),
            Map.entry("incineratorCost", (r, v) -> r.setIncineratorCost((BigDecimal) v)),
            Map.entry("fluidCost", (r, v) -> r.setFluidCost((BigDecimal) v)),
            Map.entry("zescoOffset", (r, v) -> r.setZescoOffset((BigDecimal) v)),
            Map.entry("zescoPenalty", (r, v) -> r.setZescoPenalty((BigDecimal) v)),
            Map.entry("zescoAdvanceRepay", (r, v) -> r.setZescoAdvanceRepay((BigDecimal) v)),
            Map.entry("compositeLngWater", (r, v) -> r.setCompositeLngWater((BigDecimal) v)),
            Map.entry("compositeInvest", (r, v) -> r.setCompositeInvest((BigDecimal) v)),
            Map.entry("compositeIncome", (r, v) -> r.setCompositeIncome((BigDecimal) v)),
            Map.entry("landLease", (r, v) -> r.setLandLease((BigDecimal) v)),
            Map.entry("paperFuel", (r, v) -> r.setPaperFuel((BigDecimal) v)),
            Map.entry("livingFuel", (r, v) -> r.setLivingFuel((BigDecimal) v)),
            Map.entry("fuelVsPlan", (r, v) -> r.setFuelVsPlan((BigDecimal) v)),
            Map.entry("paperProdTon", (r, v) -> r.setPaperProdTon((BigDecimal) v)),
            Map.entry("tissueProdTon", (r, v) -> r.setTissueProdTon((BigDecimal) v)),
            Map.entry("steamProdTon", (r, v) -> r.setSteamProdTon((BigDecimal) v)),
            Map.entry("lngBurnerCost", (r, v) -> r.setLngBurnerCost((BigDecimal) v)),
            Map.entry("compositeNet", (r, v) -> r.setCompositeNet((BigDecimal) v)),
            Map.entry("zescoNet", (r, v) -> r.setZescoNet((BigDecimal) v))
    );

    private final SteamEnergyAccountingPlanService service;

    public SteamEnergyAccountingPlanAdapter(SteamEnergyAccountingPlanService service) {
        this.service = service;
    }

    @Override
    public String alias() { return "accountingPlan"; }

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
        SteamEnergyAccountingPlanSaveRequest request = new SteamEnergyAccountingPlanSaveRequest();
        request.setYearNo(yearNo);
        if (monthNo != null) request.setMonthNo(monthNo);
        values.forEach((field, value) -> {
            BiConsumer<SteamEnergyAccountingPlanSaveRequest, Object> setter = SETTERS.get(field);
            if (setter != null) setter.accept(request, value);
        });
        service.save(request);
    }
}
