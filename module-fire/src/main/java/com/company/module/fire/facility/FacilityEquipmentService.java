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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityEquipmentService {

    public static final String CATEGORY_AIRCON = "AIRCON";
    public static final String CATEGORY_WATER_PURIFIER = "WATER_PURIFIER";

    private static final Set<String> AIRCON_TYPES = Set.of("시스템", "벽걸이", "스탠드형");
    private static final Map<String, String> AIRCON_BUILDING_PREFIXES = Map.ofEntries(
            Map.entry("관리동", "1"),
            Map.entry("복지관", "2"),
            Map.entry("화장지45호동", "3"),
            Map.entry("경비실중문", "4"),
            Map.entry("천막창고중문", "5"),
            Map.entry("보일러동", "6"),
            Map.entry("물류현장사무실container", "7"),
            Map.entry("전기현장사무실동", "8"),
            Map.entry("화장지13동", "9"),
            Map.entry("생산지원팀동", "10"),
            Map.entry("pulper동", "11"),
            Map.entry("원료장", "12"),
            Map.entry("제지2호동화장지2호동", "13"),
            Map.entry("제지3호동", "14"),
            Map.entry("수출창고동제지", "15"),
            Map.entry("pad동", "16"),
            Map.entry("환경에너지동", "17"),
            Map.entry("폐수처리및소각동", "18"),
            Map.entry("동진창고", "19"),
            Map.entry("경비실정문", "20"),
            Map.entry("유동상보일러동", "21"),
            Map.entry("신규설치", "22"),
            Map.entry("제지1호기", "130")
    );
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
        BigDecimal outdoorX = normalizeCoord(req.getOutdoorX());
        BigDecimal outdoorY = normalizeCoord(req.getOutdoorY());

        FacilityEquipment entity;
        if (req.getEquipmentId() != null && req.getEquipmentId() > 0) {
            entity = findOwned(normalizedCategory, req.getEquipmentId());
            String serialNumber = resolveSerialNumber(normalizedCategory, building, req.getSerialNumber(), entity);
            entity.update(serialNumber, building, floor, req.getEquipmentType(), trimToNull(req.getManufacturer()),
                    normalizeInstallationYear(req.getInstallationYear()), trimToNull(req.getLocationDescription()),
                    normalizeOutdoorUnitCount(req.getOutdoorUnitCount()), outdoorX, outdoorY, req.getManufactureDate(),
                    req.getReplacementCycleYears(), req.getQuantity(), x, y, req.getNote());
        } else {
            String serialNumber = resolveSerialNumber(normalizedCategory, building, req.getSerialNumber(), null);
            entity = FacilityEquipment.builder()
                    .category(normalizedCategory)
                    .serialNumber(serialNumber)
                    .building(building)
                    .floor(floor)
                    .equipmentType(req.getEquipmentType())
                    .manufacturer(trimToNull(req.getManufacturer()))
                    .installationYear(normalizeInstallationYear(req.getInstallationYear()))
                    .locationDescription(trimToNull(req.getLocationDescription()))
                    .outdoorUnitCount(normalizeOutdoorUnitCount(req.getOutdoorUnitCount()))
                    .outdoorX(outdoorX)
                    .outdoorY(outdoorY)
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

    private Integer normalizeInstallationYear(Integer year) {
        if (year == null) return null;
        if (year < 1980 || year > 2100) {
            throw new BusinessException("설치연도는 1980~2100 사이로 입력하세요.");
        }
        return year;
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
        String serialNumber = requested != null ? requested : (current != null ? current.getSerialNumber() : generateNextSerialNumber(category, building));
        equipmentRepository.findBySerialNumber(serialNumber).ifPresent(found -> {
            if (current == null || !found.getEquipmentId().equals(current.getEquipmentId())) {
                throw new BusinessException("이미 사용 중인 식별 No.입니다: " + serialNumber);
            }
        });
        return serialNumber;
    }

    private String generateNextSerialNumber(String category, Building building) {
        String prefix = CATEGORY_AIRCON.equals(category) ? resolveAirconBuildingPrefix(building) + "-" : "WP-";
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
        return CATEGORY_AIRCON.equals(category) ? prefix + (maxNum + 1) : String.format("%s%06d", prefix, maxNum + 1);
    }

    private String resolveAirconBuildingPrefix(Building building) {
        String normalized = normalizeName(building != null ? building.getBuildingName() : null);
        String prefix = AIRCON_BUILDING_PREFIXES.get(normalized);
        if (prefix != null) return prefix;
        if (building != null && building.getBuildingId() != null && building.getBuildingId() > 0) {
            return String.valueOf(building.getBuildingId());
        }
        return "AC";
    }

    private String normalizeName(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\r\\n\\t\\\"'()+,._\\-]+", "");
    }
}
