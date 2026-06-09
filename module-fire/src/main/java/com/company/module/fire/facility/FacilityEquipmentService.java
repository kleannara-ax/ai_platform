package com.company.module.fire.facility;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.BuildingRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityEquipmentService {

    public static final String CATEGORY_AIRCON = "AIRCON";
    public static final String CATEGORY_WATER_PURIFIER = "WATER_PURIFIER";

    private static final Set<String> AIRCON_TYPES = Set.of("시스템", "벽걸이", "스탠드형");
    private static final int MAX_INSPECTION_HISTORY = 12;

    private final FacilityEquipmentRepository equipmentRepository;
    private final FacilityEquipmentInspectionRepository inspectionRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    public Page<FacilityEquipmentResponse> getEquipmentList(String category, Long buildingId, Long floorId,
                                                            String keyword, int page, int size) {
        String normalizedCategory = normalizeCategory(category);
        Pageable pageable = PageRequest.of(page, size, Sort.by("equipmentId").ascending());
        Long bId = (buildingId != null && buildingId > 0) ? buildingId : null;
        Long fId = (floorId != null && floorId > 0) ? floorId : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        return equipmentRepository.search(normalizedCategory, bId, fId, kw, pageable).map(e -> {
            FacilityEquipmentResponse dto = FacilityEquipmentResponse.from(e);
            inspectionRepository.findTopByEquipment_EquipmentIdOrderByInspectionDateDescInspectionIdDesc(e.getEquipmentId())
                    .ifPresent(dto::setLastInspection);
            return dto;
        });
    }

    public FacilityEquipmentResponse getDetail(String category, Long equipmentId) {
        FacilityEquipment e = findOwned(category, equipmentId);
        FacilityEquipmentResponse dto = FacilityEquipmentResponse.from(e);
        Pageable top12 = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<FacilityEquipmentInspection> history = inspectionRepository
                .findByEquipment_EquipmentIdOrderByInspectionDateDescInspectionIdDesc(equipmentId, top12);
        if (!history.isEmpty()) {
            dto.setLastInspection(history.get(0));
        }
        dto.setInspectionHistory(history);
        return dto;
    }

    @Transactional
    public FacilityEquipmentResponse saveEquipment(String category, FacilityEquipmentSaveRequest req) {
        String normalizedCategory = normalizeCategory(category);
        validateType(normalizedCategory, req.getEquipmentType());

        Building building = buildingRepository.findById(req.getBuildingId())
                .orElseThrow(() -> new BusinessException("건물 정보를 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(req.getFloorId())
                .orElseThrow(() -> new BusinessException("층 정보를 찾을 수 없습니다."));

        BigDecimal x = normalizeCoord(req.getX());
        BigDecimal y = normalizeCoord(req.getY());

        FacilityEquipment entity;
        if (req.getEquipmentId() != null && req.getEquipmentId() > 0) {
            entity = findOwned(normalizedCategory, req.getEquipmentId());
            entity.update(building, floor, req.getEquipmentType(), req.getManufactureDate(),
                    req.getReplacementCycleYears(), req.getQuantity(), x, y, req.getNote());
        } else {
            String serialNumber = generateNextSerialNumber(normalizedCategory);
            entity = FacilityEquipment.builder()
                    .category(normalizedCategory)
                    .serialNumber(serialNumber)
                    .building(building)
                    .floor(floor)
                    .equipmentType(req.getEquipmentType())
                    .manufactureDate(req.getManufactureDate())
                    .replacementCycleYears(req.getReplacementCycleYears())
                    .quantity(req.getQuantity())
                    .x(x)
                    .y(y)
                    .note(req.getNote())
                    .build();
            equipmentRepository.save(entity);
        }

        log.info("Facility equipment saved: category={}, id={}, serial={}", normalizedCategory, entity.getEquipmentId(), entity.getSerialNumber());
        return FacilityEquipmentResponse.from(entity);
    }

    @Transactional
    public void inspect(String category, FacilityEquipmentInspectRequest req, Long userId, String inspectorName) {
        if (req.isFaulty() && (req.getFaultReason() == null || req.getFaultReason().isBlank())) {
            throw new BusinessException("비정상인 경우 고장 사유가 필요합니다.");
        }
        FacilityEquipment equipment = findOwned(category, req.getEquipmentId());
        LocalDate inspectionDate = req.getInspectionDate() != null ? req.getInspectionDate() : LocalDate.now();
        if (inspectionRepository.existsByEquipment_EquipmentIdAndInspectionDate(equipment.getEquipmentId(), inspectionDate)) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }
        inspectionRepository.save(FacilityEquipmentInspection.builder()
                .equipment(equipment)
                .inspectionDate(inspectionDate)
                .isFaulty(req.isFaulty())
                .faultReason(req.getFaultReason())
                .inspectedByUserId(userId)
                .inspectedByName(inspectorName)
                .build());
        inspectionRepository.trimInspectionsKeepLatest12(equipment.getEquipmentId());
    }

    @Transactional
    public void addInspection(String category, Long equipmentId, LocalDate inspectionDate, boolean isFaulty,
                              String faultReason, String inspectorName, Long userId) {
        if (inspectionDate == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("비정상인 경우 고장 사유가 필요합니다.");
        }
        FacilityEquipment equipment = findOwned(category, equipmentId);
        if (inspectionRepository.existsByEquipment_EquipmentIdAndInspectionDate(equipmentId, inspectionDate)) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }
        inspectionRepository.save(FacilityEquipmentInspection.builder()
                .equipment(equipment)
                .inspectionDate(inspectionDate)
                .isFaulty(isFaulty)
                .faultReason(faultReason)
                .inspectedByUserId(userId)
                .inspectedByName((inspectorName == null || inspectorName.isBlank()) ? "관리자" : inspectorName.trim())
                .build());
        inspectionRepository.trimInspectionsKeepLatest12(equipmentId);
    }

    @Transactional
    public void updateInspection(String category, Long equipmentId, Long inspectionId, LocalDate inspectionDate,
                                 boolean isFaulty, String faultReason, String inspectorName) {
        if (inspectionDate == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        if (isFaulty && (faultReason == null || faultReason.isBlank())) {
            throw new BusinessException("비정상인 경우 고장 사유가 필요합니다.");
        }
        findOwned(category, equipmentId);
        FacilityEquipmentInspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("점검 이력", inspectionId));
        if (inspection.getEquipment() == null || !equipmentId.equals(inspection.getEquipment().getEquipmentId())) {
            throw new BusinessException("설비와 점검 이력이 일치하지 않습니다.");
        }
        if (inspectionRepository.existsByEquipment_EquipmentIdAndInspectionDateAndInspectionIdNot(equipmentId, inspectionDate, inspectionId)) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }
        inspection.updateInspection(inspectionDate, isFaulty, faultReason, inspectorName);
    }

    @Transactional
    public void deleteInspection(String category, Long equipmentId, Long inspectionId) {
        findOwned(category, equipmentId);
        FacilityEquipmentInspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("점검 이력", inspectionId));
        if (inspection.getEquipment() == null || !equipmentId.equals(inspection.getEquipment().getEquipmentId())) {
            throw new BusinessException("설비와 점검 이력이 일치하지 않습니다.");
        }
        inspectionRepository.delete(inspection);
    }

    @Transactional
    public void deleteEquipment(String category, Long equipmentId) {
        FacilityEquipment equipment = findOwned(category, equipmentId);
        equipmentRepository.delete(equipment);
    }

    @Transactional
    public void updateImagePath(String category, Long equipmentId, String imagePath) {
        FacilityEquipment equipment = findOwned(category, equipmentId);
        equipment.updateImagePath(imagePath);
    }

    public FacilityEquipmentResponse getBySerial(String serialNumber) {
        FacilityEquipment equipment = equipmentRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new EntityNotFoundException("FacilityEquipment", serialNumber));
        return FacilityEquipmentResponse.from(equipment);
    }

    private FacilityEquipment findOwned(String category, Long equipmentId) {
        String normalizedCategory = normalizeCategory(category);
        FacilityEquipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new EntityNotFoundException("FacilityEquipment", equipmentId));
        if (!normalizedCategory.equals(equipment.getCategory())) {
            throw new BusinessException("요청한 설비 유형과 대상 설비가 일치하지 않습니다.");
        }
        return equipment;
    }

    private String normalizeCategory(String category) {
        if (CATEGORY_AIRCON.equals(category) || CATEGORY_WATER_PURIFIER.equals(category)) {
            return category;
        }
        throw new BusinessException("지원하지 않는 기타설비 유형입니다.");
    }

    private void validateType(String category, String equipmentType) {
        if (equipmentType == null || equipmentType.isBlank()) {
            throw new BusinessException("설비 종류를 입력하세요.");
        }
        if (CATEGORY_AIRCON.equals(category) && !AIRCON_TYPES.contains(equipmentType.trim())) {
            throw new BusinessException("에어컨 종류는 시스템, 벽걸이, 스탠드형 중 하나여야 합니다.");
        }
    }

    private BigDecimal normalizeCoord(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateNextSerialNumber(String category) {
        String prefix = CATEGORY_AIRCON.equals(category) ? "AC-" : "WP-";
        List<String> serials = equipmentRepository.findByCategory(category).stream()
                .map(FacilityEquipment::getSerialNumber)
                .filter(s -> s != null && s.startsWith(prefix))
                .toList();
        int maxNum = 0;
        for (String serial : serials) {
            try {
                int num = Integer.parseInt(serial.substring(prefix.length()));
                if (num > maxNum) maxNum = num;
            } catch (NumberFormatException ignored) { }
        }
        return String.format("%s%06d", prefix, maxNum + 1);
    }
}
