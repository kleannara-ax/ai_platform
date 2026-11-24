package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementPaymentBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementPaymentResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementPaymentSaveRequest;
import com.company.module.steamenergy.entity.SteamFluidizedImprovementPayment;
import com.company.module.steamenergy.repository.SteamFluidizedImprovementPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유동상 설비개선 월별 비용 비즈니스 로직 — (YEAR_NO, RECORD_KEY) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFluidizedImprovementPaymentService {

    private final SteamFluidizedImprovementPaymentRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamFluidizedImprovementPaymentResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamFluidizedImprovementPaymentResponse::from).toList();
    }

    public SteamFluidizedImprovementPaymentResponse get(Long id) {
        return SteamFluidizedImprovementPaymentResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFluidizedImprovementPaymentResponse save(SteamFluidizedImprovementPaymentSaveRequest request) {
        Upsert result = upsert(request);
        return SteamFluidizedImprovementPaymentResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 삭제한다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamFluidizedImprovementPaymentBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamFluidizedImprovementPaymentSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getRecordKey()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getRecordKey());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFluidizedImprovementPayment e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getRecordKey())) continue;
                e.delete(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 유동상 설비개선 월별 비용 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 유동상 설비개선 월별 비용 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamFluidizedImprovementPayment entity, boolean updated) {}

    private Upsert upsert(SteamFluidizedImprovementPaymentSaveRequest r) {
        SteamFluidizedImprovementPayment active = repository.findActive(r.getYearNo(), r.getRecordKey()).orElse(null);
        if (active != null) {
            active.update(r.getPayMonth(), r.getPaymentAmount(), r.getSpentAmount(), r.getVendorName(), r.getUsageDesc(), r.getSortOrder(), null);
            return new Upsert(active, true);
        }
        SteamFluidizedImprovementPayment deleted = repository.findAnyByKey(r.getYearNo(), r.getRecordKey()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getPayMonth(), r.getPaymentAmount(), r.getSpentAmount(), r.getVendorName(), r.getUsageDesc(), r.getSortOrder(), null);
            return new Upsert(deleted, true);
        }
        SteamFluidizedImprovementPayment created = repository.save(SteamFluidizedImprovementPayment.builder()
                .yearNo(r.getYearNo())
                .recordKey(r.getRecordKey())
                .payMonth(r.getPayMonth())
                .paymentAmount(r.getPaymentAmount())
                .spentAmount(r.getSpentAmount())
                .vendorName(r.getVendorName())
                .usageDesc(r.getUsageDesc())
                .sortOrder(r.getSortOrder())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamFluidizedImprovementPayment> scope(int year, Integer month) {
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

    private SteamFluidizedImprovementPayment findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("유동상 설비개선 월별 비용", id));
    }
}
