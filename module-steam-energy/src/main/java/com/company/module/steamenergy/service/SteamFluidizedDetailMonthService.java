package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthSaveRequest;
import com.company.module.steamenergy.entity.SteamFluidizedDetailMonth;
import com.company.module.steamenergy.repository.SteamFluidizedDetailMonthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유동상 세부 운영내역 (월별 단가·비용) 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFluidizedDetailMonthService {

    private final SteamFluidizedDetailMonthRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamFluidizedDetailMonthResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamFluidizedDetailMonthResponse::from).toList();
    }

    public SteamFluidizedDetailMonthResponse get(Long id) {
        return SteamFluidizedDetailMonthResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFluidizedDetailMonthResponse save(SteamFluidizedDetailMonthSaveRequest request) {
        Upsert result = upsert(request);
        return SteamFluidizedDetailMonthResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamFluidizedDetailMonthBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamFluidizedDetailMonthSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFluidizedDetailMonth e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 유동상 세부 운영내역 (월별 단가·비용) 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 유동상 세부 운영내역 (월별 단가·비용) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamFluidizedDetailMonth entity, boolean updated) {}

    private Upsert upsert(SteamFluidizedDetailMonthSaveRequest r) {
        SteamFluidizedDetailMonth active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getImprovementCost(), r.getExtraCost(), r.getSteamProducedUnitPrice(), r.getAmmoniumCarbonateUnitPrice(), r.getBoilerChemInUnitPrice(), r.getBoilerChemUseUnitPrice(), r.getActivatedCarbonInUnitPrice(), r.getActivatedCarbonUseUnitPrice(), r.getCausticSodaInUnitPrice(), r.getCausticSodaUseUnitPrice(), r.getSlakedLimeUnitPrice(), r.getSaltUnitPrice(), r.getDieselUnitPrice(), r.getSilicaSandUseUnitPrice(), r.getSrfUnitPrice(), r.getFlyAshGeneratedUnitPrice(), r.getFlyAshOut01UnitPrice(), r.getFlyAshOut02UnitPrice(), r.getFlyAshOut03UnitPrice(), r.getFlyAshOut04UnitPrice(), r.getFlyAshOut05UnitPrice(), r.getFlyAshOut06UnitPrice(), r.getFlyAshOut07UnitPrice(), r.getFlyAshOut08UnitPrice(), r.getBottomAshGeneratedUnitPrice(), r.getBottomAshOut01UnitPrice(), r.getBottomAshOut02UnitPrice(), r.getBottomAshOut03UnitPrice(), r.getBottomAshOut04UnitPrice(), r.getPowerUnitPrice(), r.getPowerUreaUnitPrice(), r.getPowerFdFanUnitPrice(), r.getReuseWaterUnitPrice(), r.getCondensateUnitPrice(), r.getBoilerFeedWaterUnitPrice(), r.getWasteWaterUnitPrice(), r.getSoftWaterUnitPrice(), r.getSludgeOutsource01UnitPrice(), r.getSludgeOutsource02UnitPrice(), r.getSludgeOutsource03UnitPrice(), r.getSludgeOutsource04UnitPrice(), r.getSludgeOutsource05UnitPrice(), r.getSludgeOutsource06UnitPrice(), r.getSludgeOutsource07UnitPrice(), r.getSludgeOutsource08UnitPrice(), r.getSludgeOutsource09UnitPrice(), r.getSludgeOutsource10UnitPrice(), r.getSludgeOutsource11UnitPrice(), r.getInspectionFeeUnitPrice(), r.getSilicaSandUseNote(), null);
            return new Upsert(active, true);
        }
        SteamFluidizedDetailMonth deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getImprovementCost(), r.getExtraCost(), r.getSteamProducedUnitPrice(), r.getAmmoniumCarbonateUnitPrice(), r.getBoilerChemInUnitPrice(), r.getBoilerChemUseUnitPrice(), r.getActivatedCarbonInUnitPrice(), r.getActivatedCarbonUseUnitPrice(), r.getCausticSodaInUnitPrice(), r.getCausticSodaUseUnitPrice(), r.getSlakedLimeUnitPrice(), r.getSaltUnitPrice(), r.getDieselUnitPrice(), r.getSilicaSandUseUnitPrice(), r.getSrfUnitPrice(), r.getFlyAshGeneratedUnitPrice(), r.getFlyAshOut01UnitPrice(), r.getFlyAshOut02UnitPrice(), r.getFlyAshOut03UnitPrice(), r.getFlyAshOut04UnitPrice(), r.getFlyAshOut05UnitPrice(), r.getFlyAshOut06UnitPrice(), r.getFlyAshOut07UnitPrice(), r.getFlyAshOut08UnitPrice(), r.getBottomAshGeneratedUnitPrice(), r.getBottomAshOut01UnitPrice(), r.getBottomAshOut02UnitPrice(), r.getBottomAshOut03UnitPrice(), r.getBottomAshOut04UnitPrice(), r.getPowerUnitPrice(), r.getPowerUreaUnitPrice(), r.getPowerFdFanUnitPrice(), r.getReuseWaterUnitPrice(), r.getCondensateUnitPrice(), r.getBoilerFeedWaterUnitPrice(), r.getWasteWaterUnitPrice(), r.getSoftWaterUnitPrice(), r.getSludgeOutsource01UnitPrice(), r.getSludgeOutsource02UnitPrice(), r.getSludgeOutsource03UnitPrice(), r.getSludgeOutsource04UnitPrice(), r.getSludgeOutsource05UnitPrice(), r.getSludgeOutsource06UnitPrice(), r.getSludgeOutsource07UnitPrice(), r.getSludgeOutsource08UnitPrice(), r.getSludgeOutsource09UnitPrice(), r.getSludgeOutsource10UnitPrice(), r.getSludgeOutsource11UnitPrice(), r.getInspectionFeeUnitPrice(), r.getSilicaSandUseNote(), null);
            return new Upsert(deleted, true);
        }
        SteamFluidizedDetailMonth created = repository.save(SteamFluidizedDetailMonth.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .improvementCost(r.getImprovementCost())
                .extraCost(r.getExtraCost())
                .steamProducedUnitPrice(r.getSteamProducedUnitPrice())
                .ammoniumCarbonateUnitPrice(r.getAmmoniumCarbonateUnitPrice())
                .boilerChemInUnitPrice(r.getBoilerChemInUnitPrice())
                .boilerChemUseUnitPrice(r.getBoilerChemUseUnitPrice())
                .activatedCarbonInUnitPrice(r.getActivatedCarbonInUnitPrice())
                .activatedCarbonUseUnitPrice(r.getActivatedCarbonUseUnitPrice())
                .causticSodaInUnitPrice(r.getCausticSodaInUnitPrice())
                .causticSodaUseUnitPrice(r.getCausticSodaUseUnitPrice())
                .slakedLimeUnitPrice(r.getSlakedLimeUnitPrice())
                .saltUnitPrice(r.getSaltUnitPrice())
                .dieselUnitPrice(r.getDieselUnitPrice())
                .silicaSandUseUnitPrice(r.getSilicaSandUseUnitPrice())
                .srfUnitPrice(r.getSrfUnitPrice())
                .flyAshGeneratedUnitPrice(r.getFlyAshGeneratedUnitPrice())
                .flyAshOut01UnitPrice(r.getFlyAshOut01UnitPrice())
                .flyAshOut02UnitPrice(r.getFlyAshOut02UnitPrice())
                .flyAshOut03UnitPrice(r.getFlyAshOut03UnitPrice())
                .flyAshOut04UnitPrice(r.getFlyAshOut04UnitPrice())
                .flyAshOut05UnitPrice(r.getFlyAshOut05UnitPrice())
                .flyAshOut06UnitPrice(r.getFlyAshOut06UnitPrice())
                .flyAshOut07UnitPrice(r.getFlyAshOut07UnitPrice())
                .flyAshOut08UnitPrice(r.getFlyAshOut08UnitPrice())
                .bottomAshGeneratedUnitPrice(r.getBottomAshGeneratedUnitPrice())
                .bottomAshOut01UnitPrice(r.getBottomAshOut01UnitPrice())
                .bottomAshOut02UnitPrice(r.getBottomAshOut02UnitPrice())
                .bottomAshOut03UnitPrice(r.getBottomAshOut03UnitPrice())
                .bottomAshOut04UnitPrice(r.getBottomAshOut04UnitPrice())
                .powerUnitPrice(r.getPowerUnitPrice())
                .powerUreaUnitPrice(r.getPowerUreaUnitPrice())
                .powerFdFanUnitPrice(r.getPowerFdFanUnitPrice())
                .reuseWaterUnitPrice(r.getReuseWaterUnitPrice())
                .condensateUnitPrice(r.getCondensateUnitPrice())
                .boilerFeedWaterUnitPrice(r.getBoilerFeedWaterUnitPrice())
                .wasteWaterUnitPrice(r.getWasteWaterUnitPrice())
                .softWaterUnitPrice(r.getSoftWaterUnitPrice())
                .sludgeOutsource01UnitPrice(r.getSludgeOutsource01UnitPrice())
                .sludgeOutsource02UnitPrice(r.getSludgeOutsource02UnitPrice())
                .sludgeOutsource03UnitPrice(r.getSludgeOutsource03UnitPrice())
                .sludgeOutsource04UnitPrice(r.getSludgeOutsource04UnitPrice())
                .sludgeOutsource05UnitPrice(r.getSludgeOutsource05UnitPrice())
                .sludgeOutsource06UnitPrice(r.getSludgeOutsource06UnitPrice())
                .sludgeOutsource07UnitPrice(r.getSludgeOutsource07UnitPrice())
                .sludgeOutsource08UnitPrice(r.getSludgeOutsource08UnitPrice())
                .sludgeOutsource09UnitPrice(r.getSludgeOutsource09UnitPrice())
                .sludgeOutsource10UnitPrice(r.getSludgeOutsource10UnitPrice())
                .sludgeOutsource11UnitPrice(r.getSludgeOutsource11UnitPrice())
                .inspectionFeeUnitPrice(r.getInspectionFeeUnitPrice())
                .silicaSandUseNote(r.getSilicaSandUseNote())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamFluidizedDetailMonth> scope(int year, Integer month) {
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

    private SteamFluidizedDetailMonth findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("유동상 세부 운영내역 (월별 단가·비용)", id));
    }
}
