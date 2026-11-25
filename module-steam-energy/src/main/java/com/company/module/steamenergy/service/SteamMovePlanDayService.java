package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamMovePlanDayBulkRequest;
import com.company.module.steamenergy.dto.SteamMovePlanDayResponse;
import com.company.module.steamenergy.dto.SteamMovePlanDaySaveRequest;
import com.company.module.steamenergy.entity.SteamMovePlanDay;
import com.company.module.steamenergy.repository.SteamMovePlanDayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 이동계획 일자별 운휴 비즈니스 로직 — (PLAN_DATE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamMovePlanDayService {

    private final SteamMovePlanDayRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamMovePlanDayResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamMovePlanDayResponse::from).toList();
    }

    public SteamMovePlanDayResponse get(Long id) {
        return SteamMovePlanDayResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamMovePlanDayResponse save(SteamMovePlanDaySaveRequest request) {
        Upsert result = upsert(request);
        return SteamMovePlanDayResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamMovePlanDayBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamMovePlanDaySaveRequest r : request.getRows()) {
            if (!(r.getPlanDate() != null && r.getPlanDate().getYear() == year && (month == null || r.getPlanDate().getMonthValue() == month))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + (String.valueOf(r.getPlanDate())));
            }
            keys.add(String.valueOf(r.getPlanDate()));
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamMovePlanDay e : scope(year, month)) {
                if (keys.contains(String.valueOf(e.getPlanDate()))) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 이동계획 일자별 운휴 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 이동계획 일자별 운휴 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamMovePlanDay entity, boolean updated) {}

    private Upsert upsert(SteamMovePlanDaySaveRequest r) {
        SteamMovePlanDay active = repository.findActive(r.getPlanDate()).orElse(null);
        if (active != null) {
            active.update(r.getDownPm2(), r.getDownPm3(), r.getDownTm3(), r.getDownTm4(), r.getDownTm5(), r.getStopWaste1(), r.getStopWaste2(), r.getStopCombo(), r.getStopFluid(), r.getStopLng20(), r.getStopLng50(), r.getIdleWaste1(), r.getIdleWaste2(), r.getIdleCombo(), r.getIdleFluid(), r.getIdleLng20(), r.getIdleLng50(), r.getNote(), null);
            return new Upsert(active, true);
        }
        SteamMovePlanDay deleted = repository.findAnyByKey(r.getPlanDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getDownPm2(), r.getDownPm3(), r.getDownTm3(), r.getDownTm4(), r.getDownTm5(), r.getStopWaste1(), r.getStopWaste2(), r.getStopCombo(), r.getStopFluid(), r.getStopLng20(), r.getStopLng50(), r.getIdleWaste1(), r.getIdleWaste2(), r.getIdleCombo(), r.getIdleFluid(), r.getIdleLng20(), r.getIdleLng50(), r.getNote(), null);
            return new Upsert(deleted, true);
        }
        SteamMovePlanDay created = repository.save(SteamMovePlanDay.builder()
                .planDate(r.getPlanDate())
                .downPm2(r.getDownPm2())
                .downPm3(r.getDownPm3())
                .downTm3(r.getDownTm3())
                .downTm4(r.getDownTm4())
                .downTm5(r.getDownTm5())
                .stopWaste1(r.getStopWaste1())
                .stopWaste2(r.getStopWaste2())
                .stopCombo(r.getStopCombo())
                .stopFluid(r.getStopFluid())
                .stopLng20(r.getStopLng20())
                .stopLng50(r.getStopLng50())
                .idleWaste1(r.getIdleWaste1())
                .idleWaste2(r.getIdleWaste2())
                .idleCombo(r.getIdleCombo())
                .idleFluid(r.getIdleFluid())
                .idleLng20(r.getIdleLng20())
                .idleLng50(r.getIdleLng50())
                .note(r.getNote())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamMovePlanDay> scope(int year, Integer month) {
        requireScope(year, month);
        LocalDate from = LocalDate.of(year, month == null ? 1 : month, 1);
        LocalDate to = month == null ? LocalDate.of(year, 12, 31) : from.plusMonths(1).minusDays(1);
        return repository.findScope(from, to);
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamMovePlanDay findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("이동계획 일자별 운휴", id));
    }
}
