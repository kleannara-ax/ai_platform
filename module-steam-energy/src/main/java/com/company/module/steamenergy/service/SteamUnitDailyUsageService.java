package com.company.module.steamenergy.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamDailyPeriod;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageBulkRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageSaveRequest;
import com.company.module.steamenergy.entity.SteamUnitDailyUsage;
import com.company.module.steamenergy.repository.SteamUnitDailyUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 원단위 일지(호기별 일일 실적) 비즈니스 로직
 *
 * <p>저장은 (일자 + 호기) 기준 업서트. 같은 키의 소프트 삭제 행이 있으면 되살려 쓴다(UNIQUE 충돌 방지).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamUnitDailyUsageService {

    private final SteamUnitDailyUsageRepository repository;

    /** 연/월(월 생략 시 연도 전체)·호기(생략 시 전체) 목록 — 일자·호기 순 */
    public List<SteamUnitDailyUsageResponse> getList(int year, Integer month, String machineCode) {
        SteamDailyPeriod period = SteamDailyPeriod.of(year, month);
        List<SteamUnitDailyUsage> rows = (machineCode == null || machineCode.isBlank())
                ? repository.findByUsageDateBetweenOrderByUsageDateAscMachineCodeAsc(period.from(), period.to())
                : repository.findByUsageDateBetweenAndMachineCodeOrderByUsageDateAsc(period.from(), period.to(), machineCode);
        return rows.stream().map(SteamUnitDailyUsageResponse::from).toList();
    }

    public SteamUnitDailyUsageResponse get(Long id) {
        return SteamUnitDailyUsageResponse.from(findOrThrow(id));
    }

    /** (일자 + 호기) 업서트 */
    @Transactional
    public SteamUnitDailyUsageResponse save(SteamUnitDailyUsageSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 원단위 일지 {} - {} {}", result.updated() ? "갱신" : "신규",
                request.getUsageDate(), request.getMachineCode());
        return SteamUnitDailyUsageResponse.from(result.entity(), result.updated());
    }

    /**
     * 한 달치 일괄 저장 (엑셀 가져오기).
     * clearMissing 이면 그 달 기존 행 중 목록에 없는 (일자·호기)는 값만 비운다 — 행을 지우지 않아 UNIQUE·소프트 삭제 충돌이 없다.
     */
    @Transactional
    public SteamBulkSaveResponse saveMonth(SteamUnitDailyUsageBulkRequest request) {
        SteamDailyPeriod period = SteamDailyPeriod.of(request.getYear(), request.getMonth());
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamUnitDailyUsageSaveRequest row : request.getRows()) {
            period.requireContains(row.getUsageDate());
            keys.add(key(row.getUsageDate().toString(), row.getMachineCode()));
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamUnitDailyUsage existing : repository.findByUsageDateBetweenOrderByUsageDateAscMachineCodeAsc(period.from(), period.to())) {
                if (keys.contains(key(existing.getUsageDate().toString(), existing.getMachineCode()))) continue;
                existing.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 원단위 일지 일괄 저장 {}-{}: 신규 {}, 갱신 {}, 비움 {}",
                request.getYear(), request.getMonth(), inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    /** 소프트 삭제 */
    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 원단위 일지 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamUnitDailyUsage entity, boolean updated) {}

    private Upsert upsert(SteamUnitDailyUsageSaveRequest r) {
        SteamUnitDailyUsage active = repository.findByUsageDateAndMachineCode(r.getUsageDate(), r.getMachineCode()).orElse(null);
        if (active != null) {
            apply(active, r);
            return new Upsert(active, true);
        }
        SteamUnitDailyUsage deleted = repository.findAnyByKey(r.getUsageDate(), r.getMachineCode()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            apply(deleted, r);
            return new Upsert(deleted, true);
        }
        SteamUnitDailyUsage created = repository.save(SteamUnitDailyUsage.builder()
                .usageDate(r.getUsageDate())
                .machineCode(r.getMachineCode())
                .mainSteam(r.getMainSteam())
                .coaterSteam(r.getCoaterSteam())
                .ventilationSteam(r.getVentilationSteam())
                .steam(r.getSteam())
                .productionKg(r.getProductionKg())
                .productionType(r.getProductionType())
                .runtimeHours(r.getRuntimeHours())
                .disperserRuntimeHours(r.getDisperserRuntimeHours())
                .ventRuntimeHours(r.getVentRuntimeHours())
                .note(r.getNote())
                .build());
        return new Upsert(created, false);
    }

    private static void apply(SteamUnitDailyUsage e, SteamUnitDailyUsageSaveRequest r) {
        e.update(r.getMainSteam(), r.getCoaterSteam(), r.getVentilationSteam(), r.getSteam(),
                r.getProductionKg(), r.getProductionType(),
                r.getRuntimeHours(), r.getDisperserRuntimeHours(), r.getVentRuntimeHours(),
                r.getNote(), null);
    }

    private static String key(String date, String machine) {
        return date + "|" + machine;
    }

    private SteamUnitDailyUsage findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("원단위 일지", id));
    }
}
