package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyFuelPlanBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyFuelPlanResponse;
import com.company.module.steamenergy.dto.SteamEnergyFuelPlanSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyFuelPlan;
import com.company.module.steamenergy.repository.SteamEnergyFuelPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 연료비 사업계획 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyFuelPlanService {

    private final SteamEnergyFuelPlanRepository repository;

    public List<SteamEnergyFuelPlanResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyFuelPlanResponse::from).toList();
    }

    public SteamEnergyFuelPlanResponse get(Long id) {
        return SteamEnergyFuelPlanResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyFuelPlanResponse save(SteamEnergyFuelPlanSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 연료비 사업계획 {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyFuelPlanResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyFuelPlanBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyFuelPlanSaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyFuelPlan existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
                if (months.contains(existing.getMonthNo())) continue;
                existing.clearValues(null);
                cleared += 1;
            }
        }
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 연료비 사업계획 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyFuelPlan entity, boolean updated) {}

    private Upsert upsert(SteamEnergyFuelPlanSaveRequest r) {
        SteamEnergyFuelPlan active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getPaperProdTon(), r.getTissueProdTon(), r.getPaperUnit(), r.getTissueUnit(), r.getPaperTotalSteam(), r.getTissueTotalSteam(), r.getEtcDisperser(), r.getEtcToc(), r.getOperatingDays(), r.getProdIncinerator(), r.getProdComposite(), r.getProdExtBoiler(), r.getProdFluid(), r.getLngComposite(), r.getLngTm3(), r.getLngTm4(), r.getLngTm5(), r.getLngPm3(), r.getPriceLng(), r.getPriceIncinerator(), r.getPriceComposite(), r.getPriceExtBoiler(), r.getPriceFluid(), r.getOpexIncinerator(), r.getOpexFluid(), r.getPayExtAdvance(), r.getPayExtPenalty(), r.getPayIncinerator(), r.getPayFluid(), null);
            return new Upsert(active, true);
        }
        SteamEnergyFuelPlan deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getPaperProdTon(), r.getTissueProdTon(), r.getPaperUnit(), r.getTissueUnit(), r.getPaperTotalSteam(), r.getTissueTotalSteam(), r.getEtcDisperser(), r.getEtcToc(), r.getOperatingDays(), r.getProdIncinerator(), r.getProdComposite(), r.getProdExtBoiler(), r.getProdFluid(), r.getLngComposite(), r.getLngTm3(), r.getLngTm4(), r.getLngTm5(), r.getLngPm3(), r.getPriceLng(), r.getPriceIncinerator(), r.getPriceComposite(), r.getPriceExtBoiler(), r.getPriceFluid(), r.getOpexIncinerator(), r.getOpexFluid(), r.getPayExtAdvance(), r.getPayExtPenalty(), r.getPayIncinerator(), r.getPayFluid(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyFuelPlan created = repository.save(SteamEnergyFuelPlan.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .paperProdTon(r.getPaperProdTon())
                .tissueProdTon(r.getTissueProdTon())
                .paperUnit(r.getPaperUnit())
                .tissueUnit(r.getTissueUnit())
                .paperTotalSteam(r.getPaperTotalSteam())
                .tissueTotalSteam(r.getTissueTotalSteam())
                .etcDisperser(r.getEtcDisperser())
                .etcToc(r.getEtcToc())
                .operatingDays(r.getOperatingDays())
                .prodIncinerator(r.getProdIncinerator())
                .prodComposite(r.getProdComposite())
                .prodExtBoiler(r.getProdExtBoiler())
                .prodFluid(r.getProdFluid())
                .lngComposite(r.getLngComposite())
                .lngTm3(r.getLngTm3())
                .lngTm4(r.getLngTm4())
                .lngTm5(r.getLngTm5())
                .lngPm3(r.getLngPm3())
                .priceLng(r.getPriceLng())
                .priceIncinerator(r.getPriceIncinerator())
                .priceComposite(r.getPriceComposite())
                .priceExtBoiler(r.getPriceExtBoiler())
                .priceFluid(r.getPriceFluid())
                .opexIncinerator(r.getOpexIncinerator())
                .opexFluid(r.getOpexFluid())
                .payExtAdvance(r.getPayExtAdvance())
                .payExtPenalty(r.getPayExtPenalty())
                .payIncinerator(r.getPayIncinerator())
                .payFluid(r.getPayFluid())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyFuelPlan findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("연료비 사업계획", id));
    }
}
