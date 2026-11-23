package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioMachineBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyRatioMachineResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioMachineSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyRatioMachine;
import com.company.module.steamenergy.repository.SteamEnergyRatioMachineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 연료비 배분비율(호기별) 비즈니스 로직 — (연, 월) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyRatioMachineService {

    private final SteamEnergyRatioMachineRepository repository;

    public List<SteamEnergyRatioMachineResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByMonthNoAsc(year).stream().map(SteamEnergyRatioMachineResponse::from).toList();
    }

    public SteamEnergyRatioMachineResponse get(Long id) {
        return SteamEnergyRatioMachineResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyRatioMachineResponse save(SteamEnergyRatioMachineSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 연료비 배분비율(호기별) {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo() + "/" + request.getMonthNo());
        return SteamEnergyRatioMachineResponse.from(result.entity(), result.updated());
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기). clearMissing 이면 목록에 없는 달의 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveYear(SteamEnergyRatioMachineBulkRequest request) {
        requireYear(request.getYear());
        int inserted = 0, updated = 0, cleared = 0;
        Set<Integer> months = new HashSet<>();
        for (SteamEnergyRatioMachineSaveRequest row : request.getRows()) {
            if (!request.getYear().equals(row.getYearNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "연도 " + row.getYearNo() + " 행은 요청 연도(" + request.getYear() + ")와 다릅니다.");
            }
            months.add(row.getMonthNo());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamEnergyRatioMachine existing : repository.findByYearNoOrderByMonthNoAsc(request.getYear())) {
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
        log.info("[STEAM-ENERGY] 연료비 배분비율(호기별) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyRatioMachine entity, boolean updated) {}

    private Upsert upsert(SteamEnergyRatioMachineSaveRequest r) {
        SteamEnergyRatioMachine active = repository.findByYearNoAndMonthNo(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (active != null) {
            active.update(r.getFuelPm2(), r.getFuelPm3(), r.getFuelPaperSubtotal(), r.getFuelTm3(), r.getFuelTm4(), r.getFuelTm5(), r.getFuelTissueSubtotal(), r.getFuelSteamProdTon(), r.getUsagePm2(), r.getUsagePm3(), r.getUsageTm3(), r.getUsageTm4(), r.getUsageTm5(), r.getUsageTotalProdSteam(), r.getUsageEtc(), r.getProdPm2Kg(), r.getProdPm3Kg(), r.getProdPaperSubtotalKg(), r.getProdTm3Kg(), r.getProdTm4Kg(), r.getProdTm5Kg(), r.getProdTissueSubtotalKg(), null);
            return new Upsert(active, true);
        }
        SteamEnergyRatioMachine deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getFuelPm2(), r.getFuelPm3(), r.getFuelPaperSubtotal(), r.getFuelTm3(), r.getFuelTm4(), r.getFuelTm5(), r.getFuelTissueSubtotal(), r.getFuelSteamProdTon(), r.getUsagePm2(), r.getUsagePm3(), r.getUsageTm3(), r.getUsageTm4(), r.getUsageTm5(), r.getUsageTotalProdSteam(), r.getUsageEtc(), r.getProdPm2Kg(), r.getProdPm3Kg(), r.getProdPaperSubtotalKg(), r.getProdTm3Kg(), r.getProdTm4Kg(), r.getProdTm5Kg(), r.getProdTissueSubtotalKg(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyRatioMachine created = repository.save(SteamEnergyRatioMachine.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .fuelPm2(r.getFuelPm2())
                .fuelPm3(r.getFuelPm3())
                .fuelPaperSubtotal(r.getFuelPaperSubtotal())
                .fuelTm3(r.getFuelTm3())
                .fuelTm4(r.getFuelTm4())
                .fuelTm5(r.getFuelTm5())
                .fuelTissueSubtotal(r.getFuelTissueSubtotal())
                .fuelSteamProdTon(r.getFuelSteamProdTon())
                .usagePm2(r.getUsagePm2())
                .usagePm3(r.getUsagePm3())
                .usageTm3(r.getUsageTm3())
                .usageTm4(r.getUsageTm4())
                .usageTm5(r.getUsageTm5())
                .usageTotalProdSteam(r.getUsageTotalProdSteam())
                .usageEtc(r.getUsageEtc())
                .prodPm2Kg(r.getProdPm2Kg())
                .prodPm3Kg(r.getProdPm3Kg())
                .prodPaperSubtotalKg(r.getProdPaperSubtotalKg())
                .prodTm3Kg(r.getProdTm3Kg())
                .prodTm4Kg(r.getProdTm4Kg())
                .prodTm5Kg(r.getProdTm5Kg())
                .prodTissueSubtotalKg(r.getProdTissueSubtotalKg())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyRatioMachine findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("연료비 배분비율(호기별)", id));
    }
}
