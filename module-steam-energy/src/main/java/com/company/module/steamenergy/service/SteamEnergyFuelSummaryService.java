package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyFuelSummaryBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyFuelSummaryResponse;
import com.company.module.steamenergy.dto.SteamEnergyFuelSummarySaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyFuelSummary;
import com.company.module.steamenergy.repository.SteamEnergyFuelSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 연료비 요약 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyFuelSummaryService {

    private final SteamEnergyFuelSummaryRepository repository;

    public List<SteamEnergyFuelSummaryResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyFuelSummaryResponse::from).toList();
    }

    public SteamEnergyFuelSummaryResponse get(Long id) {
        return SteamEnergyFuelSummaryResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyFuelSummaryResponse save(SteamEnergyFuelSummarySaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 연료비 요약 {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyFuelSummaryResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyFuelSummaryBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyFuelSummarySaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyFuelSummary existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
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
        log.info("[STEAM-ENERGY] 연료비 요약 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyFuelSummary entity, boolean updated) {}

    private Upsert upsert(SteamEnergyFuelSummarySaveRequest r) {
        SteamEnergyFuelSummary active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getT1Pm2Prod(), r.getT1Pm2Fuel(), r.getT1Pm3Prod(), r.getT1Pm3Fuel(), r.getT1Tm3Prod(), r.getT1Tm3Fuel(), r.getT1Tm4Prod(), r.getT1Tm4Fuel(), r.getT1Tm5Prod(), r.getT1Tm5Fuel(), r.getT2LngUsage(), r.getT2LngPrice(), r.getT2LngCost(), r.getT2CompositeUsage(), r.getT2CompositePrice(), r.getT2CompositeCost(), r.getT2IncineratorUsage(), r.getT2IncineratorPrice(), r.getT2IncineratorCost(), r.getT2FluidUsage(), r.getT2FluidPrice(), r.getT2FluidCost(), r.getT2ExtUsage(), r.getT2ExtPrice(), r.getT2ExtCost(), r.getT2LngElec(), r.getT2LngPenalty(), r.getT2LngOverprice(), r.getT2CompositeElec(), r.getT2CompositePenalty(), r.getT2CompositeOverprice(), r.getT2IncineratorElec(), r.getT2IncineratorPenalty(), r.getT2IncineratorOverprice(), r.getT2FluidElec(), r.getT2FluidPenalty(), r.getT2FluidOverprice(), r.getT2ExtElec(), r.getT2ExtPenalty(), r.getT2ExtOverprice(), r.getT2SubtotalElec(), r.getT2SubtotalPenalty(), r.getT2SubtotalOverprice(), r.getT2BurnerLngElec(), r.getT2BurnerLngPenalty(), r.getT2BurnerLngOverprice(), r.getT2TotalElec(), r.getT2TotalPenalty(), r.getT2TotalOverprice(), null);
            return new Upsert(active, true);
        }
        SteamEnergyFuelSummary deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getT1Pm2Prod(), r.getT1Pm2Fuel(), r.getT1Pm3Prod(), r.getT1Pm3Fuel(), r.getT1Tm3Prod(), r.getT1Tm3Fuel(), r.getT1Tm4Prod(), r.getT1Tm4Fuel(), r.getT1Tm5Prod(), r.getT1Tm5Fuel(), r.getT2LngUsage(), r.getT2LngPrice(), r.getT2LngCost(), r.getT2CompositeUsage(), r.getT2CompositePrice(), r.getT2CompositeCost(), r.getT2IncineratorUsage(), r.getT2IncineratorPrice(), r.getT2IncineratorCost(), r.getT2FluidUsage(), r.getT2FluidPrice(), r.getT2FluidCost(), r.getT2ExtUsage(), r.getT2ExtPrice(), r.getT2ExtCost(), r.getT2LngElec(), r.getT2LngPenalty(), r.getT2LngOverprice(), r.getT2CompositeElec(), r.getT2CompositePenalty(), r.getT2CompositeOverprice(), r.getT2IncineratorElec(), r.getT2IncineratorPenalty(), r.getT2IncineratorOverprice(), r.getT2FluidElec(), r.getT2FluidPenalty(), r.getT2FluidOverprice(), r.getT2ExtElec(), r.getT2ExtPenalty(), r.getT2ExtOverprice(), r.getT2SubtotalElec(), r.getT2SubtotalPenalty(), r.getT2SubtotalOverprice(), r.getT2BurnerLngElec(), r.getT2BurnerLngPenalty(), r.getT2BurnerLngOverprice(), r.getT2TotalElec(), r.getT2TotalPenalty(), r.getT2TotalOverprice(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyFuelSummary created = repository.save(SteamEnergyFuelSummary.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .t1Pm2Prod(r.getT1Pm2Prod())
                .t1Pm2Fuel(r.getT1Pm2Fuel())
                .t1Pm3Prod(r.getT1Pm3Prod())
                .t1Pm3Fuel(r.getT1Pm3Fuel())
                .t1Tm3Prod(r.getT1Tm3Prod())
                .t1Tm3Fuel(r.getT1Tm3Fuel())
                .t1Tm4Prod(r.getT1Tm4Prod())
                .t1Tm4Fuel(r.getT1Tm4Fuel())
                .t1Tm5Prod(r.getT1Tm5Prod())
                .t1Tm5Fuel(r.getT1Tm5Fuel())
                .t2LngUsage(r.getT2LngUsage())
                .t2LngPrice(r.getT2LngPrice())
                .t2LngCost(r.getT2LngCost())
                .t2CompositeUsage(r.getT2CompositeUsage())
                .t2CompositePrice(r.getT2CompositePrice())
                .t2CompositeCost(r.getT2CompositeCost())
                .t2IncineratorUsage(r.getT2IncineratorUsage())
                .t2IncineratorPrice(r.getT2IncineratorPrice())
                .t2IncineratorCost(r.getT2IncineratorCost())
                .t2FluidUsage(r.getT2FluidUsage())
                .t2FluidPrice(r.getT2FluidPrice())
                .t2FluidCost(r.getT2FluidCost())
                .t2ExtUsage(r.getT2ExtUsage())
                .t2ExtPrice(r.getT2ExtPrice())
                .t2ExtCost(r.getT2ExtCost())
                .t2LngElec(r.getT2LngElec())
                .t2LngPenalty(r.getT2LngPenalty())
                .t2LngOverprice(r.getT2LngOverprice())
                .t2CompositeElec(r.getT2CompositeElec())
                .t2CompositePenalty(r.getT2CompositePenalty())
                .t2CompositeOverprice(r.getT2CompositeOverprice())
                .t2IncineratorElec(r.getT2IncineratorElec())
                .t2IncineratorPenalty(r.getT2IncineratorPenalty())
                .t2IncineratorOverprice(r.getT2IncineratorOverprice())
                .t2FluidElec(r.getT2FluidElec())
                .t2FluidPenalty(r.getT2FluidPenalty())
                .t2FluidOverprice(r.getT2FluidOverprice())
                .t2ExtElec(r.getT2ExtElec())
                .t2ExtPenalty(r.getT2ExtPenalty())
                .t2ExtOverprice(r.getT2ExtOverprice())
                .t2SubtotalElec(r.getT2SubtotalElec())
                .t2SubtotalPenalty(r.getT2SubtotalPenalty())
                .t2SubtotalOverprice(r.getT2SubtotalOverprice())
                .t2BurnerLngElec(r.getT2BurnerLngElec())
                .t2BurnerLngPenalty(r.getT2BurnerLngPenalty())
                .t2BurnerLngOverprice(r.getT2BurnerLngOverprice())
                .t2TotalElec(r.getT2TotalElec())
                .t2TotalPenalty(r.getT2TotalPenalty())
                .t2TotalOverprice(r.getT2TotalOverprice())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyFuelSummary findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("연료비 요약", id));
    }
}
