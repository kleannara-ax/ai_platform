package com.company.module.fire.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.fire.dto.EquipmentInspectionItemRequest;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FireSprinklerPipeResponse;
import com.company.module.fire.dto.FireSprinklerPipeSaveRequest;
import com.company.module.fire.entity.FireSprinklerPipe;
import com.company.module.fire.entity.FireSprinklerPipeInspection;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.FireSprinklerPipeInspectionRepository;
import com.company.module.fire.repository.FireSprinklerPipeRepository;
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
public class FireSprinklerPipeService {

    private static final long DEFAULT_OUTDOOR_FLOOR_ID = 99L;
    private static final int MAX_INSPECTION_HISTORY = 12;
    private static final List<InspectionWorkbookExporter.ItemColumn> SPRINKLER_PIPE_EXPORT_COLUMNS = List.of(
            new InspectionWorkbookExporter.ItemColumn("pipe_damage", "배관 파손여부 확인 (휘거나 찌그러짐) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("pipe_connection", "배관 연결부 상태 확인 (흔들림, 나사부, 외부 등) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("pipe_support", "배관 지지대 상태 확인 (고정 및 흔들 확인) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("drain_valve", "드레인 밸브 누수 상태 확인 (밸브 파손 및 잠금상태) [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("drain_pipe_sealing", "드레인 배관 실리콘 마감상태 확인 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("head_reflector", "헤드 반사판 탈락여부 확인 [점검결과]"),
            new InspectionWorkbookExporter.ItemColumn("product_clearance", "헤드로부터 제품 이격거리 60cm 이격거리 확보 여부 [점검결과]")
    );

    private final FireSprinklerPipeRepository fireSprinklerPipeRepository;
    private final FireSprinklerPipeInspectionRepository fireSprinklerPipeInspectionRepository;
    private final FloorRepository floorRepository;
    private final ObjectMapper objectMapper;


    public Page<FireSprinklerPipeResponse> getSprinklerPipes(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sprinklerPipeId").ascending());
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return fireSprinklerPipeRepository.search(kw, pageable).map(sprinklerPipe -> {
            FireSprinklerPipeResponse response = FireSprinklerPipeResponse.from(sprinklerPipe);
            fireSprinklerPipeInspectionRepository
                    .findTopBySprinklerPipe_SprinklerPipeIdOrderByInspectionDateDescInspectionIdDesc(sprinklerPipe.getSprinklerPipeId())
                    .ifPresent(response::setLastInspection);
            return response;
        });
    }


    public FireSprinklerPipeResponse getSprinklerPipeDetail(Long sprinklerPipeId) {
        FireSprinklerPipe sprinklerPipe = fireSprinklerPipeRepository.findById(sprinklerPipeId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipe", sprinklerPipeId));

        FireSprinklerPipeResponse response = FireSprinklerPipeResponse.from(sprinklerPipe);
        Pageable pageable = PageRequest.of(0, MAX_INSPECTION_HISTORY,
                Sort.by("inspectionDate").descending().and(Sort.by("inspectionId").descending()));
        List<FireSprinklerPipeInspection> history = fireSprinklerPipeInspectionRepository
                .findBySprinklerPipe_SprinklerPipeIdOrderByInspectionDateDescInspectionIdDesc(sprinklerPipeId, pageable);

        if (!history.isEmpty()) {
            response.setLastInspection(history.get(0));
        }
        response.setInspectionHistory(history, history.stream().map(this::parseChecklist).toList());
        return response;
    }


    public byte[] exportInspectionWorkbook(Long sprinklerPipeId, LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);

        FireSprinklerPipe sprinklerPipe = fireSprinklerPipeRepository.findById(sprinklerPipeId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipe", sprinklerPipeId));
        List<FireSprinklerPipeInspection> inspections = fireSprinklerPipeInspectionRepository
                .findBySprinklerPipe_SprinklerPipeIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
                        sprinklerPipeId, fromDate, toDate);

        List<InspectionWorkbookExporter.RowData> rows = new ArrayList<>();
        boolean latestForSprinklerPipe = true;
        for (FireSprinklerPipeInspection inspection : inspections) {
            rows.add(toWorkbookRow(sprinklerPipe, inspection, latestForSprinklerPipe));
            latestForSprinklerPipe = false;
        }
        return InspectionWorkbookExporter.export("스프링쿨러 배관 점검보고서", SPRINKLER_PIPE_EXPORT_COLUMNS, rows, this::resolveInspectionImage);
    }


    public byte[] exportAllInspectionWorkbook(LocalDate fromDate, LocalDate toDate) {
        validateExportRange(fromDate, toDate);
        List<FireSprinklerPipeInspection> inspections = fireSprinklerPipeInspectionRepository
                .findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(fromDate, toDate);

        Set<Long> sprinklerPipeIdsAlreadyExported = new HashSet<>();
        List<InspectionWorkbookExporter.RowData> rows = new ArrayList<>();
        for (FireSprinklerPipeInspection inspection : inspections) {
            FireSprinklerPipe sprinklerPipe = inspection.getSprinklerPipe();
            Long sprinklerPipeId = sprinklerPipe == null ? null : sprinklerPipe.getSprinklerPipeId();
            boolean latestForSprinklerPipe = sprinklerPipeId != null && sprinklerPipeIdsAlreadyExported.add(sprinklerPipeId);
            rows.add(toWorkbookRow(sprinklerPipe, inspection, latestForSprinklerPipe));
        }
        return InspectionWorkbookExporter.export("스프링쿨러 배관 점검보고서", SPRINKLER_PIPE_EXPORT_COLUMNS, rows, this::resolveInspectionImage);
    }

    @Transactional
    public FireSprinklerPipeResponse save(FireSprinklerPipeSaveRequest request) {
        Floor floor = floorRepository.findById(DEFAULT_OUTDOOR_FLOOR_ID)
                .orElseThrow(() -> new BusinessException("옥외 층 정보가 없습니다."));

        BigDecimal x = normalizeCoord(request.getX());
        BigDecimal y = normalizeCoord(request.getY());

        FireSprinklerPipe sprinklerPipe;
        if (request.getSprinklerPipeId() != null && request.getSprinklerPipeId() > 0) {
            sprinklerPipe = fireSprinklerPipeRepository.findById(request.getSprinklerPipeId())
                    .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipe", request.getSprinklerPipeId()));
            sprinklerPipe.update(request.getBuildingName().trim(), floor, x, y,
                    request.getLocationDescription(), request.getNote());
        } else {
            sprinklerPipe = FireSprinklerPipe.builder()
                    .serialNumber(generateNextSerialNumber())
                    .buildingName(request.getBuildingName().trim())
                    .floor(floor)
                    .x(x)
                    .y(y)
                    .locationDescription(request.getLocationDescription())
                    .note(request.getNote())
                    .isActive(true)
                    .build();
            fireSprinklerPipeRepository.save(sprinklerPipe);
        }

        log.info("FireSprinklerPipe saved: id={}, serial={}", sprinklerPipe.getSprinklerPipeId(), sprinklerPipe.getSerialNumber());
        return FireSprinklerPipeResponse.from(sprinklerPipe);
    }

    @Transactional
    public FireSprinklerPipeResponse inspect(Long sprinklerPipeId, EquipmentInspectionRequest request,
                                    Long userId, String inspectorName) {
        FireSprinklerPipe sprinklerPipe = fireSprinklerPipeRepository.findById(sprinklerPipeId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipe", sprinklerPipeId));

        if (fireSprinklerPipeInspectionRepository.existsBySprinklerPipe_SprinklerPipeIdAndInspectionDate(sprinklerPipeId, java.time.LocalDate.now())) {
            throw new BusinessException("오늘 이미 점검이 완료된 스프링쿨러 배관입니다.");
        }

        String checklistJson = writeChecklist(request.getItems());
        String inspectionStatus = resolveInspectionStatus(request.getItems());
        Map<String, String> statusMap = toStatusMap(request.getItems());
        LocalTime inspectionTime = request.getInspectionTime() != null ? request.getInspectionTime() : LocalTime.now().withSecond(0).withNano(0);

        FireSprinklerPipeInspection inspection = FireSprinklerPipeInspection.builder()
                .sprinklerPipe(sprinklerPipe)
                .inspectionDate(java.time.LocalDate.now())
                .inspectionTime(inspectionTime)
                .inspectionStatus(inspectionStatus)
                .checklistJson(checklistJson)
                .note(trimToNull(request.getNote()))
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
        fireSprinklerPipeInspectionRepository.save(inspection);
        fireSprinklerPipeInspectionRepository.trimInspectionsKeepLatest12(sprinklerPipeId);

        return getSprinklerPipeDetail(sprinklerPipeId);
    }

    @Transactional
    public void updateInspectionImagePath(Long sprinklerPipeId, Long inspectionId, String imagePath) {
        FireSprinklerPipeInspection inspection = fireSprinklerPipeInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipeInspection", inspectionId));
        if (inspection.getSprinklerPipe() == null || !sprinklerPipeId.equals(inspection.getSprinklerPipe().getSprinklerPipeId())) {
            throw new BusinessException("스프링쿨러 배관 점검 이력이 올바르지 않습니다.");
        }
        inspection.updateImagePath(imagePath);
        inspection.getSprinklerPipe().updateImagePath(imagePath);
    }

    @Transactional
    public void updateImagePath(Long sprinklerPipeId, String imagePath) {
        FireSprinklerPipe sprinklerPipe = fireSprinklerPipeRepository.findById(sprinklerPipeId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipe", sprinklerPipeId));
        sprinklerPipe.updateImagePath(imagePath);
    }

    @Transactional
    public FireSprinklerPipeResponse updateInspection(Long sprinklerPipeId, Long inspectionId, EquipmentInspectionUpdateRequest request) {
        FireSprinklerPipeInspection inspection = fireSprinklerPipeInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipeInspection", inspectionId));
        if (inspection.getSprinklerPipe() == null || !sprinklerPipeId.equals(inspection.getSprinklerPipe().getSprinklerPipeId())) {
            throw new BusinessException("Inspection does not belong to this sprinklerPipe.");
        }
        if (fireSprinklerPipeInspectionRepository.existsBySprinklerPipe_SprinklerPipeIdAndInspectionDateAndInspectionIdNot(
                sprinklerPipeId, request.getInspectionDate(), inspectionId)) {
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
                statusMap.get("pipe_damage"),
                statusMap.get("pipe_connection"),
                statusMap.get("pipe_support"),
                statusMap.get("drain_valve"),
                statusMap.get("drain_pipe_sealing"),
                statusMap.get("head_reflector"),
                statusMap.get("product_clearance")
        );
        return getSprinklerPipeDetail(sprinklerPipeId);
    }

    @Transactional
    public FireSprinklerPipeResponse addInspection(Long sprinklerPipeId, EquipmentInspectionUpdateRequest request) {
        FireSprinklerPipe sprinklerPipe = fireSprinklerPipeRepository.findById(sprinklerPipeId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipe", sprinklerPipeId));
        if (fireSprinklerPipeInspectionRepository.existsBySprinklerPipe_SprinklerPipeIdAndInspectionDate(sprinklerPipeId, request.getInspectionDate())) {
            throw new BusinessException("An inspection already exists for the selected date.");
        }
        String checklistJson = writeChecklist(request.getItems());
        String inspectionStatus = resolveInspectionStatus(request.getItems());
        Map<String, String> statusMap = toStatusMap(request.getItems());
        String inspectorName = trimToNull(request.getInspectorName());
        if (inspectorName == null) {
            inspectorName = "관리자";
        }
        FireSprinklerPipeInspection inspection = FireSprinklerPipeInspection.builder()
                .sprinklerPipe(sprinklerPipe)
                .inspectionDate(request.getInspectionDate())
                .inspectionTime(request.getInspectionTime())
                .inspectionStatus(inspectionStatus)
                .checklistJson(checklistJson)
                .note(trimToNull(request.getNote()))
                .pipeDamageStatus(statusMap.get("pipe_damage"))
                .pipeConnectionStatus(statusMap.get("pipe_connection"))
                .pipeSupportStatus(statusMap.get("pipe_support"))
                .drainValveStatus(statusMap.get("drain_valve"))
                .drainPipeSealingStatus(statusMap.get("drain_pipe_sealing"))
                .headReflectorStatus(statusMap.get("head_reflector"))
                .productClearanceStatus(statusMap.get("product_clearance"))
                .inspectedByName(inspectorName)
                .build();
        fireSprinklerPipeInspectionRepository.save(inspection);
        fireSprinklerPipeInspectionRepository.trimInspectionsKeepLatest12(sprinklerPipeId);
        return getSprinklerPipeDetail(sprinklerPipeId);
    }

    @Transactional
    public FireSprinklerPipeResponse deleteInspection(Long sprinklerPipeId, Long inspectionId) {
        FireSprinklerPipeInspection inspection = fireSprinklerPipeInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipeInspection", inspectionId));
        if (inspection.getSprinklerPipe() == null || !sprinklerPipeId.equals(inspection.getSprinklerPipe().getSprinklerPipeId())) {
            throw new BusinessException("Inspection does not belong to this sprinklerPipe.");
        }
        fireSprinklerPipeInspectionRepository.delete(inspection);
        return getSprinklerPipeDetail(sprinklerPipeId);
    }

    @Transactional
    public void delete(Long sprinklerPipeId) {
        FireSprinklerPipe sprinklerPipe = fireSprinklerPipeRepository.findById(sprinklerPipeId)
                .orElseThrow(() -> new EntityNotFoundException("FireSprinklerPipe", sprinklerPipeId));
        fireSprinklerPipeRepository.delete(sprinklerPipe);
    }

    private BigDecimal normalizeCoord(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateNextSerialNumber() {
        List<String> serials = fireSprinklerPipeRepository.findAllSerialNumbers();
        int maxNum = 0;
        for (String serial : serials) {
            try {
                maxNum = Math.max(maxNum, Integer.parseInt(serial.substring(4)));
            } catch (RuntimeException ignored) {
            }
        }
        return String.format("SPP-%06d", maxNum + 1);
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
            List<FireSprinklerPipeResponse.InspectionChecklistItem> mapped = items.stream()
                    .map(item -> new FireSprinklerPipeResponse.InspectionChecklistItem(
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

    private List<FireSprinklerPipeResponse.InspectionChecklistItem> parseChecklist(FireSprinklerPipeInspection inspection) {
        List<FireSprinklerPipeResponse.InspectionChecklistItem> fromColumns = buildChecklistFromColumns(inspection);
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
                    .map(item -> new FireSprinklerPipeResponse.InspectionChecklistItem(
                            trimToNull(item.getItemKey()),
                            trimToNull(item.getItemLabel()),
                            normalizeResult(item.getResult())))
                    .toList();
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse sprinklerPipe inspection checklist: inspectionId={}", inspection.getInspectionId(), ex);
            return List.of();
        }
    }

    private List<FireSprinklerPipeResponse.InspectionChecklistItem> buildChecklistFromColumns(FireSprinklerPipeInspection inspection) {
        List<FireSprinklerPipeResponse.InspectionChecklistItem> items = new ArrayList<>();
        addChecklistItem(items, "pipe_damage", "배관 파손여부 확인 (휘거나 찌그러짐)", inspection.getPipeDamageStatus());
        addChecklistItem(items, "pipe_connection", "배관 연결부 상태 확인 (흔들림, 나사부, 외부 등)", inspection.getPipeConnectionStatus());
        addChecklistItem(items, "pipe_support", "배관 지지대 상태 확인 (고정 및 흔들 확인)", inspection.getPipeSupportStatus());
        addChecklistItem(items, "drain_valve", "드레인 밸브 누수 상태 확인 (밸브 파손 및 잠금상태)", inspection.getDrainValveStatus());
        addChecklistItem(items, "drain_pipe_sealing", "드레인 배관 실리콘 마감상태 확인", inspection.getDrainPipeSealingStatus());
        addChecklistItem(items, "head_reflector", "헤드 반사판 탈락여부 확인", inspection.getHeadReflectorStatus());
        addChecklistItem(items, "product_clearance", "헤드로부터 제품 이격거리 60cm 이격거리 확보 여부", inspection.getProductClearanceStatus());
        return items;
    }

    private void addChecklistItem(List<FireSprinklerPipeResponse.InspectionChecklistItem> items, String itemKey, String itemLabel, String result) {
        String normalized = trimToNull(result);
        if (normalized == null) {
            return;
        }
        items.add(new FireSprinklerPipeResponse.InspectionChecklistItem(itemKey, itemLabel, normalized));
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

    private InspectionWorkbookExporter.RowData toWorkbookRow(FireSprinklerPipe sprinklerPipe,
                                                               FireSprinklerPipeInspection inspection,
                                                               boolean latestForSprinklerPipe) {
        String sectionTitle = sprinklerPipe != null ? sprinklerPipe.getBuildingName() : "스프링쿨러 배관";
        String imagePath = trimToNull(inspection.getImagePath());
        if (imagePath == null && latestForSprinklerPipe && sprinklerPipe != null) {
            imagePath = trimToNull(sprinklerPipe.getImagePath());
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

    private Map<String, String> toItemResultMap(List<FireSprinklerPipeResponse.InspectionChecklistItem> items) {
        Map<String, String> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (FireSprinklerPipeResponse.InspectionChecklistItem item : items) {
            String key = trimToNull(item.getItemKey());
            if (key != null) {
                result.put(key, item.getResult());
            }
        }
        return result;
    }

    private java.util.Optional<InspectionWorkbookExporter.ImageFile> resolveInspectionImage(String imagePath) {
        return InspectionWorkbookExporter.loadImage(imagePath, uploadDir("sprinkler-pipe-inspections"))
                .or(() -> InspectionWorkbookExporter.loadImage(imagePath, uploadDir("sprinkler-pipes")))
                .or(() -> InspectionWorkbookExporter.loadImage(imagePath, Paths.get("/data/upload/module_fire/sprinkler-pipe-inspections")))
                .or(() -> InspectionWorkbookExporter.loadImage(imagePath, Paths.get("/data/upload/module_fire/sprinkler-pipes")));
    }

    private static Path uploadDir(String child) {
        String root = System.getenv("MODULE_FIRE_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_fire").toString();
        }
        return Paths.get(root).resolve(child).normalize();
    }
}
