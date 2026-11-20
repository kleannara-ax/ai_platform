package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyAccountingPlanBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyAccountingPlanResponse;
import com.company.module.steamenergy.dto.SteamEnergyAccountingPlanSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyAccountingPlan;
import com.company.module.steamenergy.repository.SteamEnergyAccountingPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 회계비용 계획 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyAccountingPlanService {

    private final SteamEnergyAccountingPlanRepository repository;

    public List<SteamEnergyAccountingPlanResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyAccountingPlanResponse::from).toList();
    }

    public SteamEnergyAccountingPlanResponse get(Long id) {
        return SteamEnergyAccountingPlanResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyAccountingPlanResponse save(SteamEnergyAccountingPlanSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 회계비용 계획 {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyAccountingPlanResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyAccountingPlanBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyAccountingPlanSaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyAccountingPlan existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
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
        log.info("[STEAM-ENERGY] 회계비용 계획 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyAccountingPlan entity, boolean updated) {}

    private Upsert upsert(SteamEnergyAccountingPlanSaveRequest r) {
        SteamEnergyAccountingPlan active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getCompositeSteamBuy(), r.getZescoSteamBuy(), r.getLngBuy(), r.getIncineratorCost(), r.getFluidCost(), r.getZescoOffset(), r.getZescoPenalty(), r.getZescoAdvanceRepay(), r.getCompositeLngWater(), r.getCompositeInvest(), r.getCompositeIncome(), r.getLandLease(), r.getPaperFuel(), r.getLivingFuel(), r.getFuelVsPlan(), r.getPaperProdTon(), r.getTissueProdTon(), r.getSteamProdTon(), r.getLngBurnerCost(), r.getCompositeNet(), r.getZescoNet(), null);
            return new Upsert(active, true);
        }
        SteamEnergyAccountingPlan deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getCompositeSteamBuy(), r.getZescoSteamBuy(), r.getLngBuy(), r.getIncineratorCost(), r.getFluidCost(), r.getZescoOffset(), r.getZescoPenalty(), r.getZescoAdvanceRepay(), r.getCompositeLngWater(), r.getCompositeInvest(), r.getCompositeIncome(), r.getLandLease(), r.getPaperFuel(), r.getLivingFuel(), r.getFuelVsPlan(), r.getPaperProdTon(), r.getTissueProdTon(), r.getSteamProdTon(), r.getLngBurnerCost(), r.getCompositeNet(), r.getZescoNet(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyAccountingPlan created = repository.save(SteamEnergyAccountingPlan.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .compositeSteamBuy(r.getCompositeSteamBuy())
                .zescoSteamBuy(r.getZescoSteamBuy())
                .lngBuy(r.getLngBuy())
                .incineratorCost(r.getIncineratorCost())
                .fluidCost(r.getFluidCost())
                .zescoOffset(r.getZescoOffset())
                .zescoPenalty(r.getZescoPenalty())
                .zescoAdvanceRepay(r.getZescoAdvanceRepay())
                .compositeLngWater(r.getCompositeLngWater())
                .compositeInvest(r.getCompositeInvest())
                .compositeIncome(r.getCompositeIncome())
                .landLease(r.getLandLease())
                .paperFuel(r.getPaperFuel())
                .livingFuel(r.getLivingFuel())
                .fuelVsPlan(r.getFuelVsPlan())
                .paperProdTon(r.getPaperProdTon())
                .tissueProdTon(r.getTissueProdTon())
                .steamProdTon(r.getSteamProdTon())
                .lngBurnerCost(r.getLngBurnerCost())
                .compositeNet(r.getCompositeNet())
                .zescoNet(r.getZescoNet())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyAccountingPlan findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("회계비용 계획", id));
    }
}
