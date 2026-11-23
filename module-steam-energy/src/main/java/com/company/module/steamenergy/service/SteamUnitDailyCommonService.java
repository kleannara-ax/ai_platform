package com.company.module.steamenergy.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamDailyPeriod;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonBulkRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonSaveRequest;
import com.company.module.steamenergy.entity.SteamUnitDailyCommon;
import com.company.module.steamenergy.repository.SteamUnitDailyCommonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 원단위 일지(일자별 공통 계측값) 비즈니스 로직 — 일자 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamUnitDailyCommonService {

    private final SteamUnitDailyCommonRepository repository;

    public List<SteamUnitDailyCommonResponse> getList(int year, Integer month) {
        SteamDailyPeriod period = SteamDailyPeriod.of(year, month);
        return repository.findByUsageDateBetweenOrderByUsageDateAsc(period.from(), period.to())
                .stream().map(SteamUnitDailyCommonResponse::from).toList();
    }

    public SteamUnitDailyCommonResponse get(Long id) {
        return SteamUnitDailyCommonResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamUnitDailyCommonResponse save(SteamUnitDailyCommonSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 원단위 공통값 {} - {}", result.updated() ? "갱신" : "신규", request.getUsageDate());
        return SteamUnitDailyCommonResponse.from(result.entity(), result.updated());
    }

    @Transactional
    public SteamBulkSaveResponse saveMonth(SteamUnitDailyCommonBulkRequest request) {
        SteamDailyPeriod period = SteamDailyPeriod.of(request.getYear(), request.getMonth());
        int inserted = 0, updated = 0, cleared = 0;
        Set<LocalDate> dates = new HashSet<>();
        for (SteamUnitDailyCommonSaveRequest row : request.getRows()) {
            period.requireContains(row.getUsageDate());
            dates.add(row.getUsageDate());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamUnitDailyCommon existing : repository.findByUsageDateBetweenOrderByUsageDateAsc(period.from(), period.to())) {
                if (dates.contains(existing.getUsageDate())) continue;
                existing.clearValues(null);
                cleared += 1;
            }
        }
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 원단위 공통값 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamUnitDailyCommon entity, boolean updated) {}

    private Upsert upsert(SteamUnitDailyCommonSaveRequest r) {
        SteamUnitDailyCommon active = repository.findByUsageDate(r.getUsageDate()).orElse(null);
        if (active != null) {
            active.update(r.getDisperserTotal(), r.getTocSteam(), r.getPica121Vent(), null);
            return new Upsert(active, true);
        }
        SteamUnitDailyCommon deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getDisperserTotal(), r.getTocSteam(), r.getPica121Vent(), null);
            return new Upsert(deleted, true);
        }
        SteamUnitDailyCommon created = repository.save(SteamUnitDailyCommon.builder()
                .usageDate(r.getUsageDate())
                .disperserTotal(r.getDisperserTotal())
                .tocSteam(r.getTocSteam())
                .pica121Vent(r.getPica121Vent())
                .build());
        return new Upsert(created, false);
    }

    private SteamUnitDailyCommon findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("원단위 공통값", id));
    }
}
