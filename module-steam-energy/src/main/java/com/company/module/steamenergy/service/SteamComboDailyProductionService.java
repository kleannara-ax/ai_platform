package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboDailyProductionBulkRequest;
import com.company.module.steamenergy.dto.SteamComboDailyProductionResponse;
import com.company.module.steamenergy.dto.SteamComboDailyProductionSaveRequest;
import com.company.module.steamenergy.entity.SteamComboDailyProduction;
import com.company.module.steamenergy.repository.SteamComboDailyProductionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 복합보일러 스팀생산실적 비즈니스 로직 — (USAGE_DATE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamComboDailyProductionService {

    private final SteamComboDailyProductionRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamComboDailyProductionResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamComboDailyProductionResponse::from).toList();
    }

    public SteamComboDailyProductionResponse get(Long id) {
        return SteamComboDailyProductionResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamComboDailyProductionResponse save(SteamComboDailyProductionSaveRequest request) {
        Upsert result = upsert(request);
        return SteamComboDailyProductionResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamComboDailyProductionBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamComboDailyProductionSaveRequest r : request.getRows()) {
            if (!(r.getUsageDate().getYear() == year && (month == null || r.getUsageDate().getMonthValue() == month))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getUsageDate()));
            }
            keys.add("" + r.getUsageDate());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamComboDailyProduction e : scope(year, month)) {
                if (keys.contains("" + e.getUsageDate())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 복합보일러 스팀생산실적 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 복합보일러 스팀생산실적 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamComboDailyProduction entity, boolean updated) {}

    private Upsert upsert(SteamComboDailyProductionSaveRequest r) {
        SteamComboDailyProduction active = repository.findActive(r.getUsageDate()).orElse(null);
        if (active != null) {
            active.update(r.getComboSteam(), r.getComboCondensate(), r.getComboMakeup(), r.getComboDeaerator(), r.getComboProcess(), r.getComboDilution(), r.getExternalSteam(), r.getExternalCondensate(), r.getExternalMakeup(), r.getWasteSteam1(), r.getWasteSteam2(), r.getKn20(), r.getKn50(), r.getFluidizedSteam(), r.getFluidizedCondensate(), r.getRuntime20(), r.getRuntime50(), r.getRuntimeFluidized(), null);
            return new Upsert(active, true);
        }
        SteamComboDailyProduction deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getComboSteam(), r.getComboCondensate(), r.getComboMakeup(), r.getComboDeaerator(), r.getComboProcess(), r.getComboDilution(), r.getExternalSteam(), r.getExternalCondensate(), r.getExternalMakeup(), r.getWasteSteam1(), r.getWasteSteam2(), r.getKn20(), r.getKn50(), r.getFluidizedSteam(), r.getFluidizedCondensate(), r.getRuntime20(), r.getRuntime50(), r.getRuntimeFluidized(), null);
            return new Upsert(deleted, true);
        }
        SteamComboDailyProduction created = repository.save(SteamComboDailyProduction.builder()
                .usageDate(r.getUsageDate())
                .comboSteam(r.getComboSteam())
                .comboCondensate(r.getComboCondensate())
                .comboMakeup(r.getComboMakeup())
                .comboDeaerator(r.getComboDeaerator())
                .comboProcess(r.getComboProcess())
                .comboDilution(r.getComboDilution())
                .externalSteam(r.getExternalSteam())
                .externalCondensate(r.getExternalCondensate())
                .externalMakeup(r.getExternalMakeup())
                .wasteSteam1(r.getWasteSteam1())
                .wasteSteam2(r.getWasteSteam2())
                .kn20(r.getKn20())
                .kn50(r.getKn50())
                .fluidizedSteam(r.getFluidizedSteam())
                .fluidizedCondensate(r.getFluidizedCondensate())
                .runtime20(r.getRuntime20())
                .runtime50(r.getRuntime50())
                .runtimeFluidized(r.getRuntimeFluidized())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamComboDailyProduction> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(LocalDate.of(year, month == null ? 1 : month, 1), month == null ? LocalDate.of(year, 12, 31) : LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamComboDailyProduction findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("복합보일러 스팀생산실적", id));
    }
}
