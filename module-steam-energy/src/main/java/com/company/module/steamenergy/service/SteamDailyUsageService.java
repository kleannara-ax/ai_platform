package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamDailyUsageSaveRequest;
import com.company.module.steamenergy.entity.SteamDailyUsage;
import com.company.module.steamenergy.entity.SteamEquipment;
import com.company.module.steamenergy.repository.SteamDailyUsageRepository;
import com.company.module.steamenergy.repository.SteamEquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 일일 스팀 사용 실적 비즈니스 로직
 *
 * <p>저장은 (일자+설비) 기준 업서트(Upsert). 설비 존재 여부를 검증한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamDailyUsageService {

    private final SteamDailyUsageRepository usageRepository;
    private final SteamEquipmentRepository equipmentRepository;

    public Page<SteamDailyUsageResponse> getList(Long equipmentId, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        LocalDate from = (dateFrom != null) ? dateFrom : LocalDate.of(2000, 1, 1);
        LocalDate to = (dateTo != null) ? dateTo : LocalDate.of(2099, 12, 31);
        boolean hasRange = (dateFrom != null || dateTo != null);

        Page<SteamDailyUsage> page;
        if (equipmentId != null && hasRange) {
            page = usageRepository.findByEquipmentAndDateRange(equipmentId, from, to, pageable);
        } else if (equipmentId != null) {
            page = usageRepository.findByEquipmentAndDateRange(equipmentId, from, to, pageable);
        } else if (hasRange) {
            page = usageRepository.findByDateRange(from, to, pageable);
        } else {
            page = usageRepository.findAllByOrderByUsageDateDescEquipmentIdAsc(pageable);
        }
        return page.map(this::toResponse);
    }

    public SteamDailyUsageResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    /** (일자+설비) 업서트 */
    @Transactional
    public SteamDailyUsageResponse save(SteamDailyUsageSaveRequest request) {
        SteamEquipment equipment = equipmentRepository.findById(request.getEquipmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "존재하지 않는 설비입니다. equipmentId=" + request.getEquipmentId()));

        SteamDailyUsage existing = usageRepository
                .findByUsageDateAndEquipmentId(request.getUsageDate(), request.getEquipmentId())
                .orElse(null);

        boolean isUpdate;
        SteamDailyUsage entity;
        if (existing != null) {
            existing.update(request.getSteamAmount(), request.getRuntimeHours(),
                    request.getProductionKg(), request.getUnitRate(), request.getRemark());
            entity = existing;
            isUpdate = true;
        } else {
            entity = usageRepository.save(SteamDailyUsage.builder()
                    .usageDate(request.getUsageDate())
                    .equipmentId(request.getEquipmentId())
                    .steamAmount(request.getSteamAmount())
                    .runtimeHours(request.getRuntimeHours())
                    .productionKg(request.getProductionKg())
                    .unitRate(request.getUnitRate())
                    .remark(request.getRemark())
                    .build());
            isUpdate = false;
        }
        log.info("[STEAM-ENERGY] 일일 실적 {} - date: {}, equipmentId: {}",
                isUpdate ? "갱신" : "신규", request.getUsageDate(), request.getEquipmentId());
        return SteamDailyUsageResponse.from(entity, equipment.getEquipmentCode(), equipment.getName(), isUpdate);
    }

    @Transactional
    /**
     * 소프트 삭제 — 물리 삭제하지 않고 DELETED_YN 을 'Y' 로 바꾼다.
     * 삭제자(DELETED_BY)는 core 에 CurrentUserProvider 가 생기면 채운다.
     */
    public void delete(Long id) {
        SteamDailyUsage target = usageRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("일일 실적", id));
        target.delete(null);
        log.info("[STEAM-ENERGY] 일일 실적 삭제(소프트) - id: {}", id);
    }

    private SteamDailyUsageResponse toResponse(SteamDailyUsage u) {
        SteamEquipment eq = equipmentRepository.findById(u.getEquipmentId()).orElse(null);
        String code = eq != null ? eq.getEquipmentCode() : null;
        String name = eq != null ? eq.getName() : null;
        return SteamDailyUsageResponse.from(u, code, name, null);
    }

    private SteamDailyUsage findOrThrow(Long id) {
        return usageRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("일일 실적", id));
    }
}
