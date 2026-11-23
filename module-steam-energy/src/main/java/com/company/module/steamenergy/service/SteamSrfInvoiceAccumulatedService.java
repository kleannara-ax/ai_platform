package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAccumulatedBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAccumulatedResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAccumulatedSaveRequest;
import com.company.module.steamenergy.entity.SteamSrfInvoiceAccumulated;
import com.company.module.steamenergy.repository.SteamSrfInvoiceAccumulatedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SRF 인보이스 누계 스팀량 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamSrfInvoiceAccumulatedService {

    private final SteamSrfInvoiceAccumulatedRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamSrfInvoiceAccumulatedResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamSrfInvoiceAccumulatedResponse::from).toList();
    }

    public SteamSrfInvoiceAccumulatedResponse get(Long id) {
        return SteamSrfInvoiceAccumulatedResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamSrfInvoiceAccumulatedResponse save(SteamSrfInvoiceAccumulatedSaveRequest request) {
        Upsert result = upsert(request);
        return SteamSrfInvoiceAccumulatedResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamSrfInvoiceAccumulatedBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamSrfInvoiceAccumulatedSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamSrfInvoiceAccumulated e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] SRF 인보이스 누계 스팀량 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] SRF 인보이스 누계 스팀량 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamSrfInvoiceAccumulated entity, boolean updated) {}

    private Upsert upsert(SteamSrfInvoiceAccumulatedSaveRequest r) {
        SteamSrfInvoiceAccumulated active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getContractVolume(), r.getBscVolume(), r.getAscVolume(), null);
            return new Upsert(active, true);
        }
        SteamSrfInvoiceAccumulated deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getContractVolume(), r.getBscVolume(), r.getAscVolume(), null);
            return new Upsert(deleted, true);
        }
        SteamSrfInvoiceAccumulated created = repository.save(SteamSrfInvoiceAccumulated.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .contractVolume(r.getContractVolume())
                .bscVolume(r.getBscVolume())
                .ascVolume(r.getAscVolume())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamSrfInvoiceAccumulated> scope(int year, Integer month) {
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

    private SteamSrfInvoiceAccumulated findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("SRF 인보이스 누계 스팀량", id));
    }
}
