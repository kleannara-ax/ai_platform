package com.company.module.fire.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.fire.dto.EquipmentInspectionItemRequest;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FirePumpResponse;
import com.company.module.fire.dto.FirePumpSaveRequest;
import com.company.module.fire.entity.FirePump;
import com.company.module.fire.entity.FirePumpInspection;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.FirePumpInspectionRepository;
import com.company.module.fire.repository.FirePumpRepository;
import com.company.module.fire.repository.FloorRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FirePumpService {

    private static final long DEFAULT_OUTDOOR_FLOOR_ID = 99L;
    private static final int MAX_INSPECTION_HISTORY = 12;
    private static final List<InspectionWorkbookExporter.ItemColumn> PUMP_EXPORT_COLUMNS = List.of(
            new InspectionWorkbookExporter.ItemColumn("pump_operation", "소방펌프(주, 보조, 예비) 작동여부 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("panel", "소방판넬 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("water_supply", "소화용수 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("fuel", "펌프(엔진)연료 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("drain_pump", "배수펌프 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("piping", "소방배관 [점검결과]")
    );

    private final FirePumpRepository firePumpRepository;
    private final FirePumpInspectionRepository firePumpInspectionRepository;
    private final FloorRepository floorRepository;
    private final ObjectMapper objectMapper;


    public Page<FirePumpResponse> getPumps(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("pumpId").ascending());
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return firePumpRepository.search(kw, pageable).map(pump -> {
            FirePumpResponse response = FirePumpResponse.from(pump);
            firePumpInspectionRepository
                    .findTopByPump_PumpIdOrderByInspectionDateDescInspectionIdDesc(pump.getPumpId())
                    .ifPresent(response::setLastInspection);
            return response;
        });
    }


    public FirePumpResponse getPumpDetail(Long pumpId) {
        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));

        FirePumpResponse response = FirePumpResponse.from(pump);
        Pageable pageable = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<FirePumpInspection> history = firePumpInspectionRepository
                .findByPump_PumpIdOrderByInspectionDateDescInspectionIdDesc(pumpId, pageable);

        if (!history.isEmpty()) {
            response.setLastInspection(history.get(0));
        }
        response.setInspectionHistory(history, history.stream().map(this::parseChecklist).toList());
        return response;
    }


    public byte[] exportInspectionWorkbook(Long pumpId, LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);

        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));
        List<FirePumpInspection> inspections = firePumpInspectionRepository
                .findByPump_PumpIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
                        pumpId, fromDate, toDate);

        List<InspectionWorkbookExporter.RowData> rows = new ArrayList<>();
        boolean latestForPump = true;
        for (FirePumpInspection inspection : inspections) {
            rows.add(toWorkbookRow(pump, inspection, latestForPump));
            latestForPump = false;
        }
        return InspectionWorkbookExporter.export("소방펌프 점검보고서", PUMP_EXPORT_COLUMNS, rows, this::resolveInspectionImage);
    }


    public byte[] exportAllInspectionWorkbook(LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);
        List<FirePumpInspection> inspections = firePumpInspectionRepository
                .findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(fromDate, toDate);

        Set<Long> pumpIdsAlreadyExported = new HashSet<>();
        List<InspectionWorkbookExporter.RowData> rows = new ArrayList<>();
        for (FirePumpInspection inspection : inspections) {
            FirePump pump = inspection.getPump();
            Long pumpId = pump == null ? null : pump.getPumpId();
            boolean latestForPump = pumpId != null && pumpIdsAlreadyExported.add(pumpId);
            rows.add(toWorkbookRow(pump, inspection, latestForPump));
        }
        return InspectionWorkbookExporter.export("소방펌프 점검보고서", PUMP_EXPORT_COLUMNS, rows, this::resolveInspectionImage);
    }

    @Transactional
    public FirePumpResponse save(FirePumpSaveRequest request) {
        Floor floor = floorRepository.findById(DEFAULT_OUTDOOR_FLOOR_ID)
                .orElseThrow(() -> new BusinessException("옥외 층 정보가 없습니다."));

        BigDecimal x = normalizeCoord(request.getX());
        BigDecimal y = normalizeCoord(request.getY());

        FirePump pump;
        if (request.getPumpId() != null && request.getPumpId() > 0) {
            pump = firePumpRepository.findById(request.getPumpId())
                    .orElseThrow(() -> new EntityNotFoundException("FirePump", request.getPumpId()));
            pump.update(request.getBuildingName().trim(), floor, x, y,
                    request.getLocationDescription(), request.getNote());
        } else {
            pump = FirePump.builder()
                    .serialNumber(generateNextSerialNumber())
                    .buildingName(request.getBuildingName().trim())
                    .floor(floor)
                    .x(x)
                    .y(y)
                    .locationDescription(request.getLocationDescription())
                    .note(request.getNote())
                    .isActive(true)
                    .build();
            firePumpRepository.save(pump);
        }

        log.info("FirePump saved: id={}, serial={}", pump.getPumpId(), pump.getSerialNumber());
        return FirePumpResponse.from(pump);
    }

    @Transactional
    public FirePumpResponse inspect(Long pumpId, EquipmentInspectionRequest request,
                                    Long userId, String inspectorName) {
        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));

        if (firePumpInspectionRepository.existsByPump_PumpIdAndInspectionDate(pumpId, java.time.LocalDate.now())) {
            throw new BusinessException("오늘 이미 점검이 완료된 소방펌프입니다.");
        }

        String checklistJson = writeChecklist(request.getItems());
        String inspectionStatus = resolveInspectionStatus(request.getItems());
        Map<String, String> statusMap = toStatusMap(request.getItems());
        LocalTime inspectionTime = request.getInspectionTime() != null ? request.getInspectionTime() : LocalTime.now().withSecond(0).withNano(0);

        FirePumpInspection inspection = FirePumpInspection.builder()
                .pump(pump)
                .inspectionDate(java.time.LocalDate.now())
                .inspectionTime(inspectionTime)
                .inspectionStatus(inspectionStatus)
                .checklistJson(checklistJson)
                .note(trimToNull(request.getNote()))
                .pumpOperationStatus(statusMap.get("pump_operation"))
                .panelStatus(statusMap.get("panel"))
                .waterSupplyStatus(statusMap.get("water_supply"))
                .fuelStatus(statusMap.get("fuel"))
                .drainPumpStatus(statusMap.get("drain_pump"))
                .pipingStatus(statusMap.get("piping"))
                .inspectedByUserId(userId)
                .inspectedByName(inspectorName)
                .build();
        firePumpInspectionRepository.save(inspection);
        firePumpInspectionRepository.trimInspectionsKeepLatest12(pumpId);

        return getPumpDetail(pumpId);
    }

    @Transactional
    public void updateInspectionImagePath(Long pumpId, Long inspectionId, String imagePath) {
        FirePumpInspection inspection = firePumpInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FirePumpInspection", inspectionId));
        if (inspection.getPump() == null || !pumpId.equals(inspection.getPump().getPumpId())) {
            throw new BusinessException("소방펌프 점검 이력이 올바르지 않습니다.");
        }
        inspection.updateImagePath(imagePath);
        inspection.getPump().updateImagePath(imagePath);
    }

    @Transactional
    public void updateImagePath(Long pumpId, String imagePath) {
        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));
        pump.updateImagePath(imagePath);
    }

    @Transactional
    public FirePumpResponse updateInspection(Long pumpId, Long inspectionId, EquipmentInspectionUpdateRequest request) {
        FirePumpInspection inspection = firePumpInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FirePumpInspection", inspectionId));
        if (inspection.getPump() == null || !pumpId.equals(inspection.getPump().getPumpId())) {
            throw new BusinessException("Inspection does not belong to this pump.");
        }
        if (firePumpInspectionRepository.existsByPump_PumpIdAndInspectionDateAndInspectionIdNot(
                pumpId, request.getInspectionDate(), inspectionId)) {
            throw new BusinessException("An inspection already exists for the selected date.");
        }

        String checklistJson = writeChecklist(request.getItems());
        String inspectionStatus = resolveInspectionStatus(request.getItems());
        Map<String, String> statusMap = toStatusMap(request.getItems());
        String inspectorName = trimToNull(request.getInspectorName());
        if (inspectorName == null) {
            inspectorName = inspection.getInspectedByName();
        }
        inspection.updateInspection(
                request.getInspectionDate(),
                request.getInspectionTime(),
                inspectionStatus,
                checklistJson,
                trimToNull(request.getNote()),
                inspectorName,
                statusMap.get("pump_operation"),
                statusMap.get("panel"),
                statusMap.get("water_supply"),
                statusMap.get("fuel"),
                statusMap.get("drain_pump"),
                statusMap.get("piping")
        );
        return getPumpDetail(pumpId);
    }

    @Transactional
    public FirePumpResponse addInspection(Long pumpId, EquipmentInspectionUpdateRequest request) {
        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));
        if (firePumpInspectionRepository.existsByPump_PumpIdAndInspectionDate(pumpId, request.getInspectionDate())) {
            throw new BusinessException("An inspection already exists for the selected date.");
        }
        String checklistJson = writeChecklist(request.getItems());
        String inspectionStatus = resolveInspectionStatus(request.getItems());
        Map<String, String> statusMap = toStatusMap(request.getItems());
        String inspectorName = trimToNull(request.getInspectorName());
        if (inspectorName == null) {
            inspectorName = "관리자";
        }
        FirePumpInspection inspection = FirePumpInspection.builder()
                .pump(pump)
                .inspectionDate(request.getInspectionDate())
                .inspectionTime(request.getInspectionTime())
                .inspectionStatus(inspectionStatus)
                .checklistJson(checklistJson)
                .note(trimToNull(request.getNote()))
                .pumpOperationStatus(statusMap.get("pump_operation"))
                .panelStatus(statusMap.get("panel"))
                .waterSupplyStatus(statusMap.get("water_supply"))
                .fuelStatus(statusMap.get("fuel"))
                .drainPumpStatus(statusMap.get("drain_pump"))
                .pipingStatus(statusMap.get("piping"))
                .inspectedByName(inspectorName)
                .build();
        firePumpInspectionRepository.save(inspection);
        firePumpInspectionRepository.trimInspectionsKeepLatest12(pumpId);
        return getPumpDetail(pumpId);
    }

    @Transactional
    public FirePumpResponse deleteInspection(Long pumpId, Long inspectionId) {
        FirePumpInspection inspection = firePumpInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FirePumpInspection", inspectionId));
        if (inspection.getPump() == null || !pumpId.equals(inspection.getPump().getPumpId())) {
            throw new BusinessException("Inspection does not belong to this pump.");
        }
        firePumpInspectionRepository.delete(inspection);
        return getPumpDetail(pumpId);
    }

    @Transactional
    public void delete(Long pumpId) {
        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));
        firePumpRepository.delete(pump);
    }

    private BigDecimal normalizeCoord(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateNextSerialNumber() {
        List<String> serials = firePumpRepository.findAllSerialNumbers();
        int maxNum = 0;
        for (String serial : serials) {
            try {
                maxNum = Math.max(maxNum, Integer.parseInt(serial.substring(4)));
            } catch (RuntimeException ignored) {
            }
        }
        return String.format("PMP-%06d", maxNum + 1);
    }

    private String resolveInspectionStatus(List<EquipmentInspectionItemRequest> items) {
        boolean hasFaulty = false;
        boolean hasMaintenance = false;
        for (EquipmentInspectionItemRequest item : items) {
            String result = normalizeResult(item.getResult());
            if ("FAULTY".equals(result)) {
                hasFaulty = true;
            } else if ("MAINTENANCE".equals(result)) {
                hasMaintenance = true;
            } else if (!"NORMAL".equals(result)) {
                throw new BusinessException("점검 결과는 정상, 요정비, 불량만 가능합니다.");
            }
        }
        if (hasFaulty) {
            return "FAULTY";
        }
        if (hasMaintenance) {
            return "MAINTENANCE";
        }
        return "NORMAL";
    }

    private String normalizeResult(String result) {
        String normalized = trimToNull(result);
        if (normalized == null) {
            return "";
        }
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "정상", "NORMAL" -> "NORMAL";
            case "요정비", "MAINTENANCE", "NEED_MAINTENANCE" -> "MAINTENANCE";
            case "불량", "FAULTY" -> "FAULTY";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private String writeChecklist(List<EquipmentInspectionItemRequest> items) {
        try {
            List<FirePumpResponse.InspectionChecklistItem> mapped = items.stream()
                    .map(item -> new FirePumpResponse.InspectionChecklistItem(
                            trimToNull(item.getItemKey()),
                            trimToNull(item.getItemLabel()),
                            normalizeResult(item.getResult())))
                    .toList();
            return objectMapper.writeValueAsString(mapped);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("점검 항목 저장에 실패했습니다.");
        }
    }

    private Map<String, String> toStatusMap(List<EquipmentInspectionItemRequest> items) {
        Map<String, String> statusMap = new LinkedHashMap<>();
        for (EquipmentInspectionItemRequest item : items) {
            String key = trimToNull(item.getItemKey());
            if (key == null) {
                continue;
            }
            statusMap.put(key, normalizeResult(item.getResult()));
        }
        return statusMap;
    }

    private List<FirePumpResponse.InspectionChecklistItem> parseChecklist(FirePumpInspection inspection) {
        List<FirePumpResponse.InspectionChecklistItem> fromColumns = buildChecklistFromColumns(inspection);
        if (!fromColumns.isEmpty()) {
            return fromColumns;
        }
        String checklistJson = trimToNull(inspection.getChecklistJson());
        if (checklistJson == null) {
            return List.of();
        }
        try {
            List<EquipmentInspectionItemRequest> items = objectMapper.readValue(
                    checklistJson,
                    new TypeReference<List<EquipmentInspectionItemRequest>>() {}
            );
            return items.stream()
                    .map(item -> new FirePumpResponse.InspectionChecklistItem(
                            trimToNull(item.getItemKey()),
                            trimToNull(item.getItemLabel()),
                            normalizeResult(item.getResult())))
                    .toList();
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse pump inspection checklist: inspectionId={}", inspection.getInspectionId(), ex);
            return List.of();
        }
    }

    private List<FirePumpResponse.InspectionChecklistItem> buildChecklistFromColumns(FirePumpInspection inspection) {
        List<FirePumpResponse.InspectionChecklistItem> items = new ArrayList<>();
        addChecklistItem(items, "pump_operation", "소방펌프(주, 보조, 예비) 작동여부", inspection.getPumpOperationStatus());
        addChecklistItem(items, "panel", "소방판넬", inspection.getPanelStatus());
        addChecklistItem(items, "water_supply", "소화용수", inspection.getWaterSupplyStatus());
        addChecklistItem(items, "fuel", "펌프(엔진)연료", inspection.getFuelStatus());
        addChecklistItem(items, "drain_pump", "배수펌프", inspection.getDrainPumpStatus());
        addChecklistItem(items, "piping", "소방배관", inspection.getPipingStatus());
        return items;
    }

    private void addChecklistItem(List<FirePumpResponse.InspectionChecklistItem> items, String itemKey, String itemLabel, String result) {
        String normalized = trimToNull(result);
        if (normalized == null) {
            return;
        }
        items.add(new FirePumpResponse.InspectionChecklistItem(itemKey, itemLabel, normalized));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateExportRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BusinessException("조회 시작일과 종료일을 입력해 주세요.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private InspectionWorkbookExporter.RowData toWorkbookRow(FirePump pump,
                                                               FirePumpInspection inspection,
                                                               boolean latestForPump) {
        String sectionTitle = pump != null ? pump.getBuildingName() : "소방펌프";
        String imagePath = trimToNull(inspection.getImagePath());
        if (imagePath == null && latestForPump && pump != null) {
            imagePath = trimToNull(pump.getImagePath());
        }
        return new InspectionWorkbookExporter.RowData(
                sectionTitle,
                inspection.getInspectionDate(),
                inspection.getInspectionTime(),
                inspection.getInspectedByName(),
                toItemResultMap(parseChecklist(inspection)),
                imagePath,
                inspection.getNote()
        );
    }

    private Map<String, String> toItemResultMap(List<FirePumpResponse.InspectionChecklistItem> items) {
        Map<String, String> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (FirePumpResponse.InspectionChecklistItem item : items) {
            String key = trimToNull(item.getItemKey());
            if (key != null) {
                result.put(key, item.getResult());
            }
        }
        return result;
    }

    private java.util.Optional<InspectionWorkbookExporter.ImageFile> resolveInspectionImage(String imagePath) {
        return InspectionWorkbookExporter.loadImage(imagePath, uploadDir("pump-inspections"))
                .or(() -> InspectionWorkbookExporter.loadImage(imagePath, uploadDir("pumps")))
                .or(() -> InspectionWorkbookExporter.loadImage(imagePath, Paths.get("/data/upload/module_fire/pump-inspections")))
                .or(() -> InspectionWorkbookExporter.loadImage(imagePath, Paths.get("/data/upload/module_fire/pumps")));
    }

    private static Path uploadDir(String child) {
        String root = System.getenv("MODULE_FIRE_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_fire").toString();
        }
        return Paths.get(root).resolve(child).normalize();
    }
}
