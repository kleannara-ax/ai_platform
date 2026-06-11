package com.company.module.fire.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.fire.dto.EquipmentInspectionItemRequest;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FireReceiverResponse;
import com.company.module.fire.dto.FireReceiverSaveRequest;
import com.company.module.fire.entity.FireReceiver;
import com.company.module.fire.entity.FireReceiverInspection;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.FireReceiverInspectionRepository;
import com.company.module.fire.repository.FireReceiverRepository;
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
import java.nio.file.Paths;
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
@Transactional(readOnly = true)
public class FireReceiverService {

    private static final long DEFAULT_OUTDOOR_FLOOR_ID = 99L;
    private static final int MAX_INSPECTION_HISTORY = 12;
    private static final List<InspectionWorkbookExporter.ItemColumn> RECEIVER_EXPORT_COLUMNS = List.of(
            new InspectionWorkbookExporter.ItemColumn("power", "전원(전원 공급 및 전원표시등 \n정상여부 확인) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("switch", "스위치[스위치 정위치(자동) \n여부] [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("transfer_device", "절환장치(상용전원 OFF시 자동 예비전원 절환 여부) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("zone_map", "경계구역일람도(경계구역 일람도 적정여부) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("continuity_test", "도통시험(회로 단선여부) \n[점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("operation_test", "동작시험(주, 지구경종 및 시각경보기 작동상태) [점검결과]")
    );

    private final FireReceiverRepository fireReceiverRepository;
    private final FireReceiverInspectionRepository fireReceiverInspectionRepository;
    private final FloorRepository floorRepository;
    private final ObjectMapper objectMapper;


    public Page<FireReceiverResponse> getReceivers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("receiverId").ascending());
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return fireReceiverRepository.search(kw, pageable).map(receiver -> {
            FireReceiverResponse response = FireReceiverResponse.from(receiver);
            fireReceiverInspectionRepository
                    .findTopByReceiver_ReceiverIdOrderByInspectionDateDescInspectionIdDesc(receiver.getReceiverId())
                    .ifPresent(response::setLastInspection);
            return response;
        });
    }


    public FireReceiverResponse getReceiverDetail(Long receiverId) {
        FireReceiver receiver = fireReceiverRepository.findById(receiverId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiver", receiverId));

        FireReceiverResponse response = FireReceiverResponse.from(receiver);
        Pageable pageable = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<FireReceiverInspection> history = fireReceiverInspectionRepository
                .findByReceiver_ReceiverIdOrderByInspectionDateDescInspectionIdDesc(receiverId, pageable);

        if (!history.isEmpty()) {
            response.setLastInspection(history.get(0));
        }
        response.setInspectionHistory(history, history.stream().map(this::parseChecklist).toList());
        return response;
    }


    public byte[] exportInspectionWorkbook(Long receiverId, LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);

        FireReceiver receiver = fireReceiverRepository.findById(receiverId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiver", receiverId));
        List<FireReceiverInspection> inspections = fireReceiverInspectionRepository
                .findByReceiver_ReceiverIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
                        receiverId, fromDate, toDate);

        List<InspectionWorkbookExporter.RowData> rows = inspections.stream()
                .map(inspection -> toWorkbookRow(receiver, inspection))
                .toList();
        return InspectionWorkbookExporter.export("수신기 점검보고서", RECEIVER_EXPORT_COLUMNS, rows, this::resolveInspectionImage);
    }


    public byte[] exportAllInspectionWorkbook(LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);
        List<FireReceiverInspection> inspections = fireReceiverInspectionRepository
                .findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(fromDate, toDate);

        List<InspectionWorkbookExporter.RowData> rows = inspections.stream()
                .map(inspection -> toWorkbookRow(inspection.getReceiver(), inspection))
                .toList();
        return InspectionWorkbookExporter.export("수신기 점검보고서", RECEIVER_EXPORT_COLUMNS, rows, this::resolveInspectionImage);
    }

    @Transactional
    public FireReceiverResponse save(FireReceiverSaveRequest request) {
        Floor floor = floorRepository.findById(DEFAULT_OUTDOOR_FLOOR_ID)
                .orElseThrow(() -> new BusinessException("옥외 층 정보가 없습니다."));

        BigDecimal x = normalizeCoord(request.getX());
        BigDecimal y = normalizeCoord(request.getY());

        FireReceiver receiver;
        if (request.getReceiverId() != null && request.getReceiverId() > 0) {
            receiver = fireReceiverRepository.findById(request.getReceiverId())
                    .orElseThrow(() -> new EntityNotFoundException("FireReceiver", request.getReceiverId()));
            receiver.update(request.getBuildingName().trim(), floor, x, y,
                    request.getLocationDescription(), request.getNote());
        } else {
            receiver = FireReceiver.builder()
                    .serialNumber(generateNextSerialNumber())
                    .buildingName(request.getBuildingName().trim())
                    .floor(floor)
                    .x(x)
                    .y(y)
                    .locationDescription(request.getLocationDescription())
                    .note(request.getNote())
                    .isActive(true)
                    .build();
            fireReceiverRepository.save(receiver);
        }

        log.info("FireReceiver saved: id={}, serial={}", receiver.getReceiverId(), receiver.getSerialNumber());
        return FireReceiverResponse.from(receiver);
    }

    @Transactional
    public FireReceiverResponse inspect(Long receiverId, EquipmentInspectionRequest request,
                                        Long userId, String inspectorName) {
        FireReceiver receiver = fireReceiverRepository.findById(receiverId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiver", receiverId));

        if (fireReceiverInspectionRepository.existsByReceiver_ReceiverIdAndInspectionDate(receiverId, java.time.LocalDate.now())) {
            throw new BusinessException("오늘 이미 점검이 완료된 수신기입니다.");
        }

        String checklistJson = writeChecklist(request.getItems());
        String inspectionStatus = resolveInspectionStatus(request.getItems());
        Map<String, String> statusMap = toStatusMap(request.getItems());
        LocalTime inspectionTime = request.getInspectionTime() != null ? request.getInspectionTime() : LocalTime.now().withSecond(0).withNano(0);

        FireReceiverInspection inspection = FireReceiverInspection.builder()
                .receiver(receiver)
                .inspectionDate(java.time.LocalDate.now())
                .inspectionTime(inspectionTime)
                .inspectionStatus(inspectionStatus)
                .checklistJson(checklistJson)
                .note(trimToNull(request.getNote()))
                .powerStatus(statusMap.get("power"))
                .switchStatus(statusMap.get("switch"))
                .transferDeviceStatus(statusMap.get("transfer_device"))
                .zoneMapStatus(statusMap.get("zone_map"))
                .continuityTestStatus(statusMap.get("continuity_test"))
                .operationTestStatus(statusMap.get("operation_test"))
                .inspectedByUserId(userId)
                .inspectedByName(inspectorName)
                .build();
        fireReceiverInspectionRepository.save(inspection);
        fireReceiverInspectionRepository.trimInspectionsKeepLatest12(receiverId);

        return getReceiverDetail(receiverId);
    }

    @Transactional
    public void updateInspectionImagePath(Long receiverId, Long inspectionId, String imagePath) {
        FireReceiverInspection inspection = fireReceiverInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiverInspection", inspectionId));
        if (inspection.getReceiver() == null || !receiverId.equals(inspection.getReceiver().getReceiverId())) {
            throw new BusinessException("수신기 점검 이력이 올바르지 않습니다.");
        }
        inspection.updateImagePath(imagePath);
    }

    @Transactional
    public FireReceiverResponse updateInspection(Long receiverId, Long inspectionId, EquipmentInspectionUpdateRequest request) {
        FireReceiverInspection inspection = fireReceiverInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiverInspection", inspectionId));
        if (inspection.getReceiver() == null || !receiverId.equals(inspection.getReceiver().getReceiverId())) {
            throw new BusinessException("Inspection does not belong to this receiver.");
        }
        if (fireReceiverInspectionRepository.existsByReceiver_ReceiverIdAndInspectionDateAndInspectionIdNot(
                receiverId, request.getInspectionDate(), inspectionId)) {
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
                statusMap.get("power"),
                statusMap.get("switch"),
                statusMap.get("transfer_device"),
                statusMap.get("zone_map"),
                statusMap.get("continuity_test"),
                statusMap.get("operation_test")
        );
        return getReceiverDetail(receiverId);
    }

    @Transactional
    public FireReceiverResponse addInspection(Long receiverId, EquipmentInspectionUpdateRequest request) {
        FireReceiver receiver = fireReceiverRepository.findById(receiverId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiver", receiverId));
        if (fireReceiverInspectionRepository.existsByReceiver_ReceiverIdAndInspectionDate(receiverId, request.getInspectionDate())) {
            throw new BusinessException("An inspection already exists for the selected date.");
        }
        String checklistJson = writeChecklist(request.getItems());
        String inspectionStatus = resolveInspectionStatus(request.getItems());
        Map<String, String> statusMap = toStatusMap(request.getItems());
        String inspectorName = trimToNull(request.getInspectorName());
        if (inspectorName == null) {
            inspectorName = "관리자";
        }
        FireReceiverInspection inspection = FireReceiverInspection.builder()
                .receiver(receiver)
                .inspectionDate(request.getInspectionDate())
                .inspectionTime(request.getInspectionTime())
                .inspectionStatus(inspectionStatus)
                .checklistJson(checklistJson)
                .note(trimToNull(request.getNote()))
                .powerStatus(statusMap.get("power"))
                .switchStatus(statusMap.get("switch"))
                .transferDeviceStatus(statusMap.get("transfer_device"))
                .zoneMapStatus(statusMap.get("zone_map"))
                .continuityTestStatus(statusMap.get("continuity_test"))
                .operationTestStatus(statusMap.get("operation_test"))
                .inspectedByName(inspectorName)
                .build();
        fireReceiverInspectionRepository.save(inspection);
        fireReceiverInspectionRepository.trimInspectionsKeepLatest12(receiverId);
        return getReceiverDetail(receiverId);
    }

    @Transactional
    public FireReceiverResponse deleteInspection(Long receiverId, Long inspectionId) {
        FireReceiverInspection inspection = fireReceiverInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiverInspection", inspectionId));
        if (inspection.getReceiver() == null || !receiverId.equals(inspection.getReceiver().getReceiverId())) {
            throw new BusinessException("Inspection does not belong to this receiver.");
        }
        fireReceiverInspectionRepository.delete(inspection);
        return getReceiverDetail(receiverId);
    }

    @Transactional
    public void delete(Long receiverId) {
        FireReceiver receiver = fireReceiverRepository.findById(receiverId)
                .orElseThrow(() -> new EntityNotFoundException("FireReceiver", receiverId));
        fireReceiverRepository.delete(receiver);
    }

    private BigDecimal normalizeCoord(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateNextSerialNumber() {
        List<String> serials = fireReceiverRepository.findAllSerialNumbers();
        int maxNum = 0;
        for (String serial : serials) {
            try {
                maxNum = Math.max(maxNum, Integer.parseInt(serial.substring(4)));
            } catch (RuntimeException ignored) {
            }
        }
        return String.format("RCV-%06d", maxNum + 1);
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
            List<FireReceiverResponse.InspectionChecklistItem> mapped = items.stream()
                    .map(item -> new FireReceiverResponse.InspectionChecklistItem(
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

    private List<FireReceiverResponse.InspectionChecklistItem> parseChecklist(FireReceiverInspection inspection) {
        List<FireReceiverResponse.InspectionChecklistItem> fromColumns = buildChecklistFromColumns(inspection);
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
                    .map(item -> new FireReceiverResponse.InspectionChecklistItem(
                            trimToNull(item.getItemKey()),
                            trimToNull(item.getItemLabel()),
                            normalizeResult(item.getResult())))
                    .toList();
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse receiver inspection checklist: inspectionId={}", inspection.getInspectionId(), ex);
            return List.of();
        }
    }

    private List<FireReceiverResponse.InspectionChecklistItem> buildChecklistFromColumns(FireReceiverInspection inspection) {
        List<FireReceiverResponse.InspectionChecklistItem> items = new ArrayList<>();
        addChecklistItem(items, "power", "전원", inspection.getPowerStatus());
        addChecklistItem(items, "switch", "스위치", inspection.getSwitchStatus());
        addChecklistItem(items, "transfer_device", "절환장치", inspection.getTransferDeviceStatus());
        addChecklistItem(items, "zone_map", "경계구역일람도", inspection.getZoneMapStatus());
        addChecklistItem(items, "continuity_test", "도통시험", inspection.getContinuityTestStatus());
        addChecklistItem(items, "operation_test", "동작시험", inspection.getOperationTestStatus());
        return items;
    }

    private void addChecklistItem(List<FireReceiverResponse.InspectionChecklistItem> items, String itemKey, String itemLabel, String result) {
        String normalized = trimToNull(result);
        if (normalized == null) {
            return;
        }
        items.add(new FireReceiverResponse.InspectionChecklistItem(itemKey, itemLabel, normalized));
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

    private InspectionWorkbookExporter.RowData toWorkbookRow(FireReceiver receiver, FireReceiverInspection inspection) {
        String sectionTitle = receiver != null ? receiver.getBuildingName() : "수신기";
        return new InspectionWorkbookExporter.RowData(
                sectionTitle,
                inspection.getInspectionDate(),
                inspection.getInspectionTime(),
                inspection.getInspectedByName(),
                toItemResultMap(parseChecklist(inspection)),
                inspection.getImagePath(),
                inspection.getNote()
        );
    }

    private Map<String, String> toItemResultMap(List<FireReceiverResponse.InspectionChecklistItem> items) {
        Map<String, String> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (FireReceiverResponse.InspectionChecklistItem item : items) {
            String key = trimToNull(item.getItemKey());
            if (key != null) {
                result.put(key, item.getResult());
            }
        }
        return result;
    }

    private java.util.Optional<InspectionWorkbookExporter.ImageFile> resolveInspectionImage(String imagePath) {
        return InspectionWorkbookExporter.loadImage(imagePath, Paths.get("/data/upload/module_fire/receiver-inspections"));
    }
}
