package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamMovePlanMachineBulkRequest;
import com.company.module.steamenergy.dto.SteamMovePlanMachineResponse;
import com.company.module.steamenergy.dto.SteamMovePlanMachineSaveRequest;
import com.company.module.steamenergy.entity.SteamMovePlanMachine;
import com.company.module.steamenergy.repository.SteamMovePlanMachineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 이동계획 호기별 생산계획 비즈니스 로직 — (YEAR_NO, MONTH_NO, MACHINE_CODE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamMovePlanMachineService {

    private final SteamMovePlanMachineRepository repository;

    /** 쓸 수 있는 호기 코드 */
    public static final List<String> CODES = List.of("PM-2", "PM-3", "TM-3", "TM-4", "TM-5");

    private static void requireCode(String code) {
        if (code == null || !CODES.contains(code)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "호기 코드가 올바르지 않습니다: " + code + " (가능한 값: " + String.join(", ", CODES) + ")");
        }
    }

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamMovePlanMachineResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamMovePlanMachineResponse::from).toList();
    }

    public SteamMovePlanMachineResponse get(Long id) {
        return SteamMovePlanMachineResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamMovePlanMachineResponse save(SteamMovePlanMachineSaveRequest request) {
        Upsert result = upsert(request);
        return SteamMovePlanMachineResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamMovePlanMachineBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamMovePlanMachineSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() != null && r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getMachineCode()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getMachineCode());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamMovePlanMachine e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo() + "|" + e.getMachineCode())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 이동계획 호기별 생산계획 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 이동계획 호기별 생산계획 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamMovePlanMachine entity, boolean updated) {}

    private Upsert upsert(SteamMovePlanMachineSaveRequest r) {
        requireCode(r.getMachineCode());
        SteamMovePlanMachine active = repository.findActive(r.getYearNo(), r.getMonthNo(), r.getMachineCode()).orElse(null);
        if (active != null) {
            active.update(r.getProductionTon(), r.getRuntimeHours(), r.getPlanKind(), r.getUnitOverride(), null);
            return new Upsert(active, true);
        }
        SteamMovePlanMachine deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo(), r.getMachineCode()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getProductionTon(), r.getRuntimeHours(), r.getPlanKind(), r.getUnitOverride(), null);
            return new Upsert(deleted, true);
        }
        SteamMovePlanMachine created = repository.save(SteamMovePlanMachine.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .machineCode(r.getMachineCode())
                .productionTon(r.getProductionTon())
                .runtimeHours(r.getRuntimeHours())
                .planKind(r.getPlanKind())
                .unitOverride(r.getUnitOverride())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamMovePlanMachine> scope(int year, Integer month) {
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

    private SteamMovePlanMachine findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("이동계획 호기별 생산계획", id));
    }
}
