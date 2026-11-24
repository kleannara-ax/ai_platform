package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDailyLogBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDailyLogResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDailyLogSaveRequest;
import com.company.module.steamenergy.entity.SteamFluidizedDailyLog;
import com.company.module.steamenergy.repository.SteamFluidizedDailyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유동상 운전일지 비즈니스 로직 — (USAGE_DATE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFluidizedDailyLogService {

    private final SteamFluidizedDailyLogRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamFluidizedDailyLogResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamFluidizedDailyLogResponse::from).toList();
    }

    public SteamFluidizedDailyLogResponse get(Long id) {
        return SteamFluidizedDailyLogResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFluidizedDailyLogResponse save(SteamFluidizedDailyLogSaveRequest request) {
        Upsert result = upsert(request);
        return SteamFluidizedDailyLogResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamFluidizedDailyLogBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamFluidizedDailyLogSaveRequest r : request.getRows()) {
            if (!(r.getUsageDate().getYear() == year && (month == null || r.getUsageDate().getMonthValue() == month))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getUsageDate()));
            }
            keys.add("" + r.getUsageDate());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFluidizedDailyLog e : scope(year, month)) {
                if (keys.contains("" + e.getUsageDate())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 유동상 운전일지 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 유동상 운전일지 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamFluidizedDailyLog entity, boolean updated) {}

    private Upsert upsert(SteamFluidizedDailyLogSaveRequest r) {
        SteamFluidizedDailyLog active = repository.findActive(r.getUsageDate()).orElse(null);
        if (active != null) {
            active.update(r.getWorkerAm(), r.getWorkerPm(), r.getWorkerNight(), r.getApprLeader(), r.getApprAssistant(), r.getApprManager(), r.getChemAmmoniumCarbonatePrev(), r.getChemBoilerAgentPrev(), r.getChemActivatedCarbonPrev(), r.getChemCausticSodaPrev(), r.getChemSlakedLimePrev(), r.getChemSaltPrev(), r.getChemDieselPrev(), r.getChemSrfPrev(), r.getChemSilicaSandPrev(), r.getAshFlyOut(), r.getAshBottomOut(), r.getIncombustibleDay(), r.getIncombustibleNight(), r.getPowerMainPrev(), r.getPowerMainToday(), r.getPowerAshSiloPrev(), r.getPowerAshSiloToday(), r.getPowerBagFilterPrev(), r.getPowerBagFilterToday(), r.getPowerScrubberPrev(), r.getPowerScrubberToday(), r.getPowerCarbonFeedPrev(), r.getPowerCarbonFeedToday(), r.getPowerLimeFeedPrev(), r.getPowerLimeFeedToday(), r.getPowerUreaFeedPrev(), r.getPowerUreaFeedToday(), r.getPowerFdPrev(), r.getPowerFdToday(), r.getPowerCarbonBagPrev(), r.getPowerCarbonBagToday(), r.getPowerLimeBagPrev(), r.getPowerLimeBagToday(), r.getUtilReuseWaterPrev(), r.getUtilRiverWaterPrev(), r.getUtilCondensatePrev(), r.getUtilBoilerFeedPrev(), r.getUtilWasteWaterPrev(), r.getUtilSoftWaterPrev(), r.getMoistAm1Time(), r.getMoistAm1Value(), r.getMoistAm2Time(), r.getMoistAm2Value(), r.getMoistAm3Time(), r.getMoistAm3Value(), r.getMoistAm4Time(), r.getMoistAm4Value(), r.getMoistAm5Time(), r.getMoistAm5Value(), r.getMoistPm1Time(), r.getMoistPm1Value(), r.getMoistPm2Time(), r.getMoistPm2Value(), r.getMoistPm3Time(), r.getMoistPm3Value(), r.getMoistPm4Time(), r.getMoistPm4Value(), r.getMoistPm5Time(), r.getMoistPm5Value(), r.getMoistNight1Time(), r.getMoistNight1Value(), r.getMoistNight2Time(), r.getMoistNight2Value(), r.getMoistNight3Time(), r.getMoistNight3Value(), r.getMoistNight4Time(), r.getMoistNight4Value(), r.getMoistNight5Time(), r.getMoistNight5Value(), null);
            return new Upsert(active, true);
        }
        SteamFluidizedDailyLog deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getWorkerAm(), r.getWorkerPm(), r.getWorkerNight(), r.getApprLeader(), r.getApprAssistant(), r.getApprManager(), r.getChemAmmoniumCarbonatePrev(), r.getChemBoilerAgentPrev(), r.getChemActivatedCarbonPrev(), r.getChemCausticSodaPrev(), r.getChemSlakedLimePrev(), r.getChemSaltPrev(), r.getChemDieselPrev(), r.getChemSrfPrev(), r.getChemSilicaSandPrev(), r.getAshFlyOut(), r.getAshBottomOut(), r.getIncombustibleDay(), r.getIncombustibleNight(), r.getPowerMainPrev(), r.getPowerMainToday(), r.getPowerAshSiloPrev(), r.getPowerAshSiloToday(), r.getPowerBagFilterPrev(), r.getPowerBagFilterToday(), r.getPowerScrubberPrev(), r.getPowerScrubberToday(), r.getPowerCarbonFeedPrev(), r.getPowerCarbonFeedToday(), r.getPowerLimeFeedPrev(), r.getPowerLimeFeedToday(), r.getPowerUreaFeedPrev(), r.getPowerUreaFeedToday(), r.getPowerFdPrev(), r.getPowerFdToday(), r.getPowerCarbonBagPrev(), r.getPowerCarbonBagToday(), r.getPowerLimeBagPrev(), r.getPowerLimeBagToday(), r.getUtilReuseWaterPrev(), r.getUtilRiverWaterPrev(), r.getUtilCondensatePrev(), r.getUtilBoilerFeedPrev(), r.getUtilWasteWaterPrev(), r.getUtilSoftWaterPrev(), r.getMoistAm1Time(), r.getMoistAm1Value(), r.getMoistAm2Time(), r.getMoistAm2Value(), r.getMoistAm3Time(), r.getMoistAm3Value(), r.getMoistAm4Time(), r.getMoistAm4Value(), r.getMoistAm5Time(), r.getMoistAm5Value(), r.getMoistPm1Time(), r.getMoistPm1Value(), r.getMoistPm2Time(), r.getMoistPm2Value(), r.getMoistPm3Time(), r.getMoistPm3Value(), r.getMoistPm4Time(), r.getMoistPm4Value(), r.getMoistPm5Time(), r.getMoistPm5Value(), r.getMoistNight1Time(), r.getMoistNight1Value(), r.getMoistNight2Time(), r.getMoistNight2Value(), r.getMoistNight3Time(), r.getMoistNight3Value(), r.getMoistNight4Time(), r.getMoistNight4Value(), r.getMoistNight5Time(), r.getMoistNight5Value(), null);
            return new Upsert(deleted, true);
        }
        SteamFluidizedDailyLog created = repository.save(SteamFluidizedDailyLog.builder()
                .usageDate(r.getUsageDate())
                .workerAm(r.getWorkerAm())
                .workerPm(r.getWorkerPm())
                .workerNight(r.getWorkerNight())
                .apprLeader(r.getApprLeader())
                .apprAssistant(r.getApprAssistant())
                .apprManager(r.getApprManager())
                .chemAmmoniumCarbonatePrev(r.getChemAmmoniumCarbonatePrev())
                .chemBoilerAgentPrev(r.getChemBoilerAgentPrev())
                .chemActivatedCarbonPrev(r.getChemActivatedCarbonPrev())
                .chemCausticSodaPrev(r.getChemCausticSodaPrev())
                .chemSlakedLimePrev(r.getChemSlakedLimePrev())
                .chemSaltPrev(r.getChemSaltPrev())
                .chemDieselPrev(r.getChemDieselPrev())
                .chemSrfPrev(r.getChemSrfPrev())
                .chemSilicaSandPrev(r.getChemSilicaSandPrev())
                .ashFlyOut(r.getAshFlyOut())
                .ashBottomOut(r.getAshBottomOut())
                .incombustibleDay(r.getIncombustibleDay())
                .incombustibleNight(r.getIncombustibleNight())
                .powerMainPrev(r.getPowerMainPrev())
                .powerMainToday(r.getPowerMainToday())
                .powerAshSiloPrev(r.getPowerAshSiloPrev())
                .powerAshSiloToday(r.getPowerAshSiloToday())
                .powerBagFilterPrev(r.getPowerBagFilterPrev())
                .powerBagFilterToday(r.getPowerBagFilterToday())
                .powerScrubberPrev(r.getPowerScrubberPrev())
                .powerScrubberToday(r.getPowerScrubberToday())
                .powerCarbonFeedPrev(r.getPowerCarbonFeedPrev())
                .powerCarbonFeedToday(r.getPowerCarbonFeedToday())
                .powerLimeFeedPrev(r.getPowerLimeFeedPrev())
                .powerLimeFeedToday(r.getPowerLimeFeedToday())
                .powerUreaFeedPrev(r.getPowerUreaFeedPrev())
                .powerUreaFeedToday(r.getPowerUreaFeedToday())
                .powerFdPrev(r.getPowerFdPrev())
                .powerFdToday(r.getPowerFdToday())
                .powerCarbonBagPrev(r.getPowerCarbonBagPrev())
                .powerCarbonBagToday(r.getPowerCarbonBagToday())
                .powerLimeBagPrev(r.getPowerLimeBagPrev())
                .powerLimeBagToday(r.getPowerLimeBagToday())
                .utilReuseWaterPrev(r.getUtilReuseWaterPrev())
                .utilRiverWaterPrev(r.getUtilRiverWaterPrev())
                .utilCondensatePrev(r.getUtilCondensatePrev())
                .utilBoilerFeedPrev(r.getUtilBoilerFeedPrev())
                .utilWasteWaterPrev(r.getUtilWasteWaterPrev())
                .utilSoftWaterPrev(r.getUtilSoftWaterPrev())
                .moistAm1Time(r.getMoistAm1Time())
                .moistAm1Value(r.getMoistAm1Value())
                .moistAm2Time(r.getMoistAm2Time())
                .moistAm2Value(r.getMoistAm2Value())
                .moistAm3Time(r.getMoistAm3Time())
                .moistAm3Value(r.getMoistAm3Value())
                .moistAm4Time(r.getMoistAm4Time())
                .moistAm4Value(r.getMoistAm4Value())
                .moistAm5Time(r.getMoistAm5Time())
                .moistAm5Value(r.getMoistAm5Value())
                .moistPm1Time(r.getMoistPm1Time())
                .moistPm1Value(r.getMoistPm1Value())
                .moistPm2Time(r.getMoistPm2Time())
                .moistPm2Value(r.getMoistPm2Value())
                .moistPm3Time(r.getMoistPm3Time())
                .moistPm3Value(r.getMoistPm3Value())
                .moistPm4Time(r.getMoistPm4Time())
                .moistPm4Value(r.getMoistPm4Value())
                .moistPm5Time(r.getMoistPm5Time())
                .moistPm5Value(r.getMoistPm5Value())
                .moistNight1Time(r.getMoistNight1Time())
                .moistNight1Value(r.getMoistNight1Value())
                .moistNight2Time(r.getMoistNight2Time())
                .moistNight2Value(r.getMoistNight2Value())
                .moistNight3Time(r.getMoistNight3Time())
                .moistNight3Value(r.getMoistNight3Value())
                .moistNight4Time(r.getMoistNight4Time())
                .moistNight4Value(r.getMoistNight4Value())
                .moistNight5Time(r.getMoistNight5Time())
                .moistNight5Value(r.getMoistNight5Value())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamFluidizedDailyLog> scope(int year, Integer month) {
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

    private SteamFluidizedDailyLog findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("유동상 운전일지", id));
    }
}
