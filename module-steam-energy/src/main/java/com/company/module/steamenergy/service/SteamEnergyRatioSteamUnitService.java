package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioSteamUnitBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyRatioSteamUnitResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioSteamUnitSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyRatioSteamUnit;
import com.company.module.steamenergy.repository.SteamEnergyRatioSteamUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 스팀사용량 원단위(기타 포함) 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyRatioSteamUnitService {

    private final SteamEnergyRatioSteamUnitRepository repository;

    public List<SteamEnergyRatioSteamUnitResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyRatioSteamUnitResponse::from).toList();
    }

    public SteamEnergyRatioSteamUnitResponse get(Long id) {
        return SteamEnergyRatioSteamUnitResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyRatioSteamUnitResponse save(SteamEnergyRatioSteamUnitSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 스팀사용량 원단위(기타 포함) {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyRatioSteamUnitResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyRatioSteamUnitBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyRatioSteamUnitSaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyRatioSteamUnit existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
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
        log.info("[STEAM-ENERGY] 스팀사용량 원단위(기타 포함) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyRatioSteamUnit entity, boolean updated) {}

    private Upsert upsert(SteamEnergyRatioSteamUnitSaveRequest r) {
        SteamEnergyRatioSteamUnit active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getUnitPm2(), r.getUnitPm3(), r.getUnitPaperSubtotal(), r.getUnitTm3(), r.getUnitTm4(), r.getUnitTm5(), r.getUnitTissueSubtotal(), null);
            return new Upsert(active, true);
        }
        SteamEnergyRatioSteamUnit deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getUnitPm2(), r.getUnitPm3(), r.getUnitPaperSubtotal(), r.getUnitTm3(), r.getUnitTm4(), r.getUnitTm5(), r.getUnitTissueSubtotal(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyRatioSteamUnit created = repository.save(SteamEnergyRatioSteamUnit.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .unitPm2(r.getUnitPm2())
                .unitPm3(r.getUnitPm3())
                .unitPaperSubtotal(r.getUnitPaperSubtotal())
                .unitTm3(r.getUnitTm3())
                .unitTm4(r.getUnitTm4())
                .unitTm5(r.getUnitTm5())
                .unitTissueSubtotal(r.getUnitTissueSubtotal())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyRatioSteamUnit findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("스팀사용량 원단위(기타 포함)", id));
    }
}
