package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceMonthlyResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceMonthlySaveRequest;
import com.company.module.steamenergy.entity.SteamSrfInvoiceMonthly;
import com.company.module.steamenergy.repository.SteamSrfInvoiceMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SRF 인보이스 월별 운영내역 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamSrfInvoiceMonthlyService {

    private final SteamSrfInvoiceMonthlyRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamSrfInvoiceMonthlyResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamSrfInvoiceMonthlyResponse::from).toList();
    }

    public SteamSrfInvoiceMonthlyResponse get(Long id) {
        return SteamSrfInvoiceMonthlyResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamSrfInvoiceMonthlyResponse save(SteamSrfInvoiceMonthlySaveRequest request) {
        Upsert result = upsert(request);
        return SteamSrfInvoiceMonthlyResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamSrfInvoiceMonthlyBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamSrfInvoiceMonthlySaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamSrfInvoiceMonthly e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] SRF 인보이스 월별 운영내역 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] SRF 인보이스 월별 운영내역 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamSrfInvoiceMonthly entity, boolean updated) {}

    private Upsert upsert(SteamSrfInvoiceMonthlySaveRequest r) {
        SteamSrfInvoiceMonthly active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getSteamQty(), r.getSteamUnit(), r.getPowerQty(), r.getPowerUnit(), r.getLngQty(), r.getLngUnit(), r.getProcessQty(), r.getProcessUnit(), r.getSoftQty(), r.getSoftUnit(), r.getSoftenQty(), r.getSoftenUnit(), r.getLifeQty(), r.getLifeUnit(), r.getEtcSpecial(), r.getEtcBsc7(), r.getEtcSrfShare(), r.getEtcInspect(), r.getEtcOver7(), r.getEtcAirOver(), r.getEtcWaste(), null);
            return new Upsert(active, true);
        }
        SteamSrfInvoiceMonthly deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getSteamQty(), r.getSteamUnit(), r.getPowerQty(), r.getPowerUnit(), r.getLngQty(), r.getLngUnit(), r.getProcessQty(), r.getProcessUnit(), r.getSoftQty(), r.getSoftUnit(), r.getSoftenQty(), r.getSoftenUnit(), r.getLifeQty(), r.getLifeUnit(), r.getEtcSpecial(), r.getEtcBsc7(), r.getEtcSrfShare(), r.getEtcInspect(), r.getEtcOver7(), r.getEtcAirOver(), r.getEtcWaste(), null);
            return new Upsert(deleted, true);
        }
        SteamSrfInvoiceMonthly created = repository.save(SteamSrfInvoiceMonthly.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .steamQty(r.getSteamQty())
                .steamUnit(r.getSteamUnit())
                .powerQty(r.getPowerQty())
                .powerUnit(r.getPowerUnit())
                .lngQty(r.getLngQty())
                .lngUnit(r.getLngUnit())
                .processQty(r.getProcessQty())
                .processUnit(r.getProcessUnit())
                .softQty(r.getSoftQty())
                .softUnit(r.getSoftUnit())
                .softenQty(r.getSoftenQty())
                .softenUnit(r.getSoftenUnit())
                .lifeQty(r.getLifeQty())
                .lifeUnit(r.getLifeUnit())
                .etcSpecial(r.getEtcSpecial())
                .etcBsc7(r.getEtcBsc7())
                .etcSrfShare(r.getEtcSrfShare())
                .etcInspect(r.getEtcInspect())
                .etcOver7(r.getEtcOver7())
                .etcAirOver(r.getEtcAirOver())
                .etcWaste(r.getEtcWaste())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamSrfInvoiceMonthly> scope(int year, Integer month) {
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

    private SteamSrfInvoiceMonthly findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("SRF 인보이스 월별 운영내역", id));
    }
}
