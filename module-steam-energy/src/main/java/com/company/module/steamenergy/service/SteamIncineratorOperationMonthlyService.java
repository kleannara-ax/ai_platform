package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorOperationMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorOperationMonthlyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorOperationMonthlySaveRequest;
import com.company.module.steamenergy.entity.SteamIncineratorOperationMonthly;
import com.company.module.steamenergy.repository.SteamIncineratorOperationMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 소각로 계획대비 가동실적 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamIncineratorOperationMonthlyService {

    private final SteamIncineratorOperationMonthlyRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamIncineratorOperationMonthlyResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamIncineratorOperationMonthlyResponse::from).toList();
    }

    public SteamIncineratorOperationMonthlyResponse get(Long id) {
        return SteamIncineratorOperationMonthlyResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamIncineratorOperationMonthlyResponse save(SteamIncineratorOperationMonthlySaveRequest request) {
        Upsert result = upsert(request);
        return SteamIncineratorOperationMonthlyResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamIncineratorOperationMonthlyBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamIncineratorOperationMonthlySaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamIncineratorOperationMonthly e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 소각로 계획대비 가동실적 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 소각로 계획대비 가동실적 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamIncineratorOperationMonthly entity, boolean updated) {}

    private Upsert upsert(SteamIncineratorOperationMonthlySaveRequest r) {
        SteamIncineratorOperationMonthly active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getU1Plan(), r.getU1Actual(), r.getU2Plan(), r.getU2Actual(), r.getSteamPlan(), r.getSteamActual(), r.getRatePlan(), r.getRateActual(), r.getCostPlan(), r.getCostActual(), null);
            return new Upsert(active, true);
        }
        SteamIncineratorOperationMonthly deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getU1Plan(), r.getU1Actual(), r.getU2Plan(), r.getU2Actual(), r.getSteamPlan(), r.getSteamActual(), r.getRatePlan(), r.getRateActual(), r.getCostPlan(), r.getCostActual(), null);
            return new Upsert(deleted, true);
        }
        SteamIncineratorOperationMonthly created = repository.save(SteamIncineratorOperationMonthly.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .u1Plan(r.getU1Plan())
                .u1Actual(r.getU1Actual())
                .u2Plan(r.getU2Plan())
                .u2Actual(r.getU2Actual())
                .steamPlan(r.getSteamPlan())
                .steamActual(r.getSteamActual())
                .ratePlan(r.getRatePlan())
                .rateActual(r.getRateActual())
                .costPlan(r.getCostPlan())
                .costActual(r.getCostActual())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamIncineratorOperationMonthly> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(year).stream().filter(e -> month == null || month.equals(e.getMonthNo())).toList();
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamIncineratorOperationMonthly findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("소각로 계획대비 가동실적", id));
    }
}
