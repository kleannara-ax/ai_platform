package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBoilerBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyRatioBoilerResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBoilerSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyRatioBoiler;
import com.company.module.steamenergy.repository.SteamEnergyRatioBoilerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 연료비 배분비율(보일러별) 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyRatioBoilerService {

    private final SteamEnergyRatioBoilerRepository repository;

    public List<SteamEnergyRatioBoilerResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyRatioBoilerResponse::from).toList();
    }

    public SteamEnergyRatioBoilerResponse get(Long id) {
        return SteamEnergyRatioBoilerResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyRatioBoilerResponse save(SteamEnergyRatioBoilerSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 연료비 배분비율(보일러별) {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyRatioBoilerResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyRatioBoilerBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyRatioBoilerSaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyRatioBoiler existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
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
        log.info("[STEAM-ENERGY] 연료비 배분비율(보일러별) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyRatioBoiler entity, boolean updated) {}

    private Upsert upsert(SteamEnergyRatioBoilerSaveRequest r) {
        SteamEnergyRatioBoiler active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getSteamLngBoiler(), r.getSteamComposite(), r.getSteamIncinerator(), r.getSteamFluid(), r.getSteamExtBoiler(), r.getFluidVentTon(), r.getCompositePenaltyTon(), r.getCompositeGuaranteeTon(), r.getCostLngBoiler(), r.getCostComposite(), r.getCostIncinerator(), r.getCostFluid(), r.getCostExtBoiler(), r.getFluidInternalVentCost(), r.getPriceLngBoiler(), r.getPriceComposite(), r.getPriceIncinerator(), r.getPriceFluid(), r.getPriceExtBoiler(), null);
            return new Upsert(active, true);
        }
        SteamEnergyRatioBoiler deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getSteamLngBoiler(), r.getSteamComposite(), r.getSteamIncinerator(), r.getSteamFluid(), r.getSteamExtBoiler(), r.getFluidVentTon(), r.getCompositePenaltyTon(), r.getCompositeGuaranteeTon(), r.getCostLngBoiler(), r.getCostComposite(), r.getCostIncinerator(), r.getCostFluid(), r.getCostExtBoiler(), r.getFluidInternalVentCost(), r.getPriceLngBoiler(), r.getPriceComposite(), r.getPriceIncinerator(), r.getPriceFluid(), r.getPriceExtBoiler(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyRatioBoiler created = repository.save(SteamEnergyRatioBoiler.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .steamLngBoiler(r.getSteamLngBoiler())
                .steamComposite(r.getSteamComposite())
                .steamIncinerator(r.getSteamIncinerator())
                .steamFluid(r.getSteamFluid())
                .steamExtBoiler(r.getSteamExtBoiler())
                .fluidVentTon(r.getFluidVentTon())
                .compositePenaltyTon(r.getCompositePenaltyTon())
                .compositeGuaranteeTon(r.getCompositeGuaranteeTon())
                .costLngBoiler(r.getCostLngBoiler())
                .costComposite(r.getCostComposite())
                .costIncinerator(r.getCostIncinerator())
                .costFluid(r.getCostFluid())
                .costExtBoiler(r.getCostExtBoiler())
                .fluidInternalVentCost(r.getFluidInternalVentCost())
                .priceLngBoiler(r.getPriceLngBoiler())
                .priceComposite(r.getPriceComposite())
                .priceIncinerator(r.getPriceIncinerator())
                .priceFluid(r.getPriceFluid())
                .priceExtBoiler(r.getPriceExtBoiler())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyRatioBoiler findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("연료비 배분비율(보일러별)", id));
    }
}
