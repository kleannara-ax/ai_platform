package com.company.module.fire.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.core.common.response.ApiResponse;
import com.company.core.common.exception.BusinessException;
import com.company.module.fire.entity.*;
import com.company.module.fire.repository.*;
import com.company.module.fire.service.InspectorNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/fire-api/minspection")
@RequiredArgsConstructor
public class MobileInspectionController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final ExtinguisherRepository extinguisherRepository;
    private final FireHydrantRepository fireHydrantRepository;
    private final FireReceiverRepository fireReceiverRepository;
    private final FirePumpRepository firePumpRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ExtinguisherInspectionRepository extInspectionRepository;
    private final FireHydrantInspectionRepository hydInspectionRepository;
    private final FireReceiverInspectionRepository receiverInspectionRepository;
    private final FirePumpInspectionRepository pumpInspectionRepository;
    private final InspectorNameResolver inspectorNameResolver;
    private final ObjectMapper objectMapper;
    private static final String MSG_ERROR = "\uC694\uCCAD \uCC98\uB9AC \uC911 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC2B5\uB2C8\uB2E4.";
    private static final String MSG_NOT_FOUND = "\uC694\uCCAD\uD55C \uB370\uC774\uD130\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.";
    private static final String MSG_EMPTY_SERIAL = "QR \uD0A4\uAC00 \uBE44\uC5B4 \uC788\uC2B5\uB2C8\uB2E4.";

    @GetMapping("/extinguishers/by-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtByKey(
            @RequestParam String key) {

        String qrKey = key == null ? "" : key.trim();
        Optional<Extinguisher> opt = extinguisherRepository.findByNoteKey(qrKey);

        Map<String, Object> result = new LinkedHashMap<>();
        if (opt.isEmpty()) {
            result.put("exists", false);
            result.put("qrKey", qrKey);
            result.put("buildings", getBuildingList());
            result.put("floors", getFloorList());
            result.put("mapOptions", getMappableBuildingFloorList());
        } else {
            Extinguisher e = opt.get();
            result.put("exists", true);
            result.put("extinguisherId", e.getExtinguisherId());
            result.put("qrKey", e.getNoteKey());
            result.put("serialNumber", e.getSerialNumber());
            result.put("buildingName", e.getBuilding() != null ? e.getBuilding().getBuildingName() : "-");
            result.put("floorName", e.getFloor() != null ? e.getFloor().getFloorName() : "-");
            result.put("extinguisherType", e.getExtinguisherType());
            result.put("manufactureDate", e.getManufactureDate());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/extinguishers/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtById(@PathVariable Long id) {
        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extinguisherId", e.getExtinguisherId());
        result.put("serialNumber", e.getSerialNumber());
        result.put("buildingName", e.getBuilding() != null ? e.getBuilding().getBuildingName() : "-");
        result.put("floorName", e.getFloor() != null ? e.getFloor().getFloorName() : "-");
        result.put("extinguisherType", e.getExtinguisherType());
        result.put("manufactureDate", e.getManufactureDate());
        result.put("imagePath", e.getImagePath());
        result.put("quantity", e.getQuantity());
        result.put("note", e.getNote());

        extInspectionRepository
                .findTopByExtinguisher_ExtinguisherIdOrderByInspectionDateDescInspectionIdDesc(e.getExtinguisherId())
                .ifPresent(ins -> {
                    result.put("lastInspectionDate", ins.getInspectionDate());
                    result.put("lastInspectorName", ins.getInspectedByName());
                    result.put("lastIsFaulty", ins.isFaulty());
                    result.put("lastFaultReason", ins.getFaultReason());
                });

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/extinguishers/register")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerExtinguisher(
            @RequestBody Map<String, Object> body) {

        String qrKey = getString(body, "qrKey");
        if (qrKey == null || qrKey.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_EMPTY_SERIAL));
        }

        Optional<Extinguisher> existing = extinguisherRepository.findByNoteKey(qrKey.trim());
        if (existing.isPresent()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("extinguisherId", existing.get().getExtinguisherId());
            res.put("alreadyExists", true);
            return ResponseEntity.ok(ApiResponse.success(res));
        }

        Long buildingId = getLong(body, "buildingId");
        Long floorId = getLong(body, "floorId");
        String type = getString(body, "extinguisherType");
        String dateStr = getString(body, "manufactureDate");
        String inspectionDateStr = getString(body, "inspectionDate");
        String inspectorName = getString(body, "inspectorName");
        BigDecimal x = getBigDecimal(body, "x");
        BigDecimal y = getBigDecimal(body, "y");

        if (buildingId == null || buildingId <= 0)
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        if (floorId == null || floorId <= 0)
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        if (type == null || type.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        String strictPlanPath = resolvePlanImagePathStrict(building.getBuildingName(), floor.getFloorName());
        if (strictPlanPath == null || strictPlanPath.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        LocalDate manufactureDate = (dateStr != null && !dateStr.isBlank())
                ? LocalDate.parse(dateStr) : LocalDate.now();
        LocalDate inspectionDate = null;
        if (inspectionDateStr != null && !inspectionDateStr.isBlank()) {
            try {
                inspectionDate = LocalDate.parse(inspectionDateStr);
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
            }
        }

        int replacementYears = 10;

        Extinguisher entity = Extinguisher.builder()
                .serialNumber(generateNextExtinguisherSerialNumber())
                .building(building)
                .floor(floor)
                .extinguisherType(type.trim())
                .manufactureDate(manufactureDate)
                .replacementCycleYears(replacementYears)
                .quantity(1)
                .x(x)
                .y(y)
                .build();
        entity.assignNoteKey(qrKey.trim());

        extinguisherRepository.save(entity);
        if (inspectionDate != null) {
            String inspectedBy = (inspectorName == null || inspectorName.isBlank()) ? resolveInspectorName() : inspectorName.trim();
            ExtinguisherInspection inspection = ExtinguisherInspection.builder()
                    .extinguisher(entity)
                    .inspectionDate(inspectionDate)
                    .isFaulty(false)
                    .faultReason(null)
                    .inspectedByName(inspectedBy)
                    .build();
            extInspectionRepository.save(inspection);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("extinguisherId", entity.getExtinguisherId());
        res.put("qrKey", entity.getNoteKey());
        res.put("alreadyExists", false);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @PostMapping("/extinguishers/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectExtinguisher(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = resolveInspectorName();

        if (extInspectionRepository.existsByExtinguisher_ExtinguisherIdAndInspectionDate(id, LocalDate.now())) {
            throw new BusinessException("오늘 이미 점검을 완료했습니다.");
        }

        ExtinguisherInspection inspection = ExtinguisherInspection.builder()
                .extinguisher(e)
                .inspectionDate(LocalDate.now())
                .isFaulty(isFaulty)
                .faultReason(isFaulty ? faultReason : null)
                .inspectedByName(inspectorName)
                .build();

        extInspectionRepository.save(inspection);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/extinguishers/{id}/image")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadExtinguisherImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image file is empty."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image size must be <= 10MB."));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        Extinguisher e = extinguisherRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        try {
            Path dir = Paths.get("/data/upload/module_fire/extinguishers");
            Files.createDirectories(dir);

            String serial = e.getSerialNumber();
            if (serial == null || serial.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
            }

            // Remove old image referenced by DB path (if exists)
            String oldPath = e.getImagePath();
            if (oldPath != null && !oldPath.isBlank()) {
                String oldName = oldPath.substring(oldPath.lastIndexOf('/') + 1).replace("\\", "");
                if (!oldName.isBlank() && !oldName.contains("..")) {
                    Files.deleteIfExists(dir.resolve(oldName).normalize());
                }
            }

            String original = file.getOriginalFilename();
            String ext = "png";
            if (original != null) {
                int idx = original.lastIndexOf('.');
                if (idx > -1 && idx < original.length() - 1) {
                    String parsed = original.substring(idx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                    if (!parsed.isBlank()) ext = parsed;
                }
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = "/fire-api/minspection/files/extinguishers/" + filename;
            e.updateImagePath(publicPath);
            extinguisherRepository.save(e);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imagePath", publicPath);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail(MSG_ERROR));
        }
    }

    @GetMapping("/files/extinguishers/{filename:.+}")
    public ResponseEntity<Resource> getExtinguisherImageFile(@PathVariable String filename) {
        try {
            String clean = filename == null ? "" : filename.replace("\\", "/");
            if (clean.contains("..") || clean.contains("/")) {
                return ResponseEntity.badRequest().build();
            }
            Path base = Paths.get("/data/upload/module_fire/extinguishers").toAbsolutePath().normalize();
            Path file = base.resolve(clean).normalize();
            if (!file.startsWith(base) || !Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(file.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/extinguishers/mapdata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtMapData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        String buildingName = buildingRepository.findById(buildingId)
                .map(Building::getBuildingName).orElse("");
        String floorName = floorRepository.findById(floorId)
                .map(Floor::getFloorName).orElse("");

        String planImagePath = resolvePlanImagePath(buildingName, floorName);

        List<Map<String, Object>> items = extinguisherRepository.findForMap(buildingId, floorId)
                .stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("extinguisherId", e.getExtinguisherId());
                    m.put("x", e.getX());
                    m.put("y", e.getY());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("planImagePath", planImagePath);
        result.put("items", items);
        return ResponseEntity.ok(ApiResponse.success(result));
    }


    @GetMapping("/hydrants/by-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydByKey(
            @RequestParam String key) {

        String qrKey = key == null ? "" : key.trim();
        Optional<FireHydrant> opt = fireHydrantRepository.findByQrKey(qrKey);

        Map<String, Object> result = new LinkedHashMap<>();
        if (opt.isEmpty()) {
            result.put("exists", false);
            result.put("qrKey", qrKey);
            result.put("buildings", getBuildingList());
            result.put("floors", getFloorList());
            result.put("mapOptions", getMappableBuildingFloorList());
        } else {
            FireHydrant h = opt.get();
            result.put("exists", true);
            result.put("hydrantId", h.getHydrantId());
            result.put("qrKey", h.getQrKey());
            result.put("serialNumber", h.getSerialNumber());
            result.put("buildingName", h.getBuilding() != null ? h.getBuilding().getBuildingName() : "-");
            result.put("floorName", h.getFloor() != null ? h.getFloor().getFloorName() : "-");
            result.put("hydrantType", h.getHydrantType());
            result.put("operationType", h.getOperationType());
            result.put("locationDescription", h.getLocationDescription());
            result.put("imagePath", h.getImagePath());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/hydrants/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydById(@PathVariable Long id) {
        FireHydrant h = fireHydrantRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hydrantId", h.getHydrantId());
        result.put("serialNumber", h.getSerialNumber());
        result.put("buildingName", h.getBuilding() != null ? h.getBuilding().getBuildingName() : "-");
        result.put("floorName", h.getFloor() != null ? h.getFloor().getFloorName() : "-");
        result.put("hydrantType", h.getHydrantType());
        result.put("operationType", h.getOperationType());
        result.put("locationDescription", h.getLocationDescription());
        result.put("imagePath", h.getImagePath());
        result.put("isActive", h.isActive());

        hydInspectionRepository
                .findTopByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(h.getHydrantId())
                .ifPresent(ins -> {
                    result.put("lastInspectionDate", ins.getInspectionDate());
                    result.put("lastInspectorName", ins.getInspectedByName());
                    result.put("lastIsFaulty", ins.isFaulty());
                    result.put("lastFaultReason", ins.getFaultReason());
                });

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/hydrants/register")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerHydrant(
            @RequestBody Map<String, Object> body) {

        String qrKey = getString(body, "qrKey");
        if (qrKey == null || qrKey.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_EMPTY_SERIAL));
        }

        Optional<FireHydrant> existing = fireHydrantRepository.findByQrKey(qrKey.trim());
        if (existing.isPresent()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("hydrantId", existing.get().getHydrantId());
            res.put("alreadyExists", true);
            return ResponseEntity.ok(ApiResponse.success(res));
        }

        String hydrantType = getString(body, "hydrantType");
        String operationType = getString(body, "operationType");
        String locationDescription = getString(body, "locationDescription");
        BigDecimal x = getBigDecimal(body, "x");
        BigDecimal y = getBigDecimal(body, "y");

        if (hydrantType == null || (!hydrantType.equals("Indoor") && !hydrantType.equals("Outdoor")))
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        if (operationType == null || (!operationType.equals("Manual") && !operationType.equals("Auto")))
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));

        Long buildingId;
        Long floorId;

        if ("Outdoor".equals(hydrantType)) {
            buildingId = 99L;
            floorId = 1L;
        } else {
            buildingId = getLong(body, "buildingId");
            floorId = getLong(body, "floorId");
            if (buildingId == null || buildingId <= 0)
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
            if (floorId == null || floorId <= 0)
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        FireHydrant entity = FireHydrant.builder()
                .serialNumber(generateNextHydrantSerialNumber())
                .hydrantType(hydrantType)
                .operationType(operationType)
                .building(building)
                .floor(floor)
                .x(x)
                .y(y)
                .locationDescription(locationDescription)
                .isActive(true)
                .build();
        entity.assignQrKey(qrKey.trim());

        fireHydrantRepository.save(entity);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("hydrantId", entity.getHydrantId());
        res.put("qrKey", entity.getQrKey());
        res.put("alreadyExists", false);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @PostMapping("/hydrants/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectHydrant(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        FireHydrant h = fireHydrantRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        boolean isFaulty = Boolean.TRUE.equals(body.get("isFaulty"));
        String faultReason = getString(body, "faultReason");
        String inspectorName = getString(body, "inspectorName");
        if (inspectorName == null || inspectorName.isBlank()) inspectorName = resolveInspectorName();

        if (hydInspectionRepository.existsByHydrant_HydrantIdAndInspectionDate(id, LocalDate.now())) {
            throw new BusinessException("오늘 이미 점검을 완료했습니다.");
        }

        FireHydrantInspection inspection = FireHydrantInspection.builder()
                .hydrant(h)
                .inspectionDate(LocalDate.now())
                .isFaulty(isFaulty)
                .faultReason(isFaulty ? faultReason : null)
                .inspectedByName(inspectorName)
                .build();

        hydInspectionRepository.save(inspection);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/hydrants/{id}/image")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadHydrantImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image file is empty."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image size must be <= 10MB."));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        FireHydrant h = fireHydrantRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        try {
            Path dir = Paths.get("/data/upload/module_fire/hydrants");
            Files.createDirectories(dir);

            String serial = h.getSerialNumber();
            if (serial == null || serial.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
            }

            String oldPath = h.getImagePath();
            if (oldPath != null && !oldPath.isBlank()) {
                String oldName = oldPath.substring(oldPath.lastIndexOf('/') + 1).replace("\\", "");
                if (!oldName.isBlank() && !oldName.contains("..")) {
                    Files.deleteIfExists(dir.resolve(oldName).normalize());
                }
            }

            String original = file.getOriginalFilename();
            String ext = "png";
            if (original != null) {
                int extIdx = original.lastIndexOf('.');
                if (extIdx > -1 && extIdx < original.length() - 1) {
                    String parsed = original.substring(extIdx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                    if (!parsed.isBlank()) ext = parsed;
                }
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = "/fire-api/minspection/files/hydrants/" + filename;
            h.updateImagePath(publicPath);
            fireHydrantRepository.save(h);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imagePath", publicPath);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail(MSG_ERROR));
        }
    }

    @GetMapping("/files/hydrants/{filename:.+}")
    public ResponseEntity<Resource> getHydrantImageFile(@PathVariable String filename) {
        try {
            String clean = filename == null ? "" : filename.replace("\\", "/");
            if (clean.contains("..") || clean.contains("/")) {
                return ResponseEntity.badRequest().build();
            }
            Path base = Paths.get("/data/upload/module_fire/hydrants").toAbsolutePath().normalize();
            Path file = base.resolve(clean).normalize();
            if (!file.startsWith(base) || !Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(file.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
    @GetMapping("/hydrants/mapdata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHydMapData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        String buildingName = buildingRepository.findById(buildingId)
                .map(Building::getBuildingName).orElse("");
        String floorName = floorRepository.findById(floorId)
                .map(Floor::getFloorName).orElse("");

        String planImagePath = resolvePlanImagePath(buildingName, floorName);

        List<Map<String, Object>> items = fireHydrantRepository
                .findForMap("Indoor", buildingId, floorId)
                .stream().map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("hydrantId", h.getHydrantId());
                    m.put("x", h.getX());
                    m.put("y", h.getY());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("planImagePath", planImagePath);
        result.put("items", items);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/receivers/by-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReceiverByKey(@RequestParam String key) {
        String qrKey = key == null ? "" : key.trim();
        Optional<FireReceiver> opt = fireReceiverRepository.findByQrKey(qrKey);

        Map<String, Object> result = new LinkedHashMap<>();
        if (opt.isEmpty()) {
            result.put("exists", false);
            result.put("qrKey", qrKey);
        } else {
            FireReceiver receiver = opt.get();
            result.put("exists", true);
            result.put("receiverId", receiver.getReceiverId());
            result.put("qrKey", receiver.getQrKey());
            result.put("serialNumber", receiver.getSerialNumber());
            result.put("buildingName", receiver.getBuildingName());
            result.put("floorName", receiver.getFloor() != null ? receiver.getFloor().getFloorName() : "-");
            result.put("locationDescription", receiver.getLocationDescription());
            result.put("x", receiver.getX());
            result.put("y", receiver.getY());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/receivers/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReceiverById(@PathVariable Long id) {
        FireReceiver receiver = fireReceiverRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("receiverId", receiver.getReceiverId());
        result.put("serialNumber", receiver.getSerialNumber());
        result.put("buildingName", receiver.getBuildingName());
        result.put("floorName", receiver.getFloor() != null ? receiver.getFloor().getFloorName() : "-");
        result.put("locationDescription", receiver.getLocationDescription());
        result.put("x", receiver.getX());
        result.put("y", receiver.getY());

        receiverInspectionRepository
                .findTopByReceiver_ReceiverIdOrderByInspectionDateDescInspectionIdDesc(receiver.getReceiverId())
                .ifPresent(ins -> {
                    result.put("lastInspectionDate", ins.getInspectionDate());
                    result.put("lastInspectionTime", ins.getInspectionTime());
                    result.put("lastInspectionStatus", ins.getInspectionStatus());
                    result.put("lastInspectorName", ins.getInspectedByName());
                    result.put("lastNote", ins.getNote());
                    result.put("lastImagePath", ins.getImagePath());
                });

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/receivers/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectReceiver(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        FireReceiver receiver = fireReceiverRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        if (receiverInspectionRepository.existsByReceiver_ReceiverIdAndInspectionDate(id, LocalDate.now())) {
            throw new BusinessException("이미 오늘 점검이 등록된 수신기입니다.");
        }

        List<Map<String, String>> items = extractChecklistItems(body.get("items"));
        String inspectorName = trimToNull(getString(body, "inspectorName"));
        if (inspectorName == null) {
            inspectorName = resolveInspectorName();
        }
        String note = trimToNull(getString(body, "note"));
        Map<String, String> statusMap = toStatusMap(items);
        String inspectionStatus = hasFaulty(items) ? "FAULTY" : "NORMAL";

        FireReceiverInspection inspection = FireReceiverInspection.builder()
                .receiver(receiver)
                .inspectionDate(LocalDate.now())
                .inspectionTime(LocalTime.now().withSecond(0).withNano(0))
                .inspectionStatus(inspectionStatus)
                .checklistJson(writeChecklistJson(items))
                .note(note)
                .powerStatus(statusMap.get("power"))
                .switchStatus(statusMap.get("switch"))
                .transferDeviceStatus(statusMap.get("transfer_device"))
                .zoneMapStatus(statusMap.get("zone_map"))
                .continuityTestStatus(statusMap.get("continuity_test"))
                .operationTestStatus(statusMap.get("operation_test"))
                .inspectedByName(inspectorName)
                .build();

        receiverInspectionRepository.save(inspection);
        receiverInspectionRepository.trimInspectionsKeepLatest12(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/receivers/{id}/image")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadReceiverImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image file is empty."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image size must be <= 10MB."));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        fireReceiverRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        FireReceiverInspection inspection = receiverInspectionRepository
                .findTopByReceiver_ReceiverIdOrderByInspectionDateDescInspectionIdDesc(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        return uploadInspectionImage(file, Paths.get("/data/upload/module_fire/receiver-inspections"),
                "/fire-api/minspection/files/receivers/", inspection::updateImagePath, () -> {
                    receiverInspectionRepository.save(inspection);
                });
    }

    @GetMapping("/files/receivers/{filename:.+}")
    public ResponseEntity<Resource> getReceiverImageFile(@PathVariable String filename) {
        return serveInspectionImage("/data/upload/module_fire/receiver-inspections", filename);
    }

    @GetMapping("/pumps/by-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPumpByKey(@RequestParam String key) {
        String qrKey = key == null ? "" : key.trim();
        Optional<FirePump> opt = firePumpRepository.findByQrKey(qrKey);

        Map<String, Object> result = new LinkedHashMap<>();
        if (opt.isEmpty()) {
            result.put("exists", false);
            result.put("qrKey", qrKey);
        } else {
            FirePump pump = opt.get();
            result.put("exists", true);
            result.put("pumpId", pump.getPumpId());
            result.put("qrKey", pump.getQrKey());
            result.put("serialNumber", pump.getSerialNumber());
            result.put("buildingName", pump.getBuildingName());
            result.put("floorName", pump.getFloor() != null ? pump.getFloor().getFloorName() : "-");
            result.put("locationDescription", pump.getLocationDescription());
            result.put("x", pump.getX());
            result.put("y", pump.getY());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/pumps/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPumpById(@PathVariable Long id) {
        FirePump pump = firePumpRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pumpId", pump.getPumpId());
        result.put("serialNumber", pump.getSerialNumber());
        result.put("buildingName", pump.getBuildingName());
        result.put("floorName", pump.getFloor() != null ? pump.getFloor().getFloorName() : "-");
        result.put("locationDescription", pump.getLocationDescription());
        result.put("x", pump.getX());
        result.put("y", pump.getY());

        pumpInspectionRepository
                .findTopByPump_PumpIdOrderByInspectionDateDescInspectionIdDesc(pump.getPumpId())
                .ifPresent(ins -> {
                    result.put("lastInspectionDate", ins.getInspectionDate());
                    result.put("lastInspectionTime", ins.getInspectionTime());
                    result.put("lastInspectionStatus", ins.getInspectionStatus());
                    result.put("lastInspectorName", ins.getInspectedByName());
                    result.put("lastNote", ins.getNote());
                    result.put("lastImagePath", ins.getImagePath());
                });

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/pumps/{id}/inspect")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> inspectPump(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        FirePump pump = firePumpRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        if (pumpInspectionRepository.existsByPump_PumpIdAndInspectionDate(id, LocalDate.now())) {
            throw new BusinessException("이미 오늘 점검이 등록된 소방펌프입니다.");
        }

        List<Map<String, String>> items = extractChecklistItems(body.get("items"));
        String inspectorName = trimToNull(getString(body, "inspectorName"));
        if (inspectorName == null) {
            inspectorName = resolveInspectorName();
        }
        String note = trimToNull(getString(body, "note"));
        Map<String, String> statusMap = toStatusMap(items);
        String inspectionStatus = hasFaulty(items) ? "FAULTY" : "NORMAL";

        FirePumpInspection inspection = FirePumpInspection.builder()
                .pump(pump)
                .inspectionDate(LocalDate.now())
                .inspectionTime(LocalTime.now().withSecond(0).withNano(0))
                .inspectionStatus(inspectionStatus)
                .checklistJson(writeChecklistJson(items))
                .note(note)
                .pumpOperationStatus(statusMap.get("pump_operation"))
                .panelStatus(statusMap.get("panel"))
                .waterSupplyStatus(statusMap.get("water_supply"))
                .fuelStatus(statusMap.get("fuel"))
                .drainPumpStatus(statusMap.get("drain_pump"))
                .pipingStatus(statusMap.get("piping"))
                .inspectedByName(inspectorName)
                .build();

        pumpInspectionRepository.save(inspection);
        pumpInspectionRepository.trimInspectionsKeepLatest12(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/pumps/{id}/image")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadPumpImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image file is empty."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image size must be <= 10MB."));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(MSG_ERROR));
        }

        firePumpRepository.findById(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        FirePumpInspection inspection = pumpInspectionRepository
                .findTopByPump_PumpIdOrderByInspectionDateDescInspectionIdDesc(id)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(MSG_NOT_FOUND));

        return uploadInspectionImage(file, Paths.get("/data/upload/module_fire/pump-inspections"),
                "/fire-api/minspection/files/pumps/", inspection::updateImagePath, () -> {
                    pumpInspectionRepository.save(inspection);
                });
    }

    @GetMapping("/files/pumps/{filename:.+}")
    public ResponseEntity<Resource> getPumpImageFile(@PathVariable String filename) {
        return serveInspectionImage("/data/upload/module_fire/pump-inspections", filename);
    }


    private List<Map<String, Object>> getBuildingList() {
        return buildingRepository.findByActiveTrueOrderByBuildingName().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("buildingId", b.getBuildingId());
            m.put("buildingName", b.getBuildingName());
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> getFloorList() {
        return floorRepository.findAllByOrderBySortOrderAsc().stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("floorId", f.getFloorId());
            m.put("floorName", f.getFloorName());
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> getMappableBuildingFloorList() {
        List<Building> buildings = new ArrayList<>(buildingRepository.findAll());
        buildings.sort(Comparator.comparing(b -> String.valueOf(b.getBuildingName())));
        List<Floor> floors = floorRepository.findAllByOrderBySortOrderAsc();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Building b : buildings) {
            String bName = b.getBuildingName();
            String bn = bName == null ? "" : bName.toLowerCase();
            if (bn.contains("\uC625\uC678") || bn.contains("outdoor")) continue;
            for (Floor f : floors) {
                String plan = resolvePlanImagePathStrict(bName, f.getFloorName());
                if (plan == null || plan.isBlank()) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("buildingId", b.getBuildingId());
                m.put("buildingName", b.getBuildingName());
                m.put("floorId", f.getFloorId());
                m.put("floorName", f.getFloorName());
                m.put("planImagePath", plan);
                result.add(m);
            }
        }
        return result;
    }

    private String resolvePlanImagePathStrict(String buildingName, String floorName) {
        String b = buildingName == null ? "" : buildingName.trim().toLowerCase();
        String f = floorName == null ? "" : floorName.trim().toLowerCase();
        String bn = b.replaceAll("[\\s,._-]", "");
        boolean b1 = isBasementFloor(f);
        int floorNo = parseFloorNumber(f);

        if (b.contains("\uBCF5\uC9C0\uAD00") || b.contains("bokji")) {
            if (b1) return "/images/bokji_B1.png";
            if (floorNo == 1) return "/images/bokji_1F.png";
            if (floorNo == 2) return "/images/bokji_2F.png";
            if (floorNo == 3) return "/images/bokji_3F.png";
            return null;
        }
        if (b.contains("\uAD00\uB9AC\uB3D9") || b.contains("gwanri")) {
            if (floorNo == 2) return "/images/gwanri_2F.PNG";
            if (floorNo == 1) return "/images/gwanri_1F.png";
            return null;
        }
        if (bn.contains("\uC81C\uC9C012\uD638\uAE30")
                || bn.contains("jeji12")
                || bn.contains("\uC81C\uC9C012")
                || bn.contains("\uC81C\uC9C02\uD638\uAE30")
                || bn.contains("jeji2")
                || (bn.contains("\uC81C\uC9C01\uD638\uAE30") && bn.contains("2\uD638\uAE30"))) {
            if (floorNo == 1) return "/images/jeji1,2_1F.PNG";
            if (floorNo == 2) return "/images/jeji1,2_2F.PNG";
            return null;
        }
        if (bn.contains("\uC81C\uC9C03\uD638\uAE30") || bn.contains("jeji3")) {
            if (floorNo == 1) return "/images/jeji3_1F.PNG";
            if (floorNo == 2) return "/images/jeji3_2F.PNG";
            return null;
        }
        if (bn.contains("\uC2EC\uBA74\uD384\uD37C")
                || bn.contains("\uC2EC\uBA74\uD384\uD504")
                || (bn.contains("\uC2EC\uBA74") && (bn.contains("\uD384\uD37C") || bn.contains("\uD384\uD504")))
                || bn.contains("palpa")
                || bn.contains("pulper")) {
            if (floorNo == 1) return "/images/palpa_1F.PNG";
            if (floorNo == 2) return "/images/palpa_2F.PNG";
            return null;
        }
        if (bn.contains("\uD328\uB4DC\uB3D9") || bn.contains("pad")) {
            if (floorNo == 1) return "/images/pad_1F.PNG";
            if (floorNo == 2) return "/images/pad_2F.PNG";
            return null;
        }
        if (bn.contains("\uD654\uC7A5\uC9C036\uD638\uAE30")
                || bn.contains("tissue36")) {
            if (floorNo == 1) return "/images/tissue1,3_1F.PNG";
            if (floorNo == 2) return "/images/tissue1,3_2F.PNG";
            return null;
        }
        if (bn.contains("\uD654\uC7A5\uC9C045\uD638\uAE30") || bn.contains("tissue45")) {
            if (b1) return "/images/tissue4,5_B1.PNG";
            if (floorNo == 1) return "/images/tissue4,5_1F.PNG";
            if (floorNo == 2) return "/images/tissue4,5_2F.PNG";
            if (floorNo == 3) return "/images/tissue4,5_3F.PNG";
            return null;
        }
        if (bn.contains("\uAE30\uC800\uADC0\uB3D9")
                || bn.contains("\uAE30\uC800\uADC0")
                || bn.contains("diaper")) {
            if (floorNo == 1) return "/images/diaper_1F.png";
            return null;
        }
        return null;
    }

    private boolean isBasementFloor(String floorName) {
        String f = floorName == null ? "" : floorName.toLowerCase().replaceAll("\\s+", "");
        return f.contains("\uC9C0\uD558") || f.contains("b1");
    }

    private int parseFloorNumber(String floorName) {
        if (isBasementFloor(floorName)) return -1;
        String f = floorName == null ? "" : floorName.toLowerCase().replaceAll("\\s+", "");
        if (f.contains("1\uCE35") || f.equals("1") || f.equals("1f") || f.equals("f1")) return 1;
        if (f.contains("2\uCE35") || f.equals("2") || f.equals("2f") || f.equals("f2")) return 2;
        if (f.contains("3\uCE35") || f.equals("3") || f.equals("3f") || f.equals("f3")) return 3;
        return -1;
    }

    private String resolvePlanImagePath(String buildingName, String floorName) {
        String b = buildingName == null ? "" : buildingName.trim().toLowerCase();
        String f = floorName == null ? "" : floorName.trim().toLowerCase();
        String bn = b.replaceAll("[\\s,._-]", "");

        if (b.contains("\uBCF5\uC9C0\uAD00") || b.contains("bokji")) {
            if (f.contains("\uC9C0\uD558") || f.contains("b1")) return "/images/bokji_B1.png";
            if (f.contains("2")) return "/images/bokji_2F.png";
            if (f.contains("1")) return "/images/bokji_1F.png";
            if (f.contains("3")) return "/images/bokji_3F.png";
        }
        if (b.contains("\uAD00\uB9AC\uB3D9") || b.contains("gwanri")) {
            if (f.contains("2")) return "/images/gwanri_2F.PNG";
            if (f.contains("1")) return "/images/gwanri_1F.png";
        }
        if (b.contains("\uC625\uC678") || b.contains("outdoor")) {
            return "/images/drone_photo.JPG";
        }
        if (bn.contains("\uC81C\uC9C012\uD638\uAE30")
                || bn.contains("jeji12")
                || bn.contains("\uC81C\uC9C012")
                || bn.contains("\uC81C\uC9C02\uD638\uAE30")
                || bn.contains("jeji2")
                || (bn.contains("\uC81C\uC9C01\uD638\uAE30") && bn.contains("2\uD638\uAE30"))) {
            if (f.contains("2")) return "/images/jeji1,2_2F.PNG";
            return "/images/jeji1,2_1F.PNG";
        }
        if (bn.contains("\uC81C\uC9C03\uD638\uAE30") || bn.contains("jeji3")) {
            if (f.contains("2")) return "/images/jeji3_2F.PNG";
            return "/images/jeji3_1F.PNG";
        }
        if (bn.contains("\uC2EC\uBA74\uD384\uD37C")
                || bn.contains("\uC2EC\uBA74\uD384\uD504")
                || (bn.contains("\uC2EC\uBA74") && (bn.contains("\uD384\uD37C") || bn.contains("\uD384\uD504")))
                || bn.contains("palpa")
                || bn.contains("pulper")) {
            if (f.contains("2")) return "/images/palpa_2F.PNG";
            return "/images/palpa_1F.PNG";
        }
        if (bn.contains("\uD328\uB4DC\uB3D9") || bn.contains("pad")) {
            if (f.contains("2")) return "/images/pad_2F.PNG";
            return "/images/pad_1F.PNG";
        }
        if (bn.contains("\uD654\uC7A5\uC9C036\uD638\uAE30") || bn.contains("tissue36")) {
            if (f.contains("2")) return "/images/tissue1,3_2F.PNG";
            return "/images/tissue1,3_1F.PNG";
        }
        if (bn.contains("\uD654\uC7A5\uC9C045\uD638\uAE30") || bn.contains("tissue45")) {
            if (f.contains("\uC9C0\uD558") || f.contains("b1")) return "/images/tissue4,5_B1.PNG";
            if (f.contains("3")) return "/images/tissue4,5_3F.PNG";
            if (f.contains("2")) return "/images/tissue4,5_2F.PNG";
            return "/images/tissue4,5_1F.PNG";
        }
        if (bn.contains("\uAE30\uC800\uADC0\uB3D9")
                || bn.contains("\uAE30\uC800\uADC0")
                || bn.contains("diaper")) {
            return "/images/diaper_1F.png";
        }
        return "/images/bokji_1F.png";
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> uploadInspectionImage(
            MultipartFile file,
            Path dir,
            String publicPrefix,
            java.util.function.Consumer<String> imageUpdater,
            Runnable saver) {

        try {
            Files.createDirectories(dir);

            String original = file.getOriginalFilename();
            String ext = "png";
            if (original != null) {
                int idx = original.lastIndexOf('.');
                if (idx > -1 && idx < original.length() - 1) {
                    String parsed = original.substring(idx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                    if (!parsed.isBlank()) {
                        ext = parsed;
                    }
                }
            }

            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = publicPrefix + filename;
            imageUpdater.accept(publicPath);
            saver.run();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imagePath", publicPath);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail(MSG_ERROR));
        }
    }

    private ResponseEntity<Resource> serveInspectionImage(String baseDir, String filename) {
        try {
            String clean = filename == null ? "" : filename.replace("\\", "/");
            if (clean.contains("..") || clean.contains("/")) {
                return ResponseEntity.badRequest().build();
            }
            Path base = Paths.get(baseDir).toAbsolutePath().normalize();
            Path file = base.resolve(clean).normalize();
            if (!file.startsWith(base) || !Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(file.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private List<Map<String, String>> extractChecklistItems(Object rawItems) {
        if (!(rawItems instanceof List<?> rawList)) {
            return Collections.emptyList();
        }

        List<Map<String, String>> items = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String key = trimToNull(String.valueOf(rawMap.get("key")));
            String label = trimToNull(String.valueOf(rawMap.get("label")));
            String result = trimToNull(String.valueOf(rawMap.get("result")));
            if (key == null || result == null) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", label);
            item.put("result", result.toUpperCase(Locale.ROOT));
            items.add(item);
        }
        return items;
    }

    private boolean hasFaulty(List<Map<String, String>> items) {
        return items.stream().anyMatch(item -> "FAULTY".equalsIgnoreCase(item.get("result")));
    }

    private Map<String, String> toStatusMap(List<Map<String, String>> items) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, String> item : items) {
            String key = trimToNull(item.get("key"));
            String status = trimToNull(item.get("result"));
            if (key != null && status != null) {
                result.put(key, status.toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private String writeChecklistJson(List<Map<String, String>> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("점검 체크리스트 저장에 실패했습니다.");
        }
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString().trim();
    }

    private Long getLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private BigDecimal getBigDecimal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private String generateNextExtinguisherSerialNumber() {
        int max = extinguisherRepository.findAll().stream()
                .map(Extinguisher::getSerialNumber)
                .filter(s -> s != null && s.startsWith("EXT-"))
                .mapToInt(s -> {
                    try {
                        return Integer.parseInt(s.substring(4));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        return String.format("EXT-%06d", max + 1);
    }

    private String generateNextHydrantSerialNumber() {
        int max = fireHydrantRepository.findAllSerialNumbers().stream()
                .mapToInt(s -> {
                    try {
                        return Integer.parseInt(s.substring(4));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        return String.format("HYD-%06d", max + 1);
    }

    private String resolveInspectorName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String name = auth.getName();
                if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
                    return inspectorNameResolver.resolveDisplayName(name);
                }
            }
        } catch (Exception ignored) {
        }
        return "\uBAA8\uBC14\uC77CQR";
    }
}

