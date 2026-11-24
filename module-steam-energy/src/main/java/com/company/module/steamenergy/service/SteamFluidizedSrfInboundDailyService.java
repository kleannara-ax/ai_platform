package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedSrfInboundDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedSrfInboundDailyResponse;
import com.company.module.steamenergy.dto.SteamFluidizedSrfInboundDailySaveRequest;
import com.company.module.steamenergy.entity.SteamFluidizedSrfInboundDaily;
import com.company.module.steamenergy.repository.SteamFluidizedSrfInboundDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유동상 SRF 입고내역 비즈니스 로직 — (USAGE_DATE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFluidizedSrfInboundDailyService {

    private final SteamFluidizedSrfInboundDailyRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamFluidizedSrfInboundDailyResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamFluidizedSrfInboundDailyResponse::from).toList();
    }

    public SteamFluidizedSrfInboundDailyResponse get(Long id) {
        return SteamFluidizedSrfInboundDailyResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFluidizedSrfInboundDailyResponse save(SteamFluidizedSrfInboundDailySaveRequest request) {
        Upsert result = upsert(request);
        return SteamFluidizedSrfInboundDailyResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamFluidizedSrfInboundDailyBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamFluidizedSrfInboundDailySaveRequest r : request.getRows()) {
            if (!(r.getUsageDate().getYear() == year && (month == null || r.getUsageDate().getMonthValue() == month))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getUsageDate()));
            }
            keys.add("" + r.getUsageDate());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFluidizedSrfInboundDaily e : scope(year, month)) {
                if (keys.contains("" + e.getUsageDate())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 유동상 SRF 입고내역 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 유동상 SRF 입고내역 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamFluidizedSrfInboundDaily entity, boolean updated) {}

    private Upsert upsert(SteamFluidizedSrfInboundDailySaveRequest r) {
        SteamFluidizedSrfInboundDaily active = repository.findActive(r.getUsageDate()).orElse(null);
        if (active != null) {
            active.update(r.getVendor01Kg(), r.getVendor02Kg(), r.getVendor03Kg(), r.getVendor04Kg(), r.getVendor05Kg(), r.getVendor06Kg(), r.getVendor07Kg(), r.getVendor08Kg(), r.getVendor09Kg(), r.getVendor10Kg(), null);
            return new Upsert(active, true);
        }
        SteamFluidizedSrfInboundDaily deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getVendor01Kg(), r.getVendor02Kg(), r.getVendor03Kg(), r.getVendor04Kg(), r.getVendor05Kg(), r.getVendor06Kg(), r.getVendor07Kg(), r.getVendor08Kg(), r.getVendor09Kg(), r.getVendor10Kg(), null);
            return new Upsert(deleted, true);
        }
        SteamFluidizedSrfInboundDaily created = repository.save(SteamFluidizedSrfInboundDaily.builder()
                .usageDate(r.getUsageDate())
                .vendor01Kg(r.getVendor01Kg())
                .vendor02Kg(r.getVendor02Kg())
                .vendor03Kg(r.getVendor03Kg())
                .vendor04Kg(r.getVendor04Kg())
                .vendor05Kg(r.getVendor05Kg())
                .vendor06Kg(r.getVendor06Kg())
                .vendor07Kg(r.getVendor07Kg())
                .vendor08Kg(r.getVendor08Kg())
                .vendor09Kg(r.getVendor09Kg())
                .vendor10Kg(r.getVendor10Kg())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamFluidizedSrfInboundDaily> scope(int year, Integer month) {
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

    private SteamFluidizedSrfInboundDaily findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("유동상 SRF 입고내역", id));
    }
}
