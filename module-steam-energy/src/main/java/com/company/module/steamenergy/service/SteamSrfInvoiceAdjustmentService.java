package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAdjustmentBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAdjustmentResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceAdjustmentSaveRequest;
import com.company.module.steamenergy.entity.SteamSrfInvoiceAdjustment;
import com.company.module.steamenergy.repository.SteamSrfInvoiceAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SRF 인보이스 Adjustment Factor 비즈니스 로직 — (YEAR_NO, SECTION, ITEM_CODE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamSrfInvoiceAdjustmentService {

    private final SteamSrfInvoiceAdjustmentRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamSrfInvoiceAdjustmentResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamSrfInvoiceAdjustmentResponse::from).toList();
    }

    public SteamSrfInvoiceAdjustmentResponse get(Long id) {
        return SteamSrfInvoiceAdjustmentResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamSrfInvoiceAdjustmentResponse save(SteamSrfInvoiceAdjustmentSaveRequest request) {
        Upsert result = upsert(request);
        return SteamSrfInvoiceAdjustmentResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamSrfInvoiceAdjustmentBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamSrfInvoiceAdjustmentSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getSection() + "|" + r.getItemCode()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getSection() + "|" + r.getItemCode());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamSrfInvoiceAdjustment e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getSection() + "|" + e.getItemCode())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] SRF 인보이스 Adjustment Factor 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] SRF 인보이스 Adjustment Factor 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamSrfInvoiceAdjustment entity, boolean updated) {}

    private Upsert upsert(SteamSrfInvoiceAdjustmentSaveRequest r) {
        SteamSrfInvoiceAdjustment active = repository.findActive(r.getYearNo(), r.getSection(), r.getItemCode()).orElse(null);
        if (active != null) {
            active.update(r.getCoefficient(), r.getBaseValue(), r.getPresentValue(), null);
            return new Upsert(active, true);
        }
        SteamSrfInvoiceAdjustment deleted = repository.findAnyByKey(r.getYearNo(), r.getSection(), r.getItemCode()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getCoefficient(), r.getBaseValue(), r.getPresentValue(), null);
            return new Upsert(deleted, true);
        }
        SteamSrfInvoiceAdjustment created = repository.save(SteamSrfInvoiceAdjustment.builder()
                .yearNo(r.getYearNo())
                .section(r.getSection())
                .itemCode(r.getItemCode())
                .coefficient(r.getCoefficient())
                .baseValue(r.getBaseValue())
                .presentValue(r.getPresentValue())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamSrfInvoiceAdjustment> scope(int year, Integer month) {
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

    private SteamSrfInvoiceAdjustment findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("SRF 인보이스 Adjustment Factor", id));
    }
}
