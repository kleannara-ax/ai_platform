package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamWasteIncinDailyLogBulkRequest;
import com.company.module.steamenergy.dto.SteamWasteIncinDailyLogResponse;
import com.company.module.steamenergy.dto.SteamWasteIncinDailyLogSaveRequest;
import com.company.module.steamenergy.entity.SteamWasteIncinDailyLog;
import com.company.module.steamenergy.repository.SteamWasteIncinDailyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 폐합성소각로 운전일지 비즈니스 로직 — (USAGE_DATE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamWasteIncinDailyLogService {

    private final SteamWasteIncinDailyLogRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamWasteIncinDailyLogResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamWasteIncinDailyLogResponse::from).toList();
    }

    public SteamWasteIncinDailyLogResponse get(Long id) {
        return SteamWasteIncinDailyLogResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamWasteIncinDailyLogResponse save(SteamWasteIncinDailyLogSaveRequest request) {
        Upsert result = upsert(request);
        return SteamWasteIncinDailyLogResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamWasteIncinDailyLogBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamWasteIncinDailyLogSaveRequest r : request.getRows()) {
            if (!(r.getUsageDate().getYear() == year && (month == null || r.getUsageDate().getMonthValue() == month))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getUsageDate()));
            }
            keys.add("" + r.getUsageDate());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamWasteIncinDailyLog e : scope(year, month)) {
                if (keys.contains("" + e.getUsageDate())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 폐합성소각로 운전일지 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 폐합성소각로 운전일지 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamWasteIncinDailyLog entity, boolean updated) {}

    private Upsert upsert(SteamWasteIncinDailyLogSaveRequest r) {
        SteamWasteIncinDailyLog active = repository.findActive(r.getUsageDate()).orElse(null);
        if (active != null) {
            active.update(r.getBurnU1Self(), r.getBurnU1Recycle(), r.getBurnU1Generated(), r.getBurnU1RunHours(), r.getBurnU1ExtStock(), r.getBurnU2Self(), r.getBurnU2Recycle(), r.getBurnU2Generated(), r.getBurnU2RunHours(), r.getBurnU2SelfStock(), r.getBurnExtTotal(), r.getSteamU1(), r.getSteamU2First(), r.getSteamU2Second(), r.getSteamAgency(), r.getSteamPressure(), r.getWaterU1First(), r.getWaterU1Second(), r.getWaterU2First(), r.getWaterU2Second(), r.getPowerMeter(), r.getPowerU1(), r.getPowerU2(), r.getPowerEp(), r.getFlyAshU1(), r.getFlyAshU2(), r.getBottomAshU1(), r.getBottomAshU2(), r.getUreaIn(), r.getUreaUse(), r.getUreaStock(), r.getBoilerChemIn(), r.getBoilerChemUse(), r.getBoilerChemStock(), r.getSaltIn(), r.getSaltUse(), r.getSaltFluidized(), r.getSaltStock(), r.getCausticIn(), r.getCausticUse(), r.getCausticStock(), r.getCarbonIn(), r.getCarbonUse(), r.getCarbonStock(), r.getOilIn(), r.getOilU1(), r.getOilU2(), r.getOilSludge1(), r.getOilSludge2(), r.getOilStock(), r.getClinkerSideWall(), r.getClinkerDrop(), r.getClinkerIn(), r.getClinkerUse(), r.getClinkerStock(), r.getTmsU1Dust(), r.getTmsU1Co(), r.getTmsU1Nox(), r.getTmsU1Sox(), r.getTmsU1Hcl(), r.getTmsU1Temp(), r.getTmsU2Dust(), r.getTmsU2Co(), r.getTmsU2Nox(), r.getTmsU2Sox(), r.getTmsU2Hcl(), r.getTmsU2Temp(), r.getInboundVendor01(), r.getInboundVendor02(), r.getInboundVendor03(), r.getInboundVendor04(), r.getInboundVendor05(), r.getInboundVendor06(), r.getInboundVendor07(), r.getInboundVendor08(), r.getInboundVendor09(), r.getInboundVendor10(), r.getInboundVendor11(), r.getInboundVendor12(), r.getInboundVendor13(), r.getInboundVendor14(), r.getGenWood(), r.getGenSaengbul(), r.getGenPlastic(), r.getGenReject(), r.getGenRagger(), r.getGenStock(), null);
            return new Upsert(active, true);
        }
        SteamWasteIncinDailyLog deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getBurnU1Self(), r.getBurnU1Recycle(), r.getBurnU1Generated(), r.getBurnU1RunHours(), r.getBurnU1ExtStock(), r.getBurnU2Self(), r.getBurnU2Recycle(), r.getBurnU2Generated(), r.getBurnU2RunHours(), r.getBurnU2SelfStock(), r.getBurnExtTotal(), r.getSteamU1(), r.getSteamU2First(), r.getSteamU2Second(), r.getSteamAgency(), r.getSteamPressure(), r.getWaterU1First(), r.getWaterU1Second(), r.getWaterU2First(), r.getWaterU2Second(), r.getPowerMeter(), r.getPowerU1(), r.getPowerU2(), r.getPowerEp(), r.getFlyAshU1(), r.getFlyAshU2(), r.getBottomAshU1(), r.getBottomAshU2(), r.getUreaIn(), r.getUreaUse(), r.getUreaStock(), r.getBoilerChemIn(), r.getBoilerChemUse(), r.getBoilerChemStock(), r.getSaltIn(), r.getSaltUse(), r.getSaltFluidized(), r.getSaltStock(), r.getCausticIn(), r.getCausticUse(), r.getCausticStock(), r.getCarbonIn(), r.getCarbonUse(), r.getCarbonStock(), r.getOilIn(), r.getOilU1(), r.getOilU2(), r.getOilSludge1(), r.getOilSludge2(), r.getOilStock(), r.getClinkerSideWall(), r.getClinkerDrop(), r.getClinkerIn(), r.getClinkerUse(), r.getClinkerStock(), r.getTmsU1Dust(), r.getTmsU1Co(), r.getTmsU1Nox(), r.getTmsU1Sox(), r.getTmsU1Hcl(), r.getTmsU1Temp(), r.getTmsU2Dust(), r.getTmsU2Co(), r.getTmsU2Nox(), r.getTmsU2Sox(), r.getTmsU2Hcl(), r.getTmsU2Temp(), r.getInboundVendor01(), r.getInboundVendor02(), r.getInboundVendor03(), r.getInboundVendor04(), r.getInboundVendor05(), r.getInboundVendor06(), r.getInboundVendor07(), r.getInboundVendor08(), r.getInboundVendor09(), r.getInboundVendor10(), r.getInboundVendor11(), r.getInboundVendor12(), r.getInboundVendor13(), r.getInboundVendor14(), r.getGenWood(), r.getGenSaengbul(), r.getGenPlastic(), r.getGenReject(), r.getGenRagger(), r.getGenStock(), null);
            return new Upsert(deleted, true);
        }
        SteamWasteIncinDailyLog created = repository.save(SteamWasteIncinDailyLog.builder()
                .usageDate(r.getUsageDate())
                .burnU1Self(r.getBurnU1Self())
                .burnU1Recycle(r.getBurnU1Recycle())
                .burnU1Generated(r.getBurnU1Generated())
                .burnU1RunHours(r.getBurnU1RunHours())
                .burnU1ExtStock(r.getBurnU1ExtStock())
                .burnU2Self(r.getBurnU2Self())
                .burnU2Recycle(r.getBurnU2Recycle())
                .burnU2Generated(r.getBurnU2Generated())
                .burnU2RunHours(r.getBurnU2RunHours())
                .burnU2SelfStock(r.getBurnU2SelfStock())
                .burnExtTotal(r.getBurnExtTotal())
                .steamU1(r.getSteamU1())
                .steamU2First(r.getSteamU2First())
                .steamU2Second(r.getSteamU2Second())
                .steamAgency(r.getSteamAgency())
                .steamPressure(r.getSteamPressure())
                .waterU1First(r.getWaterU1First())
                .waterU1Second(r.getWaterU1Second())
                .waterU2First(r.getWaterU2First())
                .waterU2Second(r.getWaterU2Second())
                .powerMeter(r.getPowerMeter())
                .powerU1(r.getPowerU1())
                .powerU2(r.getPowerU2())
                .powerEp(r.getPowerEp())
                .flyAshU1(r.getFlyAshU1())
                .flyAshU2(r.getFlyAshU2())
                .bottomAshU1(r.getBottomAshU1())
                .bottomAshU2(r.getBottomAshU2())
                .ureaIn(r.getUreaIn())
                .ureaUse(r.getUreaUse())
                .ureaStock(r.getUreaStock())
                .boilerChemIn(r.getBoilerChemIn())
                .boilerChemUse(r.getBoilerChemUse())
                .boilerChemStock(r.getBoilerChemStock())
                .saltIn(r.getSaltIn())
                .saltUse(r.getSaltUse())
                .saltFluidized(r.getSaltFluidized())
                .saltStock(r.getSaltStock())
                .causticIn(r.getCausticIn())
                .causticUse(r.getCausticUse())
                .causticStock(r.getCausticStock())
                .carbonIn(r.getCarbonIn())
                .carbonUse(r.getCarbonUse())
                .carbonStock(r.getCarbonStock())
                .oilIn(r.getOilIn())
                .oilU1(r.getOilU1())
                .oilU2(r.getOilU2())
                .oilSludge1(r.getOilSludge1())
                .oilSludge2(r.getOilSludge2())
                .oilStock(r.getOilStock())
                .clinkerSideWall(r.getClinkerSideWall())
                .clinkerDrop(r.getClinkerDrop())
                .clinkerIn(r.getClinkerIn())
                .clinkerUse(r.getClinkerUse())
                .clinkerStock(r.getClinkerStock())
                .tmsU1Dust(r.getTmsU1Dust())
                .tmsU1Co(r.getTmsU1Co())
                .tmsU1Nox(r.getTmsU1Nox())
                .tmsU1Sox(r.getTmsU1Sox())
                .tmsU1Hcl(r.getTmsU1Hcl())
                .tmsU1Temp(r.getTmsU1Temp())
                .tmsU2Dust(r.getTmsU2Dust())
                .tmsU2Co(r.getTmsU2Co())
                .tmsU2Nox(r.getTmsU2Nox())
                .tmsU2Sox(r.getTmsU2Sox())
                .tmsU2Hcl(r.getTmsU2Hcl())
                .tmsU2Temp(r.getTmsU2Temp())
                .inboundVendor01(r.getInboundVendor01())
                .inboundVendor02(r.getInboundVendor02())
                .inboundVendor03(r.getInboundVendor03())
                .inboundVendor04(r.getInboundVendor04())
                .inboundVendor05(r.getInboundVendor05())
                .inboundVendor06(r.getInboundVendor06())
                .inboundVendor07(r.getInboundVendor07())
                .inboundVendor08(r.getInboundVendor08())
                .inboundVendor09(r.getInboundVendor09())
                .inboundVendor10(r.getInboundVendor10())
                .inboundVendor11(r.getInboundVendor11())
                .inboundVendor12(r.getInboundVendor12())
                .inboundVendor13(r.getInboundVendor13())
                .inboundVendor14(r.getInboundVendor14())
                .genWood(r.getGenWood())
                .genSaengbul(r.getGenSaengbul())
                .genPlastic(r.getGenPlastic())
                .genReject(r.getGenReject())
                .genRagger(r.getGenRagger())
                .genStock(r.getGenStock())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamWasteIncinDailyLog> scope(int year, Integer month) {
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

    private SteamWasteIncinDailyLog findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("폐합성소각로 운전일지", id));
    }
}
