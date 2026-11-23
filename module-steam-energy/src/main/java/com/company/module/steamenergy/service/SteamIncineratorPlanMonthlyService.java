package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorPlanMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorPlanMonthlyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorPlanMonthlySaveRequest;
import com.company.module.steamenergy.entity.SteamIncineratorPlanMonthly;
import com.company.module.steamenergy.repository.SteamIncineratorPlanMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 소각로 사업계획대비 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamIncineratorPlanMonthlyService {

    private final SteamIncineratorPlanMonthlyRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamIncineratorPlanMonthlyResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamIncineratorPlanMonthlyResponse::from).toList();
    }

    public SteamIncineratorPlanMonthlyResponse get(Long id) {
        return SteamIncineratorPlanMonthlyResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamIncineratorPlanMonthlyResponse save(SteamIncineratorPlanMonthlySaveRequest request) {
        Upsert result = upsert(request);
        return SteamIncineratorPlanMonthlyResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamIncineratorPlanMonthlyBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamIncineratorPlanMonthlySaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamIncineratorPlanMonthly e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 소각로 사업계획대비 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 소각로 사업계획대비 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamIncineratorPlanMonthly entity, boolean updated) {}

    private Upsert upsert(SteamIncineratorPlanMonthlySaveRequest r) {
        SteamIncineratorPlanMonthly active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getContractLabor(), r.getLaborPlan(), r.getLaborActual(), r.getOperationPlan(), r.getOperationActual(), r.getTotalPlan(), r.getNoteU1(), r.getNoteU2(), r.getBrkMonthPlan(), r.getBrkMonthActual(), r.getBrkYtdPlan(), r.getBrkYtdActual(), r.getBrkPrevActual(), r.getBrkNote(), null);
            return new Upsert(active, true);
        }
        SteamIncineratorPlanMonthly deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getContractLabor(), r.getLaborPlan(), r.getLaborActual(), r.getOperationPlan(), r.getOperationActual(), r.getTotalPlan(), r.getNoteU1(), r.getNoteU2(), r.getBrkMonthPlan(), r.getBrkMonthActual(), r.getBrkYtdPlan(), r.getBrkYtdActual(), r.getBrkPrevActual(), r.getBrkNote(), null);
            return new Upsert(deleted, true);
        }
        SteamIncineratorPlanMonthly created = repository.save(SteamIncineratorPlanMonthly.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .contractLabor(r.getContractLabor())
                .laborPlan(r.getLaborPlan())
                .laborActual(r.getLaborActual())
                .operationPlan(r.getOperationPlan())
                .operationActual(r.getOperationActual())
                .totalPlan(r.getTotalPlan())
                .noteU1(r.getNoteU1())
                .noteU2(r.getNoteU2())
                .brkMonthPlan(r.getBrkMonthPlan())
                .brkMonthActual(r.getBrkMonthActual())
                .brkYtdPlan(r.getBrkYtdPlan())
                .brkYtdActual(r.getBrkYtdActual())
                .brkPrevActual(r.getBrkPrevActual())
                .brkNote(r.getBrkNote())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamIncineratorPlanMonthly> scope(int year, Integer month) {
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

    private SteamIncineratorPlanMonthly findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("소각로 사업계획대비", id));
    }
}
