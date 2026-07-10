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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityEquipmentService {

    public static final String CATEGORY_AIRCON = "AIRCON";
    public static final String CATEGORY_WATER_PURIFIER = "WATER_PURIFIER";

    private static final Set<String> AIRCON_TYPES = Set.of("시스템", "벽걸이", "스탠드형");
    private static final String WATER_PURIFIER_TYPE = "정수기";
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
        String equipmentType = normalizeEquipmentType(normalizedCategory, req.getEquipmentType());
        validateType(normalizedCategory, equipmentType);
        boolean waterPurifier = CATEGORY_WATER_PURIFIER.equals(normalizedCategory);
        String manufacturer = waterPurifier ? null : trimToNull(req.getManufacturer());
        String locationDescription = waterPurifier ? null : trimToNull(req.getLocationDescription());
        int outdoorUnitCount = waterPurifier ? 1 : normalizeOutdoorUnitCount(req.getOutdoorUnitCount());
        int replacementCycleYears = waterPurifier ? 10 : req.getReplacementCycleYears();
        String note = waterPurifier ? null : req.getNote();

        Building building = buildingRepository.findById(req.getBuildingId())
                .orElseThrow(() -> new BusinessException("건물 정보를 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(req.getFloorId())
                .orElseThrow(() -> new BusinessException("층 정보를 찾을 수 없습니다."));

        BigDecimal x = normalizeCoord(req.getX());
        BigDecimal y = normalizeCoord(req.getY());

        FacilityEquipment entity;
        if (req.getEquipmentId() != null && req.getEquipmentId() > 0) {
            entity = findOwned(normalizedCategory, req.getEquipmentId());
            String serialNumber = resolveSerialNumber(normalizedCategory, building, req.getSerialNumber(), entity);
            entity.update(serialNumber, building, floor, equipmentType, manufacturer,
                    locationDescription, outdoorUnitCount,
                    req.getManufactureDate(), replacementCycleYears, x, y, note);
        } else {
            String serialNumber = resolveSerialNumber(normalizedCategory, building, req.getSerialNumber(), null);
            entity = FacilityEquipment.builder()
                    .category(normalizedCategory)
                    .serialNumber(serialNumber)
                    .building(building)
                    .floor(floor)
                    .equipmentType(equipmentType)
                    .manufacturer(manufacturer)
                    .locationDescription(locationDescription)
                    .outdoorUnitCount(outdoorUnitCount)
                    .manufactureDate(req.getManufactureDate())
                    .replacementCycleYears(replacementCycleYears)
                    .x(x)
                    .y(y)
                    .note(note)
                    .build();
            equipmentRepository.save(entity);
        }

        log.info("Facility equipment saved: category={}, id={}, serial={}", normalizedCategory, entity.getEquipmentId(), entity.getSerialNumber());
        return FacilityEquipmentResponse.from(entity);
    }

    @Transactional
    public FacilityEquipmentResponse registerMobileEquipment(String category, String qrKey, FacilityEquipmentSaveRequest req) {
        String normalizedCategory = normalizeCategory(category);
        String cleanQrKey = trimToNull(qrKey);
        if (cleanQrKey == null) {
            throw new BusinessException("QR 키가 비어 있습니다.");
        }
        equipmentRepository.findByQrKey(cleanQrKey).ifPresent(existing -> {
            throw new BusinessException("이미 등록된 QR입니다.");
        });

        String equipmentType = normalizeEquipmentType(normalizedCategory, req.getEquipmentType());
        validateType(normalizedCategory, equipmentType);
        boolean waterPurifier = CATEGORY_WATER_PURIFIER.equals(normalizedCategory);
        String manufacturer = waterPurifier ? null : trimToNull(req.getManufacturer());
        String locationDescription = waterPurifier ? null : trimToNull(req.getLocationDescription());
        int outdoorUnitCount = waterPurifier ? 1 : normalizeOutdoorUnitCount(req.getOutdoorUnitCount());
        int replacementCycleYears = waterPurifier ? 10 : req.getReplacementCycleYears();
        String note = waterPurifier ? trimToNull(req.getNote()) : req.getNote();

        Building building = buildingRepository.findById(req.getBuildingId())
                .orElseThrow(() -> new BusinessException("건물 정보를 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(req.getFloorId())
                .orElseThrow(() -> new BusinessException("층 정보를 찾을 수 없습니다."));

        String serialNumber = resolveSerialNumber(normalizedCategory, building, req.getSerialNumber(), null);
        FacilityEquipment entity = FacilityEquipment.builder()
                .category(normalizedCategory)
                .serialNumber(serialNumber)
                .building(building)
                .floor(floor)
                .equipmentType(equipmentType)
                .manufacturer(manufacturer)
                .locationDescription(locationDescription)
                .outdoorUnitCount(outdoorUnitCount)
                .manufactureDate(req.getManufactureDate())
                .replacementCycleYears(replacementCycleYears)
                .x(normalizeCoord(req.getX()))
                .y(normalizeCoord(req.getY()))
                .note(note)
                .qrKey(cleanQrKey)
                .build();
        equipmentRepository.save(entity);
        log.info("Facility equipment mobile registered: category={}, id={}, serial={}, qrKey={}", normalizedCategory, entity.getEquipmentId(), entity.getSerialNumber(), cleanQrKey);
        return FacilityEquipmentResponse.from(entity);
    }

    public List<String> generateUnregisteredQrKeys(String category, int count) {
        String normalizedCategory = normalizeCategory(category);
        int safeCount = Math.max(0, Math.min(count, 500));
        List<String> result = new ArrayList<>(safeCount);
        while (result.size() < safeCount) {
            String key = UUID.randomUUID().toString().replace("-", "");
            if (!equipmentRepository.existsByQrKey(key) && !result.contains(key)) {
                result.add(key);
            }
        }
        log.info("Generated unregistered facility QR keys: category={}, count={}", normalizedCategory, result.size());
        return result;
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

    private String normalizeEquipmentType(String category, String equipmentType) {
        if (CATEGORY_WATER_PURIFIER.equals(category)) {
            return WATER_PURIFIER_TYPE;
        }
        return trimToNull(equipmentType);
    }

    private void validateType(String category, String equipmentType) {
        if (CATEGORY_AIRCON.equals(category)) {
            if (equipmentType == null || equipmentType.isBlank()) {
                throw new BusinessException("에어컨 종류를 입력하세요.");
            }
            if (!AIRCON_TYPES.contains(equipmentType.trim())) {
                throw new BusinessException("에어컨 종류는 시스템, 벽걸이, 스탠드형 중 하나여야 합니다.");
            }
        }
    }

    private BigDecimal normalizeCoord(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private int normalizeOutdoorUnitCount(int value) {
        if (value <= 0) return 1;
        return Math.min(value, 2);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String resolveSerialNumber(String category, Building building, String requestedSerialNumber, FacilityEquipment current) {
        String requested = trimToNull(requestedSerialNumber);
        if (CATEGORY_AIRCON.equals(category) && requested == null) {
            throw new BusinessException("에어컨 식별 No.를 입력하세요.");
        }
        String serialNumber = requested != null ? requested : (current != null ? current.getSerialNumber() : generateNextSerialNumber(category, building));
        equipmentRepository.findBySerialNumber(serialNumber).ifPresent(found -> {
            if (current == null || !found.getEquipmentId().equals(current.getEquipmentId())) {
                throw new BusinessException("이미 사용 중인 식별 No.입니다: " + serialNumber);
            }
        });
        return serialNumber;
    }

    private String generateNextSerialNumber(String category, Building building) {
        if (CATEGORY_AIRCON.equals(category)) {
            throw new BusinessException("에어컨 식별 No.는 자동 생성할 수 없습니다. 직접 입력하세요.");
        }
        String prefix = "WP-";
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
