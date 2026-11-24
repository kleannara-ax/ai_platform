package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailyResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailySaveRequest;
import com.company.module.steamenergy.entity.SteamFluidizedDetailDaily;
import com.company.module.steamenergy.repository.SteamFluidizedDetailDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유동상 세부 운영내역 (일별) 비즈니스 로직 — (USAGE_DATE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFluidizedDetailDailyService {

    private final SteamFluidizedDetailDailyRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamFluidizedDetailDailyResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamFluidizedDetailDailyResponse::from).toList();
    }

    public SteamFluidizedDetailDailyResponse get(Long id) {
        return SteamFluidizedDetailDailyResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFluidizedDetailDailyResponse save(SteamFluidizedDetailDailySaveRequest request) {
        Upsert result = upsert(request);
        return SteamFluidizedDetailDailyResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamFluidizedDetailDailyBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamFluidizedDetailDailySaveRequest r : request.getRows()) {
            if (!(r.getUsageDate().getYear() == year && (month == null || r.getUsageDate().getMonthValue() == month))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getUsageDate()));
            }
            keys.add("" + r.getUsageDate());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFluidizedDetailDaily e : scope(year, month)) {
                if (keys.contains("" + e.getUsageDate())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 유동상 세부 운영내역 (일별) 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 유동상 세부 운영내역 (일별) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamFluidizedDetailDaily entity, boolean updated) {}

    private Upsert upsert(SteamFluidizedDetailDailySaveRequest r) {
        SteamFluidizedDetailDaily active = repository.findActive(r.getUsageDate()).orElse(null);
        if (active != null) {
            active.update(r.getRunHours(), r.getSteamProducedTon(), r.getSludgeBurnTon(), r.getSrfBurnTon(), r.getSteamUnitPrice(), r.getStopHourUnitPrice(), r.getAmmoniumCarbonateInKg(), r.getAmmoniumCarbonateUseKg(), r.getBoilerChemInKg(), r.getBoilerChemUseKg(), r.getActivatedCarbonInKg(), r.getActivatedCarbonUseKg(), r.getCausticSodaInKg(), r.getCausticSodaUseKg(), r.getSlakedLimeInKg(), r.getSlakedLimeUseKg(), r.getSaltInKg(), r.getSaltUseKg(), r.getDieselInL(), r.getDieselUseL(), r.getSilicaSandInKg(), r.getSilicaSandUseKg(), r.getActivatedCarbonExtraInKg(), r.getEmergencyDieselInL(), r.getSrfInTon(), r.getFlyAshGeneratedTon(), r.getFlyAshOut01Ton(), r.getFlyAshOut02Ton(), r.getFlyAshOut03Ton(), r.getFlyAshOut04Ton(), r.getFlyAshOut05Ton(), r.getFlyAshOut06Ton(), r.getFlyAshOut07Ton(), r.getFlyAshOut08Ton(), r.getBottomAshGeneratedTon(), r.getBottomAshOut01Ton(), r.getBottomAshOut02Ton(), r.getBottomAshOut03Ton(), r.getBottomAshOut04Ton(), r.getPowerMainKwh(), r.getPowerAshVentFilterKwh(), r.getPowerBagFilterKwh(), r.getPowerScrubberKwh(), r.getPowerCarbonFeedKwh(), r.getPowerLimeFeedKwh(), r.getPowerUreaKwh(), r.getPowerFdFanKwh(), r.getReuseWaterTon(), r.getRiverbedWaterTon(), r.getCondensateTon(), r.getBoilerFeedWaterTon(), r.getWasteWaterTon(), r.getSoftWaterTon(), r.getSludgeOutsource01Ton(), r.getSludgeOutsource02Ton(), r.getSludgeOutsource03Ton(), r.getSludgeOutsource04Ton(), r.getSludgeOutsource05Ton(), r.getSludgeOutsource06Ton(), r.getSludgeOutsource07Ton(), r.getSludgeOutsource08Ton(), r.getSludgeOutsource09Ton(), r.getSludgeOutsource10Ton(), r.getSludgeOutsource11Ton(), r.getInspectionFeeQty(), null);
            return new Upsert(active, true);
        }
        SteamFluidizedDetailDaily deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getRunHours(), r.getSteamProducedTon(), r.getSludgeBurnTon(), r.getSrfBurnTon(), r.getSteamUnitPrice(), r.getStopHourUnitPrice(), r.getAmmoniumCarbonateInKg(), r.getAmmoniumCarbonateUseKg(), r.getBoilerChemInKg(), r.getBoilerChemUseKg(), r.getActivatedCarbonInKg(), r.getActivatedCarbonUseKg(), r.getCausticSodaInKg(), r.getCausticSodaUseKg(), r.getSlakedLimeInKg(), r.getSlakedLimeUseKg(), r.getSaltInKg(), r.getSaltUseKg(), r.getDieselInL(), r.getDieselUseL(), r.getSilicaSandInKg(), r.getSilicaSandUseKg(), r.getActivatedCarbonExtraInKg(), r.getEmergencyDieselInL(), r.getSrfInTon(), r.getFlyAshGeneratedTon(), r.getFlyAshOut01Ton(), r.getFlyAshOut02Ton(), r.getFlyAshOut03Ton(), r.getFlyAshOut04Ton(), r.getFlyAshOut05Ton(), r.getFlyAshOut06Ton(), r.getFlyAshOut07Ton(), r.getFlyAshOut08Ton(), r.getBottomAshGeneratedTon(), r.getBottomAshOut01Ton(), r.getBottomAshOut02Ton(), r.getBottomAshOut03Ton(), r.getBottomAshOut04Ton(), r.getPowerMainKwh(), r.getPowerAshVentFilterKwh(), r.getPowerBagFilterKwh(), r.getPowerScrubberKwh(), r.getPowerCarbonFeedKwh(), r.getPowerLimeFeedKwh(), r.getPowerUreaKwh(), r.getPowerFdFanKwh(), r.getReuseWaterTon(), r.getRiverbedWaterTon(), r.getCondensateTon(), r.getBoilerFeedWaterTon(), r.getWasteWaterTon(), r.getSoftWaterTon(), r.getSludgeOutsource01Ton(), r.getSludgeOutsource02Ton(), r.getSludgeOutsource03Ton(), r.getSludgeOutsource04Ton(), r.getSludgeOutsource05Ton(), r.getSludgeOutsource06Ton(), r.getSludgeOutsource07Ton(), r.getSludgeOutsource08Ton(), r.getSludgeOutsource09Ton(), r.getSludgeOutsource10Ton(), r.getSludgeOutsource11Ton(), r.getInspectionFeeQty(), null);
            return new Upsert(deleted, true);
        }
        SteamFluidizedDetailDaily created = repository.save(SteamFluidizedDetailDaily.builder()
                .usageDate(r.getUsageDate())
                .runHours(r.getRunHours())
                .steamProducedTon(r.getSteamProducedTon())
                .sludgeBurnTon(r.getSludgeBurnTon())
                .srfBurnTon(r.getSrfBurnTon())
                .steamUnitPrice(r.getSteamUnitPrice())
                .stopHourUnitPrice(r.getStopHourUnitPrice())
                .ammoniumCarbonateInKg(r.getAmmoniumCarbonateInKg())
                .ammoniumCarbonateUseKg(r.getAmmoniumCarbonateUseKg())
                .boilerChemInKg(r.getBoilerChemInKg())
                .boilerChemUseKg(r.getBoilerChemUseKg())
                .activatedCarbonInKg(r.getActivatedCarbonInKg())
                .activatedCarbonUseKg(r.getActivatedCarbonUseKg())
                .causticSodaInKg(r.getCausticSodaInKg())
                .causticSodaUseKg(r.getCausticSodaUseKg())
                .slakedLimeInKg(r.getSlakedLimeInKg())
                .slakedLimeUseKg(r.getSlakedLimeUseKg())
                .saltInKg(r.getSaltInKg())
                .saltUseKg(r.getSaltUseKg())
                .dieselInL(r.getDieselInL())
                .dieselUseL(r.getDieselUseL())
                .silicaSandInKg(r.getSilicaSandInKg())
                .silicaSandUseKg(r.getSilicaSandUseKg())
                .activatedCarbonExtraInKg(r.getActivatedCarbonExtraInKg())
                .emergencyDieselInL(r.getEmergencyDieselInL())
                .srfInTon(r.getSrfInTon())
                .flyAshGeneratedTon(r.getFlyAshGeneratedTon())
                .flyAshOut01Ton(r.getFlyAshOut01Ton())
                .flyAshOut02Ton(r.getFlyAshOut02Ton())
                .flyAshOut03Ton(r.getFlyAshOut03Ton())
                .flyAshOut04Ton(r.getFlyAshOut04Ton())
                .flyAshOut05Ton(r.getFlyAshOut05Ton())
                .flyAshOut06Ton(r.getFlyAshOut06Ton())
                .flyAshOut07Ton(r.getFlyAshOut07Ton())
                .flyAshOut08Ton(r.getFlyAshOut08Ton())
                .bottomAshGeneratedTon(r.getBottomAshGeneratedTon())
                .bottomAshOut01Ton(r.getBottomAshOut01Ton())
                .bottomAshOut02Ton(r.getBottomAshOut02Ton())
                .bottomAshOut03Ton(r.getBottomAshOut03Ton())
                .bottomAshOut04Ton(r.getBottomAshOut04Ton())
                .powerMainKwh(r.getPowerMainKwh())
                .powerAshVentFilterKwh(r.getPowerAshVentFilterKwh())
                .powerBagFilterKwh(r.getPowerBagFilterKwh())
                .powerScrubberKwh(r.getPowerScrubberKwh())
                .powerCarbonFeedKwh(r.getPowerCarbonFeedKwh())
                .powerLimeFeedKwh(r.getPowerLimeFeedKwh())
                .powerUreaKwh(r.getPowerUreaKwh())
                .powerFdFanKwh(r.getPowerFdFanKwh())
                .reuseWaterTon(r.getReuseWaterTon())
                .riverbedWaterTon(r.getRiverbedWaterTon())
                .condensateTon(r.getCondensateTon())
                .boilerFeedWaterTon(r.getBoilerFeedWaterTon())
                .wasteWaterTon(r.getWasteWaterTon())
                .softWaterTon(r.getSoftWaterTon())
                .sludgeOutsource01Ton(r.getSludgeOutsource01Ton())
                .sludgeOutsource02Ton(r.getSludgeOutsource02Ton())
                .sludgeOutsource03Ton(r.getSludgeOutsource03Ton())
                .sludgeOutsource04Ton(r.getSludgeOutsource04Ton())
                .sludgeOutsource05Ton(r.getSludgeOutsource05Ton())
                .sludgeOutsource06Ton(r.getSludgeOutsource06Ton())
                .sludgeOutsource07Ton(r.getSludgeOutsource07Ton())
                .sludgeOutsource08Ton(r.getSludgeOutsource08Ton())
                .sludgeOutsource09Ton(r.getSludgeOutsource09Ton())
                .sludgeOutsource10Ton(r.getSludgeOutsource10Ton())
                .sludgeOutsource11Ton(r.getSludgeOutsource11Ton())
                .inspectionFeeQty(r.getInspectionFeeQty())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamFluidizedDetailDaily> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(LocalDate.of(year, month == null ? 1 : month, 1), month == null ? LocalDate.of(year, 12, 31) : LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamFluidizedDetailDaily findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("유동상 세부 운영내역 (일별)", id));
    }
}
