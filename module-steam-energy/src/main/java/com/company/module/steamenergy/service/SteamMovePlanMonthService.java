package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamMovePlanMonthBulkRequest;
import com.company.module.steamenergy.dto.SteamMovePlanMonthResponse;
import com.company.module.steamenergy.dto.SteamMovePlanMonthSaveRequest;
import com.company.module.steamenergy.entity.SteamMovePlanMonth;
import com.company.module.steamenergy.repository.SteamMovePlanMonthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 이동계획 월 기준값 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamMovePlanMonthService {

    private final SteamMovePlanMonthRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamMovePlanMonthResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamMovePlanMonthResponse::from).toList();
    }

    public SteamMovePlanMonthResponse get(Long id) {
        return SteamMovePlanMonthResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamMovePlanMonthResponse save(SteamMovePlanMonthSaveRequest request) {
        Upsert result = upsert(request);
        return SteamMovePlanMonthResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamMovePlanMonthBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamMovePlanMonthSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() != null && r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamMovePlanMonth e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 이동계획 월 기준값 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 이동계획 월 기준값 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamMovePlanMonth entity, boolean updated) {}

    private Upsert upsert(SteamMovePlanMonthSaveRequest r) {
        SteamMovePlanMonth active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getActualUntilDay(), r.getAdjustYn(), r.getWaste2BillCapTon(), r.getComboGuaranteeTon(), r.getComboBscPrice(), r.getComboAscPrice(), r.getFluidBillTonHour(), r.getFluidImprovementCost(), r.getLngM3PerTon(), r.getLngUnitPrice(), r.getNote(), null);
            return new Upsert(active, true);
        }
        SteamMovePlanMonth deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getActualUntilDay(), r.getAdjustYn(), r.getWaste2BillCapTon(), r.getComboGuaranteeTon(), r.getComboBscPrice(), r.getComboAscPrice(), r.getFluidBillTonHour(), r.getFluidImprovementCost(), r.getLngM3PerTon(), r.getLngUnitPrice(), r.getNote(), null);
            return new Upsert(deleted, true);
        }
        SteamMovePlanMonth created = repository.save(SteamMovePlanMonth.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .actualUntilDay(r.getActualUntilDay())
                .adjustYn(r.getAdjustYn())
                .waste2BillCapTon(r.getWaste2BillCapTon())
                .comboGuaranteeTon(r.getComboGuaranteeTon())
                .comboBscPrice(r.getComboBscPrice())
                .comboAscPrice(r.getComboAscPrice())
                .fluidBillTonHour(r.getFluidBillTonHour())
                .fluidImprovementCost(r.getFluidImprovementCost())
                .lngM3PerTon(r.getLngM3PerTon())
                .lngUnitPrice(r.getLngUnitPrice())
                .note(r.getNote())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamMovePlanMonth> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(year, month);
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamMovePlanMonth findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("이동계획 월 기준값", id));
    }
}
