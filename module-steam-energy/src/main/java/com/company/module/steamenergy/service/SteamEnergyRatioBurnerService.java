package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyRatioBurner;
import com.company.module.steamenergy.repository.SteamEnergyRatioBurnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 연료비 배분비율(버너 LNG) 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyRatioBurnerService {

    private final SteamEnergyRatioBurnerRepository repository;

    public List<SteamEnergyRatioBurnerResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyRatioBurnerResponse::from).toList();
    }

    public SteamEnergyRatioBurnerResponse get(Long id) {
        return SteamEnergyRatioBurnerResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyRatioBurnerResponse save(SteamEnergyRatioBurnerSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 연료비 배분비율(버너 LNG) {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyRatioBurnerResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyRatioBurnerBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyRatioBurnerSaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyRatioBurner existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
                if (months.contains(existing.getMonthNo())) continue;
                existing.clearValues(null);
                cleared += 1;
            }
        }
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 연료비 배분비율(버너 LNG) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyRatioBurner entity, boolean updated) {}

    private Upsert upsert(SteamEnergyRatioBurnerSaveRequest r) {
        SteamEnergyRatioBurner active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getLngPm3(), r.getLngTm3(), r.getLngTm4(), r.getLngTm5(), r.getLngPricePrevYear(), r.getLngPrice(), r.getLngPriceChangeRate(), r.getLngCostTotal(), r.getLngUnitPm3(), r.getLngUnitTm3(), r.getLngUnitTm4(), r.getLngUnitTm5(), r.getLngUnitTissueSubtotal(), null);
            return new Upsert(active, true);
        }
        SteamEnergyRatioBurner deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getLngPm3(), r.getLngTm3(), r.getLngTm4(), r.getLngTm5(), r.getLngPricePrevYear(), r.getLngPrice(), r.getLngPriceChangeRate(), r.getLngCostTotal(), r.getLngUnitPm3(), r.getLngUnitTm3(), r.getLngUnitTm4(), r.getLngUnitTm5(), r.getLngUnitTissueSubtotal(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyRatioBurner created = repository.save(SteamEnergyRatioBurner.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .lngPm3(r.getLngPm3())
                .lngTm3(r.getLngTm3())
                .lngTm4(r.getLngTm4())
                .lngTm5(r.getLngTm5())
                .lngPricePrevYear(r.getLngPricePrevYear())
                .lngPrice(r.getLngPrice())
                .lngPriceChangeRate(r.getLngPriceChangeRate())
                .lngCostTotal(r.getLngCostTotal())
                .lngUnitPm3(r.getLngUnitPm3())
                .lngUnitTm3(r.getLngUnitTm3())
                .lngUnitTm4(r.getLngUnitTm4())
                .lngUnitTm5(r.getLngUnitTm5())
                .lngUnitTissueSubtotal(r.getLngUnitTissueSubtotal())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyRatioBurner findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("연료비 배분비율(버너 LNG)", id));
    }
}
