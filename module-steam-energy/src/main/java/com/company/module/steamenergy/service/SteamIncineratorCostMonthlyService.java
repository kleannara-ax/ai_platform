package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorCostMonthlyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorCostMonthlyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorCostMonthlySaveRequest;
import com.company.module.steamenergy.entity.SteamIncineratorCostMonthly;
import com.company.module.steamenergy.repository.SteamIncineratorCostMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 소각로 총 운영비용 비즈니스 로직 — (YEAR_NO, MONTH_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamIncineratorCostMonthlyService {

    private final SteamIncineratorCostMonthlyRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamIncineratorCostMonthlyResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamIncineratorCostMonthlyResponse::from).toList();
    }

    public SteamIncineratorCostMonthlyResponse get(Long id) {
        return SteamIncineratorCostMonthlyResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamIncineratorCostMonthlyResponse save(SteamIncineratorCostMonthlySaveRequest request) {
        Upsert result = upsert(request);
        return SteamIncineratorCostMonthlyResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamIncineratorCostMonthlyBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamIncineratorCostMonthlySaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamIncineratorCostMonthly e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 소각로 총 운영비용 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 소각로 총 운영비용 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamIncineratorCostMonthly entity, boolean updated) {}

    private Upsert upsert(SteamIncineratorCostMonthlySaveRequest r) {
        SteamIncineratorCostMonthly active = repository.findActive(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getOpBurnBurn1(), r.getOpBurnBurn2(), r.getOpBurnRate1(), r.getOpBurnRate2(), r.getOpCostUnit(), r.getOpCostDeduct(), r.getSelfSrfInQty(), r.getSelfSrfInRev(), r.getSelfSrfFloorRecycle(), r.getSelfSrfFloorBury(), r.getSelfSrfFlySolid(), r.getSelfSrfFlyBury(), r.getSelfSrfCostFloor(), r.getSelfSrfCostFly(), r.getSelfSrfCostMisc(), r.getSelfUtilP1(), r.getSelfUtilP2(), r.getSelfUtilPrate(), r.getSelfUtilNaohQty(), r.getSelfUtilNaohUnit(), r.getSelfUtilWater(), r.getSelfUtilWasteCharge(), r.getSelfUtilEtc(), r.getSelfTotalTotal(), r.getSelfTotalBurnRate(), r.getSelfTotalSteamRate(), r.getMatOilU1(), r.getMatOilU2(), r.getMatOilUnit(), r.getMatOilCkQty(), r.getMatOilCkUnit(), r.getMatChemUQty(), r.getMatChemUUnit(), r.getMatChemSQty(), r.getMatChemSUnit(), r.getMatMiscAQty(), r.getMatMiscAUnit(), r.getMatMiscKQty(), r.getMatMiscKUnit(), null);
            return new Upsert(active, true);
        }
        SteamIncineratorCostMonthly deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getOpBurnBurn1(), r.getOpBurnBurn2(), r.getOpBurnRate1(), r.getOpBurnRate2(), r.getOpCostUnit(), r.getOpCostDeduct(), r.getSelfSrfInQty(), r.getSelfSrfInRev(), r.getSelfSrfFloorRecycle(), r.getSelfSrfFloorBury(), r.getSelfSrfFlySolid(), r.getSelfSrfFlyBury(), r.getSelfSrfCostFloor(), r.getSelfSrfCostFly(), r.getSelfSrfCostMisc(), r.getSelfUtilP1(), r.getSelfUtilP2(), r.getSelfUtilPrate(), r.getSelfUtilNaohQty(), r.getSelfUtilNaohUnit(), r.getSelfUtilWater(), r.getSelfUtilWasteCharge(), r.getSelfUtilEtc(), r.getSelfTotalTotal(), r.getSelfTotalBurnRate(), r.getSelfTotalSteamRate(), r.getMatOilU1(), r.getMatOilU2(), r.getMatOilUnit(), r.getMatOilCkQty(), r.getMatOilCkUnit(), r.getMatChemUQty(), r.getMatChemUUnit(), r.getMatChemSQty(), r.getMatChemSUnit(), r.getMatMiscAQty(), r.getMatMiscAUnit(), r.getMatMiscKQty(), r.getMatMiscKUnit(), null);
            return new Upsert(deleted, true);
        }
        SteamIncineratorCostMonthly created = repository.save(SteamIncineratorCostMonthly.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .opBurnBurn1(r.getOpBurnBurn1())
                .opBurnBurn2(r.getOpBurnBurn2())
                .opBurnRate1(r.getOpBurnRate1())
                .opBurnRate2(r.getOpBurnRate2())
                .opCostUnit(r.getOpCostUnit())
                .opCostDeduct(r.getOpCostDeduct())
                .selfSrfInQty(r.getSelfSrfInQty())
                .selfSrfInRev(r.getSelfSrfInRev())
                .selfSrfFloorRecycle(r.getSelfSrfFloorRecycle())
                .selfSrfFloorBury(r.getSelfSrfFloorBury())
                .selfSrfFlySolid(r.getSelfSrfFlySolid())
                .selfSrfFlyBury(r.getSelfSrfFlyBury())
                .selfSrfCostFloor(r.getSelfSrfCostFloor())
                .selfSrfCostFly(r.getSelfSrfCostFly())
                .selfSrfCostMisc(r.getSelfSrfCostMisc())
                .selfUtilP1(r.getSelfUtilP1())
                .selfUtilP2(r.getSelfUtilP2())
                .selfUtilPrate(r.getSelfUtilPrate())
                .selfUtilNaohQty(r.getSelfUtilNaohQty())
                .selfUtilNaohUnit(r.getSelfUtilNaohUnit())
                .selfUtilWater(r.getSelfUtilWater())
                .selfUtilWasteCharge(r.getSelfUtilWasteCharge())
                .selfUtilEtc(r.getSelfUtilEtc())
                .selfTotalTotal(r.getSelfTotalTotal())
                .selfTotalBurnRate(r.getSelfTotalBurnRate())
                .selfTotalSteamRate(r.getSelfTotalSteamRate())
                .matOilU1(r.getMatOilU1())
                .matOilU2(r.getMatOilU2())
                .matOilUnit(r.getMatOilUnit())
                .matOilCkQty(r.getMatOilCkQty())
                .matOilCkUnit(r.getMatOilCkUnit())
                .matChemUQty(r.getMatChemUQty())
                .matChemUUnit(r.getMatChemUUnit())
                .matChemSQty(r.getMatChemSQty())
                .matChemSUnit(r.getMatChemSUnit())
                .matMiscAQty(r.getMatMiscAQty())
                .matMiscAUnit(r.getMatMiscAUnit())
                .matMiscKQty(r.getMatMiscKQty())
                .matMiscKUnit(r.getMatMiscKUnit())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamIncineratorCostMonthly> scope(int year, Integer month) {
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

    private SteamIncineratorCostMonthly findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("소각로 총 운영비용", id));
    }
}
