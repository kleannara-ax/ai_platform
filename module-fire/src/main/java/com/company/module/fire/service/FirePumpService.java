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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirePumpService {

    private static final long DEFAULT_OUTDOOR_FLOOR_ID = 7L;
    private static final int MAX_INSPECTION_HISTORY = 12;

    private final FirePumpRepository firePumpRepository;
    private final FirePumpInspectionRepository firePumpInspectionRepository;
    private final FloorRepository floorRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<FirePumpResponse> getPumps(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("pumpId").ascending());
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return firePumpRepository.search(kw, pageable).map(pump -> {
            FirePumpResponse response = new FirePumpResponse(pump);
            firePumpInspectionRepository
                    .findTopByPump_PumpIdOrderByInspectionDateDescInspectionIdDesc(pump.getPumpId())
                    .ifPresent(response::setLastInspection);
            return response;
        });
    }

    @Transactional(readOnly = true)
    public FirePumpResponse getPumpDetail(Long pumpId) {
        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));

        FirePumpResponse response = new FirePumpResponse(pump);
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

    @Transactional(readOnly = true)
    public byte[] exportInspectionCsv(Long pumpId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BusinessException("조회 시작일과 종료일을 입력해 주세요.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));
        List<FirePumpInspection> inspections = firePumpInspectionRepository
                .findByPump_PumpIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
                        pumpId, fromDate, toDate);

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("설비구분,건물,점검일,점검자,점검결과,점검항목결과,비고\n");

        for (FirePumpInspection inspection : inspections) {
            csv.append(csvValue("소방펌프")).append(',')
                    .append(csvValue(pump.getBuildingName())).append(',')
                    .append(csvValue(inspection.getInspectionDate())).append(',')
                    .append(csvValue(inspection.getInspectedByName())).append(',')
                    .append(csvValue(statusLabel(inspection.getInspectionStatus()))).append(',')
                    .append(csvValue(buildChecklistSummary(parseChecklist(inspection)))).append(',')
                    .append(csvValue(inspection.getNote()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportAllInspectionCsv(LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);
        List<FirePumpInspection> inspections = firePumpInspectionRepository
                .findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(fromDate, toDate);

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("설비구분,건물,점검일,점검자,점검결과,점검항목결과,비고\n");

        for (FirePumpInspection inspection : inspections) {
            FirePump pump = inspection.getPump();
            csv.append(csvValue("소방펌프")).append(',')
                    .append(csvValue(pump != null ? pump.getBuildingName() : "")).append(',')
                    .append(csvValue(inspection.getInspectionDate())).append(',')
                    .append(csvValue(inspection.getInspectedByName())).append(',')
                    .append(csvValue(statusLabel(inspection.getInspectionStatus()))).append(',')
                    .append(csvValue(buildChecklistSummary(parseChecklist(inspection)))).append(',')
                    .append(csvValue(inspection.getNote()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public FirePumpResponse save(FirePumpSaveRequest request) {
        Floor floor = floorRepository.findById(DEFAULT_OUTDOOR_FLOOR_ID)
                .orElseThrow(() -> new BusinessException("옥외 층 정보가 없습니다."));

        FirePump pump;
        if (request.getPumpId() != null && request.getPumpId() > 0) {
            pump = firePumpRepository.findById(request.getPumpId())
                    .orElseThrow(() -> new EntityNotFoundException("FirePump", request.getPumpId()));
            pump.update(request.getBuildingName().trim(), floor, request.getX(), request.getY(),
                    request.getLocationDescription(), request.getNote());
        } else {
            pump = FirePump.builder()
                    .serialNumber(generateNextSerialNumber())
                    .buildingName(request.getBuildingName().trim())
                    .floor(floor)
                    .x(request.getX())
                    .y(request.getY())
                    .locationDescription(request.getLocationDescription())
                    .note(request.getNote())
                    .isActive(true)
                    .build();
            firePumpRepository.save(pump);
        }

        log.info("FirePump saved: id={}, serial={}", pump.getPumpId(), pump.getSerialNumber());
        return new FirePumpResponse(pump);
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
    public void delete(Long pumpId) {
        FirePump pump = firePumpRepository.findById(pumpId)
                .orElseThrow(() -> new EntityNotFoundException("FirePump", pumpId));
        firePumpRepository.delete(pump);
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

    private String statusLabel(String status) {
        return switch (String.valueOf(status == null ? "" : status).toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> "정상";
            case "MAINTENANCE" -> "요정비";
            case "FAULTY" -> "불량";
            default -> "";
        };
    }

    private String buildChecklistSummary(List<FirePumpResponse.InspectionChecklistItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> (item.getItemLabel() == null ? "" : item.getItemLabel()) + ": " + statusLabel(item.getResult()))
                .toList()
                .toString()
                .replace("[", "")
                .replace("]", "");
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
