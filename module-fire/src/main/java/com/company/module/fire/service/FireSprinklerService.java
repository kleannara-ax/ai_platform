package com.company.module.fire.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.fire.dto.EquipmentInspectionItemRequest;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FireSprinklerResponse;
import com.company.module.fire.dto.FireSprinklerSaveRequest;
import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.FireSprinkler;
import com.company.module.fire.entity.FireSprinklerInspection;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.BuildingRepository;
import com.company.module.fire.repository.FireSprinklerInspectionRepository;
import com.company.module.fire.repository.FireSprinklerRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FireSprinklerService {

    private static final int MAX_INSPECTION_HISTORY = 12;
    private static final int INSPECTION_REQUIRED_DAYS = 90;

    private static final List<InspectionWorkbookExporter.ItemColumn> SPRINKLER_EXPORT_COLUMNS = List.of(
            new InspectionWorkbookExporter.ItemColumn("pipe_damage", "배관 파손여부 확인(휘거나 찌그러짐) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("pipe_connection", "배관 연결부 상태 확인(플랜지, 나사부, 엘보 등) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("pipe_support", "배관 지지대 상태 확인(고정 및 풀림 확인) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("drain_valve", "드레인 벨브 누수 상태 확인(벨브 파손 및 잠금상태) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("drain_pipe_sealing", "드레인 배관 실리콘 마감상태 확인 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("head_reflector", "헤드 반사판 탈락여부 확인 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("product_clearance", "헤드로부터 제품 이격거리 60cm 이격거리 확보 여부 [점검결과]")
    );

    private final FireSprinklerRepository fireSprinklerRepository;
    private final FireSprinklerInspectionRepository fireSprinklerInspectionRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ObjectMapper objectMapper;

    public Page<FireSprinklerResponse> getSprinklers(Long buildingId, Long floorId, String keyword, int page, int size) {
        return getSprinklers(buildingId == null ? null : List.of(buildingId), floorId, keyword, page, size);
    }

    public Page<FireSprinklerResponse> getSprinklers(List<Long> buildingIds, Long floorId, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sprinklerId").ascending());
        List<Long> bIds = buildingIds == null ? null : buildingIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (bIds != null && bIds.isEmpty()) bIds = null;
        Long fId = (floorId != null && floorId > 0) ? floorId : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        return fireSprinklerRepository.search(bIds, fId, kw, pageable).map(sprinkler -> {
            FireSprinklerResponse response = FireSprinklerResponse.from(sprinkler);
            fireSprinklerInspectionRepository
                    .findTopBySprinkler_SprinklerIdOrderByInspectionDateDescInspectionIdDesc(sprinkler.getSprinklerId())
                    .ifPresentOrElse(
                            inspection -> response.setLastInspection(inspection, buildFaultReason(parseChecklist(inspection))),
                            () -> response.setLastInspection(null, null));
            response.setInspectionRequired(isInspectionRequired(response.getLastInspectionDate()));
            return response;
        });
    }

    public FireSprinklerResponse getSprinklerDetail(Long sprinklerId) {
        FireSprinkler sprinkler = fireSprinklerRepository.findById(sprinklerId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinkler", sprinklerId));

        FireSprinklerResponse response = FireSprinklerResponse.from(sprinkler);
        Pageable pageable = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<FireSprinklerInspection> history = fireSprinklerInspectionRepository
                .findBySprinkler_SprinklerIdOrderByInspectionDateDescInspectionIdDesc(sprinklerId, pageable);
        List<List<FireSprinklerResponse.InspectionChecklistItem>> parsed = history.stream().map(this::parseChecklist).toList();

        if (!history.isEmpty()) {
            response.setLastInspection(history.get(0), buildFaultReason(parsed.get(0)));
        }
        response.setInspectionRequired(isInspectionRequired(response.getLastInspectionDate()));
        response.setInspectionHistory(history, parsed);
        return response;
    }

    @Transactional
    public FireSprinklerResponse save(FireSprinklerSaveRequest request) {
        Building building = buildingRepository.findById(request.getBuildingId())
                .orElseThrow(() -> new BusinessException("건물 정보를 찾을 수 없습니다."));
        Floor floor = floorRepository.findById(request.getFloorId())
                .orElseThrow(() -> new BusinessException("층 정보를 찾을 수 없습니다."));

        BigDecimal x = normalizeCoord(request.getX());
        BigDecimal y = normalizeCoord(request.getY());

        FireSprinkler sprinkler;
        if (request.getSprinklerId() != null && request.getSprinklerId() > 0) {
            sprinkler = fireSprinklerRepository.findById(request.getSprinklerId())
                    .orElseThrow(() -> new EntityNotFoundException("FireSprinkler", request.getSprinklerId()));
            sprinkler.update(building, floor, x, y, trimToNull(request.getNote()));
            ensureQrKey(sprinkler);
        } else {
            sprinkler = FireSprinkler.builder()
                    .serialNumber(generateNextSerialNumber())
                    .building(building)
                    .floor(floor)
                    .x(x)
                    .y(y)
                    .note(trimToNull(request.getNote()))
                    .qrKey(generateUniqueQrKey())
                    .isActive(true)
                    .build();
            fireSprinklerRepository.save(sprinkler);
        }
        log.info("FireSprinkler saved: id={}, serial={}", sprinkler.getSprinklerId(), sprinkler.getSerialNumber());
        return getSprinklerDetail(sprinkler.getSprinklerId());
    }

    @Transactional
    public FireSprinklerResponse inspect(Long sprinklerId, EquipmentInspectionRequest request, Long userId, String inspectorName) {
        FireSprinkler sprinkler = fireSprinklerRepository.findById(sprinklerId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinkler", sprinklerId));
        LocalDate today = LocalDate.now();
        if (fireSprinklerInspectionRepository.existsBySprinkler_SprinklerIdAndInspectionDate(sprinklerId, today)) {
            throw new BusinessException("오늘 이미 점검이 완료된 스프링클러입니다.");
        }
        FireSprinklerInspection inspection = buildInspection(sprinkler, today,
                request.getInspectionTime() != null ? request.getInspectionTime() : LocalTime.now().withSecond(0).withNano(0),
                request.getItems(), request.getNote(), userId, inspectorName);
        fireSprinklerInspectionRepository.save(inspection);
        fireSprinklerInspectionRepository.trimInspectionsKeepLatest12(sprinklerId);
        return getSprinklerDetail(sprinklerId);
    }

    @Transactional
    public FireSprinklerResponse addInspection(Long sprinklerId, EquipmentInspectionUpdateRequest request) {
        if (request.getInspectionDate() == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        FireSprinkler sprinkler = fireSprinklerRepository.findById(sprinklerId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinkler", sprinklerId));
        if (fireSprinklerInspectionRepository.existsBySprinkler_SprinklerIdAndInspectionDate(sprinklerId, request.getInspectionDate())) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
        }
        String inspectorName = trimToNull(request.getInspectorName());
        FireSprinklerInspection inspection = buildInspection(sprinkler, request.getInspectionDate(), request.getInspectionTime(),
                request.getItems(), request.getNote(), null, inspectorName == null ? "관리자" : inspectorName);
        fireSprinklerInspectionRepository.save(inspection);
        fireSprinklerInspectionRepository.trimInspectionsKeepLatest12(sprinklerId);
        return getSprinklerDetail(sprinklerId);
    }

    @Transactional
    public FireSprinklerResponse updateInspection(Long sprinklerId, Long inspectionId, EquipmentInspectionUpdateRequest request) {
        if (request.getInspectionDate() == null) {
            throw new BusinessException("점검일은 필수입니다.");
        }
        FireSprinklerInspection inspection = fireSprinklerInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerInspection", inspectionId));
        if (inspection.getSprinkler() == null || !sprinklerId.equals(inspection.getSprinkler().getSprinklerId())) {
            throw new BusinessException("스프링클러와 점검 이력이 일치하지 않습니다.");
        }
        if (fireSprinklerInspectionRepository.existsBySprinkler_SprinklerIdAndInspectionDateAndInspectionIdNot(
                sprinklerId, request.getInspectionDate(), inspectionId)) {
            throw new BusinessException("해당 날짜의 점검 이력이 이미 존재합니다.");
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
                statusMap.get("pipe_damage"),
                statusMap.get("pipe_connection"),
                statusMap.get("pipe_support"),
                statusMap.get("drain_valve"),
                statusMap.get("drain_pipe_sealing"),
                statusMap.get("head_reflector"),
                statusMap.get("product_clearance")
        );
        return getSprinklerDetail(sprinklerId);
    }

    @Transactional
    public FireSprinklerResponse deleteInspection(Long sprinklerId, Long inspectionId) {
        FireSprinklerInspection inspection = fireSprinklerInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerInspection", inspectionId));
        if (inspection.getSprinkler() == null || !sprinklerId.equals(inspection.getSprinkler().getSprinklerId())) {
            throw new BusinessException("스프링클러와 점검 이력이 일치하지 않습니다.");
        }
        fireSprinklerInspectionRepository.delete(inspection);
        return getSprinklerDetail(sprinklerId);
    }

    @Transactional
    public void delete(Long sprinklerId) {
        FireSprinkler sprinkler = fireSprinklerRepository.findById(sprinklerId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinkler", sprinklerId));
        fireSprinklerRepository.delete(sprinkler);
    }

    @Transactional
    public void updateImagePath(Long sprinklerId, String imagePath) {
        FireSprinkler sprinkler = fireSprinklerRepository.findById(sprinklerId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinkler", sprinklerId));
        sprinkler.updateImagePath(imagePath);
    }

    public byte[] exportInspectionWorkbook(Long sprinklerId, LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);
        FireSprinkler sprinkler = fireSprinklerRepository.findById(sprinklerId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinkler", sprinklerId));
        List<FireSprinklerInspection> inspections = fireSprinklerInspectionRepository
                .findBySprinkler_SprinklerIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(sprinklerId, fromDate, toDate);
        List<InspectionWorkbookExporter.RowData> rows = inspections.stream()
                .map(inspection -> toWorkbookRow(sprinkler, inspection))
                .toList();
        return InspectionWorkbookExporter.export("스프링클러 점검보고서", SPRINKLER_EXPORT_COLUMNS, rows, imagePath -> java.util.Optional.empty());
    }

    public byte[] exportAllInspectionWorkbook(LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);
        List<FireSprinklerInspection> inspections = fireSprinklerInspectionRepository
                .findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(fromDate, toDate);
        List<InspectionWorkbookExporter.RowData> rows = inspections.stream()
                .map(inspection -> toWorkbookRow(inspection.getSprinkler(), inspection))
                .toList();
        return InspectionWorkbookExporter.export("스프링클러 점검보고서", SPRINKLER_EXPORT_COLUMNS, rows, imagePath -> java.util.Optional.empty());
    }

    private FireSprinklerInspection buildInspection(FireSprinkler sprinkler, LocalDate date, LocalTime time,
                                                    List<EquipmentInspectionItemRequest> items, String note,
                                                    Long userId, String inspectorName) {
        String checklistJson = writeChecklist(items);
        String inspectionStatus = resolveInspectionStatus(items);
        Map<String, String> statusMap = toStatusMap(items);
        return FireSprinklerInspection.builder()
                .sprinkler(sprinkler)
                .inspectionDate(date)
                .inspectionTime(time)
                .inspectionStatus(inspectionStatus)
                .checklistJson(checklistJson)
                .note(trimToNull(note))
                .pipeDamageStatus(statusMap.get("pipe_damage"))
                .pipeConnectionStatus(statusMap.get("pipe_connection"))
                .pipeSupportStatus(statusMap.get("pipe_support"))
                .drainValveStatus(statusMap.get("drain_valve"))
                .drainPipeSealingStatus(statusMap.get("drain_pipe_sealing"))
                .headReflectorStatus(statusMap.get("head_reflector"))
                .productClearanceStatus(statusMap.get("product_clearance"))
                .inspectedByUserId(userId)
                .inspectedByName(inspectorName)
                .build();
    }

    private boolean isInspectionRequired(LocalDate lastInspectionDate) {
        if (lastInspectionDate == null) {
            return true;
        }
        return ChronoUnit.DAYS.between(lastInspectionDate, LocalDate.now()) >= INSPECTION_REQUIRED_DAYS;
    }

    private BigDecimal normalizeCoord(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateNextSerialNumber() {
        List<String> serials = fireSprinklerRepository.findAllSerialNumbers();
        int maxNum = 0;
        for (String serial : serials) {
            try {
                maxNum = Math.max(maxNum, Integer.parseInt(serial.substring(4)));
            } catch (RuntimeException ignored) {
            }
        }
        return String.format("SPK-%06d", maxNum + 1);
    }

    private void ensureQrKey(FireSprinkler sprinkler) {
        if (sprinkler.getQrKey() == null || sprinkler.getQrKey().isBlank()) {
            sprinkler.assignQrKey(generateUniqueQrKey());
        }
    }

    private String generateUniqueQrKey() {
        String key;
        do {
            key = UUID.randomUUID().toString().replace("-", "");
        } while (fireSprinklerRepository.existsByQrKey(key));
        return key;
    }

    private String resolveInspectionStatus(List<EquipmentInspectionItemRequest> items) {
        for (EquipmentInspectionItemRequest item : items) {
            String result = normalizeResult(item.getResult());
            if ("FAULTY".equals(result)) {
                return "FAULTY";
            }
            if (!"NORMAL".equals(result)) {
                throw new BusinessException("점검 결과는 양호 또는 불량만 가능합니다.");
            }
        }
        return "NORMAL";
    }

    private String normalizeResult(String result) {
        String normalized = trimToNull(result);
        if (normalized == null) {
            return "";
        }
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "양호", "정상", "GOOD", "NORMAL" -> "NORMAL";
            case "불량", "FAULTY" -> "FAULTY";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private String writeChecklist(List<EquipmentInspectionItemRequest> items) {
        try {
            List<FireSprinklerResponse.InspectionChecklistItem> mapped = items.stream()
                    .map(item -> new FireSprinklerResponse.InspectionChecklistItem(
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
            if (key != null) {
                statusMap.put(key, normalizeResult(item.getResult()));
            }
        }
        return statusMap;
    }

    private List<FireSprinklerResponse.InspectionChecklistItem> parseChecklist(FireSprinklerInspection inspection) {
        List<FireSprinklerResponse.InspectionChecklistItem> fromColumns = buildChecklistFromColumns(inspection);
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
                    .map(item -> new FireSprinklerResponse.InspectionChecklistItem(
                            trimToNull(item.getItemKey()),
                            trimToNull(item.getItemLabel()),
                            normalizeResult(item.getResult())))
                    .toList();
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse sprinkler inspection checklist: inspectionId={}", inspection.getInspectionId(), ex);
            return List.of();
        }
    }

    private List<FireSprinklerResponse.InspectionChecklistItem> buildChecklistFromColumns(FireSprinklerInspection inspection) {
        List<FireSprinklerResponse.InspectionChecklistItem> items = new ArrayList<>();
        addChecklistItem(items, "pipe_damage", "배관 파손여부 확인(휘거나 찌그러짐)", inspection.getPipeDamageStatus());
        addChecklistItem(items, "pipe_connection", "배관 연결부 상태 확인(플랜지, 나사부, 엘보 등)", inspection.getPipeConnectionStatus());
        addChecklistItem(items, "pipe_support", "배관 지지대 상태 확인(고정 및 풀림 확인)", inspection.getPipeSupportStatus());
        addChecklistItem(items, "drain_valve", "드레인 벨브 누수 상태 확인(벨브 파손 및 잠금상태)", inspection.getDrainValveStatus());
        addChecklistItem(items, "drain_pipe_sealing", "드레인 배관 실리콘 마감상태 확인", inspection.getDrainPipeSealingStatus());
        addChecklistItem(items, "head_reflector", "헤드 반사판 탈락여부 확인", inspection.getHeadReflectorStatus());
        addChecklistItem(items, "product_clearance", "헤드로부터 제품 이격거리 60cm 이격거리 확보 여부", inspection.getProductClearanceStatus());
        return items;
    }

    private void addChecklistItem(List<FireSprinklerResponse.InspectionChecklistItem> items, String itemKey, String itemLabel, String result) {
        String normalized = trimToNull(result);
        if (normalized != null) {
            items.add(new FireSprinklerResponse.InspectionChecklistItem(itemKey, itemLabel, normalized));
        }
    }

    private String buildFaultReason(List<FireSprinklerResponse.InspectionChecklistItem> items) {
        if (items == null) {
            return null;
        }
        String reason = items.stream()
                .filter(item -> "FAULTY".equalsIgnoreCase(item.getResult()))
                .map(FireSprinklerResponse.InspectionChecklistItem::getItemLabel)
                .toList()
                .stream()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return reason.isBlank() ? null : reason;
    }

    private InspectionWorkbookExporter.RowData toWorkbookRow(FireSprinkler sprinkler, FireSprinklerInspection inspection) {
        String sectionTitle = sprinkler == null ? "스프링클러" : sprinkler.getSerialNumber() + " / "
                + (sprinkler.getBuilding() == null ? "" : sprinkler.getBuilding().getBuildingName()) + " "
                + (sprinkler.getFloor() == null ? "" : sprinkler.getFloor().getFloorName());
        return new InspectionWorkbookExporter.RowData(
                sectionTitle,
                inspection.getInspectionDate(),
                inspection.getInspectionTime(),
                inspection.getInspectedByName(),
                toItemResultMap(parseChecklist(inspection)),
                null,
                inspection.getNote()
        );
    }

    private Map<String, String> toItemResultMap(List<FireSprinklerResponse.InspectionChecklistItem> items) {
        Map<String, String> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (FireSprinklerResponse.InspectionChecklistItem item : items) {
            String key = trimToNull(item.getItemKey());
            if (key != null) {
                result.put(key, item.getResult());
            }
        }
        return result;
    }

    private void validateExportRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BusinessException("조회 시작일과 종료일을 입력해 주세요.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
