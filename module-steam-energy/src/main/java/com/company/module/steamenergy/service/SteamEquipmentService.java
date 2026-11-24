package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamEquipmentResponse;
import com.company.module.steamenergy.dto.SteamEquipmentSaveRequest;
import com.company.module.steamenergy.entity.SteamEquipment;
import com.company.module.steamenergy.repository.SteamEquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 스팀 설비 마스터 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEquipmentService {

    private final SteamEquipmentRepository equipmentRepository;

    public Page<SteamEquipmentResponse> getList(String q, String category, Pageable pageable) {
        boolean hasQ = StringUtils.hasText(q);
        boolean hasCategory = StringUtils.hasText(category);

        Page<SteamEquipment> page;
        if (hasQ && hasCategory) {
            page = equipmentRepository.searchByCategory(q, category, pageable);
        } else if (hasQ) {
            page = equipmentRepository.search(q, pageable);
        } else if (hasCategory) {
            page = equipmentRepository.findByCategoryOrderBySortOrderAscEquipmentIdAsc(category, pageable);
        } else {
            page = equipmentRepository.findAllByOrderBySortOrderAscEquipmentIdAsc(pageable);
        }
        return page.map(SteamEquipmentResponse::from);
    }

    public SteamEquipmentResponse get(Long id) {
        return SteamEquipmentResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEquipmentResponse save(SteamEquipmentSaveRequest request) {
        if (equipmentRepository.existsByEquipmentCode(request.getEquipmentCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "이미 존재하는 설비코드입니다: " + request.getEquipmentCode());
        }
        SteamEquipment entity = SteamEquipment.builder()
                .equipmentCode(request.getEquipmentCode())
                .name(request.getName())
                .category(request.getCategory())
                .unit(request.getUnit())
                .sortOrder(request.getSortOrder())
                .isActive(request.getIsActive())
                .build();
        SteamEquipment saved = equipmentRepository.save(entity);
        log.info("[STEAM-ENERGY] 설비 등록 - id: {}, code: {}", saved.getEquipmentId(), saved.getEquipmentCode());
        return SteamEquipmentResponse.from(saved);
    }

    @Transactional
    public SteamEquipmentResponse update(Long id, SteamEquipmentSaveRequest request) {
        SteamEquipment entity = findOrThrow(id);
        entity.update(request.getName(), request.getCategory(), request.getUnit(),
                request.getSortOrder(), request.getIsActive());
        log.info("[STEAM-ENERGY] 설비 수정 - id: {}", id);
        return SteamEquipmentResponse.from(entity);
    }

    @Transactional
    /**
     * 소프트 삭제 — 물리 삭제하지 않고 DELETED_YN 을 'Y' 로 바꾼다.
     * 삭제자(DELETED_BY)는 core 에 CurrentUserProvider 가 생기면 채운다.
     */
    public void delete(Long id) {
        SteamEquipment target = findOrThrow(id);
        target.delete(null);
        log.info("[STEAM-ENERGY] 설비 삭제(소프트) - id: {}", id);
    }

    private SteamEquipment findOrThrow(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("설비", id));
    }
}
