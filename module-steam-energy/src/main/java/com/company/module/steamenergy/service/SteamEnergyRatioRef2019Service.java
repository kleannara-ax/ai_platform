package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioRef2019BulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyRatioRef2019Response;
import com.company.module.steamenergy.dto.SteamEnergyRatioRef2019SaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyRatioRef2019;
import com.company.module.steamenergy.repository.SteamEnergyRatioRef2019Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 연료비 배분비율(2019 참고) 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyRatioRef2019Service {

    private final SteamEnergyRatioRef2019Repository repository;

    public List<SteamEnergyRatioRef2019Response> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyRatioRef2019Response::from).toList();
    }

    public SteamEnergyRatioRef2019Response get(Long id) {
        return SteamEnergyRatioRef2019Response.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyRatioRef2019Response save(SteamEnergyRatioRef2019SaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 연료비 배분비율(2019 참고) {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyRatioRef2019Response.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyRatioRef2019BulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyRatioRef2019SaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyRatioRef2019 existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
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
        log.info("[STEAM-ENERGY] 연료비 배분비율(2019 참고) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyRatioRef2019 entity, boolean updated) {}

    private Upsert upsert(SteamEnergyRatioRef2019SaveRequest r) {
        SteamEnergyRatioRef2019 active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getT18Pm1(), r.getT18Pm2(), r.getT18Pm3(), r.getT18PaperSubtotal(), r.getT18Tm1(), r.getT18Tm3(), r.getT18Tm4(), r.getT18Tm5(), r.getT18TissueSubtotal(), r.getT18Total(), r.getT19Pm1(), r.getT19Pm2(), r.getT19Pm3(), r.getT19PaperSubtotal(), r.getT19Tm1(), r.getT19Tm3(), r.getT19Tm4(), r.getT19Tm5(), r.getT19TissueSubtotal(), r.getT19Total(), r.getT20Pm1(), r.getT20Pm2(), r.getT20Pm3(), r.getT20PaperSubtotal(), r.getT20Tm1(), r.getT20Tm3(), r.getT20Tm4(), r.getT20Tm5(), r.getT20TissueSubtotal(), r.getT20Total(), r.getT20TotalEnergyCost(), null);
            return new Upsert(active, true);
        }
        SteamEnergyRatioRef2019 deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getT18Pm1(), r.getT18Pm2(), r.getT18Pm3(), r.getT18PaperSubtotal(), r.getT18Tm1(), r.getT18Tm3(), r.getT18Tm4(), r.getT18Tm5(), r.getT18TissueSubtotal(), r.getT18Total(), r.getT19Pm1(), r.getT19Pm2(), r.getT19Pm3(), r.getT19PaperSubtotal(), r.getT19Tm1(), r.getT19Tm3(), r.getT19Tm4(), r.getT19Tm5(), r.getT19TissueSubtotal(), r.getT19Total(), r.getT20Pm1(), r.getT20Pm2(), r.getT20Pm3(), r.getT20PaperSubtotal(), r.getT20Tm1(), r.getT20Tm3(), r.getT20Tm4(), r.getT20Tm5(), r.getT20TissueSubtotal(), r.getT20Total(), r.getT20TotalEnergyCost(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyRatioRef2019 created = repository.save(SteamEnergyRatioRef2019.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .t18Pm1(r.getT18Pm1())
                .t18Pm2(r.getT18Pm2())
                .t18Pm3(r.getT18Pm3())
                .t18PaperSubtotal(r.getT18PaperSubtotal())
                .t18Tm1(r.getT18Tm1())
                .t18Tm3(r.getT18Tm3())
                .t18Tm4(r.getT18Tm4())
                .t18Tm5(r.getT18Tm5())
                .t18TissueSubtotal(r.getT18TissueSubtotal())
                .t18Total(r.getT18Total())
                .t19Pm1(r.getT19Pm1())
                .t19Pm2(r.getT19Pm2())
                .t19Pm3(r.getT19Pm3())
                .t19PaperSubtotal(r.getT19PaperSubtotal())
                .t19Tm1(r.getT19Tm1())
                .t19Tm3(r.getT19Tm3())
                .t19Tm4(r.getT19Tm4())
                .t19Tm5(r.getT19Tm5())
                .t19TissueSubtotal(r.getT19TissueSubtotal())
                .t19Total(r.getT19Total())
                .t20Pm1(r.getT20Pm1())
                .t20Pm2(r.getT20Pm2())
                .t20Pm3(r.getT20Pm3())
                .t20PaperSubtotal(r.getT20PaperSubtotal())
                .t20Tm1(r.getT20Tm1())
                .t20Tm3(r.getT20Tm3())
                .t20Tm4(r.getT20Tm4())
                .t20Tm5(r.getT20Tm5())
                .t20TissueSubtotal(r.getT20TissueSubtotal())
                .t20Total(r.getT20Total())
                .t20TotalEnergyCost(r.getT20TotalEnergyCost())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyRatioRef2019 findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("연료비 배분비율(2019 참고)", id));
    }
}
