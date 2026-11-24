package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceBscBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceBscResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceBscSaveRequest;
import com.company.module.steamenergy.entity.SteamSrfInvoiceBsc;
import com.company.module.steamenergy.repository.SteamSrfInvoiceBscRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SRF 인보이스 BSC 단가 비즈니스 로직 — (YEAR_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamSrfInvoiceBscService {

    private final SteamSrfInvoiceBscRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamSrfInvoiceBscResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamSrfInvoiceBscResponse::from).toList();
    }

    public SteamSrfInvoiceBscResponse get(Long id) {
        return SteamSrfInvoiceBscResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamSrfInvoiceBscResponse save(SteamSrfInvoiceBscSaveRequest request) {
        Upsert result = upsert(request);
        return SteamSrfInvoiceBscResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamSrfInvoiceBscBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamSrfInvoiceBscSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo()));
            }
            keys.add("" + r.getYearNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamSrfInvoiceBsc e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] SRF 인보이스 BSC 단가 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] SRF 인보이스 BSC 단가 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamSrfInvoiceBsc entity, boolean updated) {}

    private Upsert upsert(SteamSrfInvoiceBscSaveRequest r) {
        SteamSrfInvoiceBsc active = repository.findActive(r.getYearNo()).orElse(null);
        if (active != null) {
            active.update(r.getBscRate(), null);
            return new Upsert(active, true);
        }
        SteamSrfInvoiceBsc deleted = repository.findAnyByKey(r.getYearNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getBscRate(), null);
            return new Upsert(deleted, true);
        }
        SteamSrfInvoiceBsc created = repository.save(SteamSrfInvoiceBsc.builder()
                .yearNo(r.getYearNo())
                .bscRate(r.getBscRate())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamSrfInvoiceBsc> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(year);
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamSrfInvoiceBsc findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("SRF 인보이스 BSC 단가", id));
    }
}
