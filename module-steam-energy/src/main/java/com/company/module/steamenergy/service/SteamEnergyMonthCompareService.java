package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyMonthCompare;
import com.company.module.steamenergy.repository.SteamEnergyMonthCompareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 회계비용 마감비교 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyMonthCompareService {

    private final SteamEnergyMonthCompareRepository repository;

    public List<SteamEnergyMonthCompareResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyMonthCompareResponse::from).toList();
    }

    public SteamEnergyMonthCompareResponse get(Long id) {
        return SteamEnergyMonthCompareResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyMonthCompareResponse save(SteamEnergyMonthCompareSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 회계비용 마감비교 {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyMonthCompareResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyMonthCompareBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyMonthCompareSaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyMonthCompare existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
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
        log.info("[STEAM-ENERGY] 회계비용 마감비교 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyMonthCompare entity, boolean updated) {}

    private Upsert upsert(SteamEnergyMonthCompareSaveRequest r) {
        SteamEnergyMonthCompare active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getZescoSteamBuy(), r.getLngBuy(), r.getZescoOffset(), r.getZescoPenalty(), r.getFuelPlan(), r.getIncineratorSteamBuy(), r.getFluidSteamBuy(), r.getFuelActual(), r.getFuelSteamUnit(), null);
            return new Upsert(active, true);
        }
        SteamEnergyMonthCompare deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getZescoSteamBuy(), r.getLngBuy(), r.getZescoOffset(), r.getZescoPenalty(), r.getFuelPlan(), r.getIncineratorSteamBuy(), r.getFluidSteamBuy(), r.getFuelActual(), r.getFuelSteamUnit(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyMonthCompare created = repository.save(SteamEnergyMonthCompare.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .zescoSteamBuy(r.getZescoSteamBuy())
                .lngBuy(r.getLngBuy())
                .zescoOffset(r.getZescoOffset())
                .zescoPenalty(r.getZescoPenalty())
                .fuelPlan(r.getFuelPlan())
                .incineratorSteamBuy(r.getIncineratorSteamBuy())
                .fluidSteamBuy(r.getFluidSteamBuy())
                .fuelActual(r.getFuelActual())
                .fuelSteamUnit(r.getFuelSteamUnit())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyMonthCompare findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("회계비용 마감비교", id));
    }
}
