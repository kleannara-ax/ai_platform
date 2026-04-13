package com.company.module.fire.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.fire.dto.FireHydrantResponse;
import com.company.module.fire.dto.FireHydrantSaveRequest;
import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.FireHydrant;
import com.company.module.fire.entity.FireHydrantInspection;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.BuildingRepository;
import com.company.module.fire.repository.FireHydrantInspectionRepository;
import com.company.module.fire.repository.FireHydrantRepository;
import com.company.module.fire.repository.FloorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FireHydrantService {

    private final FireHydrantRepository hydrantRepository;
    private final FireHydrantInspectionRepository inspectionRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    private static final int MAX_INSPECTION_HISTORY = 12;

    public Page<FireHydrantResponse> getHydrants(Long buildingId, Long floorId,
                                                  String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("hydrantId").ascending());

        Long bId = (buildingId != null && buildingId > 0) ? buildingId : null;
        Long fId = (floorId != null && floorId > 0) ? floorId : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<FireHydrant> entityPage = hydrantRepository.searchHydrants(bId, fId, kw, pageable);
        return entityPage.map(h -> {
            FireHydrantResponse dto = FireHydrantResponse.from(h);
            inspectionRepository
                    .findTopByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(h.getHydrantId())
                    .ifPresent(dto::setLastInspection);
            return dto;
        });
    }

    public FireHydrantResponse getHydrantDetail(Long hydrantId) {
        FireHydrant h = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new EntityNotFoundException("FireHydrant", hydrantId));

        FireHydrantResponse dto = FireHydrantResponse.from(h);

        Pageable top12 = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<FireHydrantInspection> history = inspectionRepository
                .findByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(hydrantId, top12);

        if (!history.isEmpty()) {
            dto.setLastInspection(history.get(0));
        }
        dto.setInspectionHistory(history);
        return dto;
    }

    @Transactional
    public FireHydrantResponse saveHydrant(FireHydrantSaveRequest req) {
        String hydrantType = (req.getHydrantType() != null) ? req.getHydrantType().trim() : "Indoor";
        String operationType = (req.getOperationType() != null) ? req.getOperationType().trim() : "Manual";

        if (!"Indoor".equals(hydrantType) && !"Outdoor".equals(hydrantType)) {
            throw new BusinessException("HydrantType must be Indoor or Outdoor.");
        }
        if (!"Auto".equals(operationType) && !"Manual".equals(operationType)) {
            throw new BusinessException("OperationType must be Auto or Manual.");
        }

        FireHydrant entity;
        if (req.getHydrantId() != null && req.getHydrantId() > 0) {
            entity = hydrantRepository.findById(req.getHydrantId())
                    .orElseThrow(() -> new EntityNotFoundException("FireHydrant", req.getHydrantId()));
            hydrantType = entity.getHydrantType();
        } else {
            String serialNumber = generateNextSerialNumber();
            entity = FireHydrant.builder()
                    .serialNumber(serialNumber)
                    .hydrantType(hydrantType)
                    .operationType(operationType)
                    .isActive(true)
                    .build();
            hydrantRepository.save(entity);
        }

        Building building;
        Floor floor;
        BigDecimal x;
        BigDecimal y;

        if ("Outdoor".equals(hydrantType)) {
            building = buildingRepository.findById(99L)
                    .orElseThrow(() -> new BusinessException("Outdoor building(id=99) is missing."));
            // 옥외용 층: id=99 '옥외' 고정
            floor = floorRepository.findById(99L)
                    .orElseThrow(() -> new BusinessException("Outdoor floor(id=99) is missing."));

            if (req.getX() == null || req.getY() == null) {
                throw new BusinessException("Coordinates are required for outdoor hydrants.");
            }
            x = req.getX().setScale(2, java.math.RoundingMode.HALF_UP);
            y = req.getY().setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            if (req.getBuildingId() == null || req.getFloorId() == null) {
                throw new BusinessException("Building and floor are required.");
            }
            building = buildingRepository.findById(req.getBuildingId())
                    .orElseThrow(() -> new BusinessException("Building not found."));
            floor = floorRepository.findById(req.getFloorId())
                    .orElseThrow(() -> new BusinessException("Floor not found."));
            x = req.getX();
            y = req.getY();
        }

        entity.update(operationType, building, floor, x, y, req.getLocationDescription());
        log.info("FireHydrant saved: id={}, serial={}", entity.getHydrantId(), entity.getSerialNumber());
        return FireHydrantResponse.from(entity);
    }

    @Transactional
    public void inspect(Long hydrantId, boolean isFaulty, String faultReason,
                        Long userId, String inspectorName) {
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("faultReason is required when isFaulty=true.");
        }

        FireHydrant hydrant = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new EntityNotFoundException("FireHydrant", hydrantId));

        LocalDate today = LocalDate.now();
        if (inspectionRepository.existsByHydrant_HydrantIdAndInspectionDate(hydrantId, today)) {
            throw new BusinessException("오늘 이미 점검 완료된 소화전입니다.");
        }

        FireHydrantInspection inspection = FireHydrantInspection.builder()
                .hydrant(hydrant)
                .inspectionDate(today)
                .isFaulty(isFaulty)
                .faultReason(faultReason)
                .inspectedByUserId(userId)
                .inspectedByName(inspectorName)
                .build();

        inspectionRepository.save(inspection);
        inspectionRepository.trimInspectionsKeepLatest12(hydrantId);
        log.info("FireHydrant inspected: hydrantId={}, isFaulty={}, by={}", hydrantId, isFaulty, inspectorName);
    }

    @Transactional
    public void addInspection(Long hydrantId, LocalDate inspectionDate, boolean isFaulty,
                              String faultReason, String inspectorName, Long userId) {
        if (inspectionDate == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("비정상인 경우 고장 사유가 필요합니다.");
        }
        FireHydrant hydrant = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new EntityNotFoundException("FireHydrant", hydrantId));
        if (inspectionRepository.existsByHydrant_HydrantIdAndInspectionDate(hydrantId, inspectionDate)) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }
        FireHydrantInspection inspection = FireHydrantInspection.builder()
                .hydrant(hydrant)
                .inspectionDate(inspectionDate)
                .isFaulty(isFaulty)
                .faultReason(faultReason)
                .inspectedByUserId(userId)
                .inspectedByName((inspectorName == null || inspectorName.isBlank()) ? "관리자" : inspectorName.trim())
                .build();
        inspectionRepository.save(inspection);
        inspectionRepository.trimInspectionsKeepLatest12(hydrantId);
    }

    @Transactional
    public void deleteInspection(Long hydrantId, Long inspectionId) {
        FireHydrantInspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("Inspection", inspectionId));

        if (inspection.getHydrant() == null || !hydrantId.equals(inspection.getHydrant().getHydrantId())) {
            throw new BusinessException("Inspection does not belong to this hydrant.");
        }

        inspectionRepository.delete(inspection);
    }

    @Transactional
    public void updateInspection(Long hydrantId, Long inspectionId, LocalDate inspectionDate,
                                 boolean isFaulty, String faultReason, String inspectorName) {
        if (inspectionDate == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("비정상인 경우 고장 사유가 필요합니다.");
        }
        FireHydrantInspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("점검 이력", inspectionId));
        if (inspection.getHydrant() == null || !hydrantId.equals(inspection.getHydrant().getHydrantId())) {
            throw new BusinessException("소화전과 점검 이력이 일치하지 않습니다.");
        }
        boolean duplicated = inspectionRepository
                .existsByHydrant_HydrantIdAndInspectionDateAndInspectionIdNot(hydrantId, inspectionDate, inspectionId);
        if (duplicated) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }
        inspection.updateInspection(inspectionDate, isFaulty, faultReason, inspectorName);
    }

    @Transactional
    public void deleteHydrant(Long hydrantId) {
        FireHydrant h = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new EntityNotFoundException("FireHydrant", hydrantId));
        hydrantRepository.delete(h);
        log.info("FireHydrant deleted: id={}", hydrantId);
    }

    @Transactional
    public void updateImagePath(Long hydrantId, String imagePath) {
        FireHydrant h = hydrantRepository.findById(hydrantId)
                .orElseThrow(() -> new EntityNotFoundException("FireHydrant", hydrantId));
        h.updateImagePath(imagePath);
    }

    private String generateNextSerialNumber() {
        List<String> serials = hydrantRepository.findAllSerialNumbers();
        int maxNum = 0;
        for (String s : serials) {
            try {
                int n = Integer.parseInt(s.substring(4));
                if (n > maxNum) maxNum = n;
            } catch (NumberFormatException ignored) {
            }
        }
        return String.format("HYD-%06d", maxNum + 1);
    }
}
