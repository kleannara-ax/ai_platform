package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceSummaryBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceSummaryResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceSummarySaveRequest;
import com.company.module.steamenergy.entity.SteamSrfInvoiceSummary;
import com.company.module.steamenergy.repository.SteamSrfInvoiceSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SRF 인보이스 Summary 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamSrfInvoiceSummaryService {

    private final SteamSrfInvoiceSummaryRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamSrfInvoiceSummaryResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamSrfInvoiceSummaryResponse::from).toList();
    }

    public SteamSrfInvoiceSummaryResponse get(Long id) {
        return SteamSrfInvoiceSummaryResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamSrfInvoiceSummaryResponse save(SteamSrfInvoiceSummarySaveRequest request) {
        Upsert result = upsert(request);
        return SteamSrfInvoiceSummaryResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamSrfInvoiceSummaryBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamSrfInvoiceSummarySaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamSrfInvoiceSummary e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] SRF 인보이스 Summary 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] SRF 인보이스 Summary 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamSrfInvoiceSummary entity, boolean updated) {}

    private Upsert upsert(SteamSrfInvoiceSummarySaveRequest r) {
        SteamSrfInvoiceSummary active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getBscRate(), r.getBscFactor(), r.getAfbscrBsc(), r.getBscSteamAmount(), r.getAscRate(), r.getAscFactor(), r.getAscSteamAmount(), r.getOthers0(), r.getOthers1(), r.getOthers2(), r.getOthers3(), r.getPayableAdjust(), r.getLiquidatedDamage(), r.getAscRefundAnnual(), r.getUtilitiesDeduction(), r.getOtherDeduction1(), r.getOtherDeduction2(), null);
            return new Upsert(active, true);
        }
        SteamSrfInvoiceSummary deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getBscRate(), r.getBscFactor(), r.getAfbscrBsc(), r.getBscSteamAmount(), r.getAscRate(), r.getAscFactor(), r.getAscSteamAmount(), r.getOthers0(), r.getOthers1(), r.getOthers2(), r.getOthers3(), r.getPayableAdjust(), r.getLiquidatedDamage(), r.getAscRefundAnnual(), r.getUtilitiesDeduction(), r.getOtherDeduction1(), r.getOtherDeduction2(), null);
            return new Upsert(deleted, true);
        }
        SteamSrfInvoiceSummary created = repository.save(SteamSrfInvoiceSummary.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .bscRate(r.getBscRate())
                .bscFactor(r.getBscFactor())
                .afbscrBsc(r.getAfbscrBsc())
                .bscSteamAmount(r.getBscSteamAmount())
                .ascRate(r.getAscRate())
                .ascFactor(r.getAscFactor())
                .ascSteamAmount(r.getAscSteamAmount())
                .others0(r.getOthers0())
                .others1(r.getOthers1())
                .others2(r.getOthers2())
                .others3(r.getOthers3())
                .payableAdjust(r.getPayableAdjust())
                .liquidatedDamage(r.getLiquidatedDamage())
                .ascRefundAnnual(r.getAscRefundAnnual())
                .utilitiesDeduction(r.getUtilitiesDeduction())
                .otherDeduction1(r.getOtherDeduction1())
                .otherDeduction2(r.getOtherDeduction2())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamSrfInvoiceSummary> scope(int year, Integer month) {
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

    private SteamSrfInvoiceSummary findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("SRF 인보이스 Summary", id));
    }
}
