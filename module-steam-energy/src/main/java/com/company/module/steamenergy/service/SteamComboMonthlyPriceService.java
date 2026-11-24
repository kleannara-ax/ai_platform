package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboMonthlyPriceBulkRequest;
import com.company.module.steamenergy.dto.SteamComboMonthlyPriceResponse;
import com.company.module.steamenergy.dto.SteamComboMonthlyPriceSaveRequest;
import com.company.module.steamenergy.entity.SteamComboMonthlyPrice;
import com.company.module.steamenergy.repository.SteamComboMonthlyPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 복합보일러 월 연료단가 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamComboMonthlyPriceService {

    private final SteamComboMonthlyPriceRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamComboMonthlyPriceResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamComboMonthlyPriceResponse::from).toList();
    }

    public SteamComboMonthlyPriceResponse get(Long id) {
        return SteamComboMonthlyPriceResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamComboMonthlyPriceResponse save(SteamComboMonthlyPriceSaveRequest request) {
        Upsert result = upsert(request);
        return SteamComboMonthlyPriceResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamComboMonthlyPriceBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamComboMonthlyPriceSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamComboMonthlyPrice e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 복합보일러 월 연료단가 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 복합보일러 월 연료단가 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamComboMonthlyPrice entity, boolean updated) {}

    private Upsert upsert(SteamComboMonthlyPriceSaveRequest r) {
        SteamComboMonthlyPrice active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getFuelPriceLngBoiler(), r.getFuelPriceFluidized(), r.getFuelPriceCombo(), r.getFuelPriceWaste(), r.getFuelPriceExternal(), r.getVentLossUnitPrice(), null);
            return new Upsert(active, true);
        }
        SteamComboMonthlyPrice deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getFuelPriceLngBoiler(), r.getFuelPriceFluidized(), r.getFuelPriceCombo(), r.getFuelPriceWaste(), r.getFuelPriceExternal(), r.getVentLossUnitPrice(), null);
            return new Upsert(deleted, true);
        }
        SteamComboMonthlyPrice created = repository.save(SteamComboMonthlyPrice.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .fuelPriceLngBoiler(r.getFuelPriceLngBoiler())
                .fuelPriceFluidized(r.getFuelPriceFluidized())
                .fuelPriceCombo(r.getFuelPriceCombo())
                .fuelPriceWaste(r.getFuelPriceWaste())
                .fuelPriceExternal(r.getFuelPriceExternal())
                .ventLossUnitPrice(r.getVentLossUnitPrice())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamComboMonthlyPrice> scope(int year, Integer month) {
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

    private SteamComboMonthlyPrice findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("복합보일러 월 연료단가", id));
    }
}
