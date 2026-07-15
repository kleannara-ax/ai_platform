package com.company.module.fire.facility;

import com.company.core.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/facility-api/mobile")
@RequiredArgsConstructor
public class FacilityMobileQrController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final FacilityEquipmentRepository equipmentRepository;
    private final FacilityEquipmentService facilityEquipmentService;
    private final FacilityAirconFaultReportRepository airconFaultReportRepository;
    private final FacilityWaterDisinfectionRepository waterDisinfectionRepository;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
    }

    @GetMapping("/air-conditioners/by-key")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAirConditionerByKey(@RequestParam String key) {
        FacilityEquipment equipment = findByQrKey(FacilityEquipmentService.CATEGORY_AIRCON, key);
        Map<String, Object> result = equipmentDetail(equipment);
        result.put("recentFaultReports", airconFaultReportRepository
                .findTop5ByEquipment_EquipmentIdOrderByCreatedAtDescReportIdDesc(equipment.getEquipmentId())
                .stream()
                .map(this::faultReportRow)
                .toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/water-purifiers/by-key")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWaterPurifierByKey(@RequestParam String key) {
        FacilityEquipment equipment = findByQrKey(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, key);
        Map<String, Object> result = equipmentDetail(equipment);
        result.put("recentDisinfections", waterDisinfectionRepository
                .findTop5ByEquipment_EquipmentIdOrderByDisinfectionDateDescDisinfectionIdDesc(equipment.getEquipmentId())
                .stream()
                .map(this::disinfectionRow)
                .toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }


    @PostMapping(value = "/air-conditioners/{qrKey}/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerAirConditioner(
            @PathVariable String qrKey,
            @RequestParam(required = false) String serialNumber,
            @RequestParam Long buildingId,
            @RequestParam Long floorId,
            @RequestParam String equipmentType,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String locationDescription,
            @RequestParam(defaultValue = "1") int outdoorUnitCount,
            @RequestParam(required = false) String manufactureDate,
            @RequestParam(required = false) BigDecimal x,
            @RequestParam(required = false) BigDecimal y,
            @RequestParam(required = false) MultipartFile photo) {
        FacilityEquipmentSaveRequest request = baseRegisterRequest(serialNumber, buildingId, floorId, manufactureDate, x, y);
        request.setEquipmentType(equipmentType);
        request.setManufacturer(manufacturer);
        request.setLocationDescription(locationDescription);
        request.setOutdoorUnitCount(outdoorUnitCount);
        request.setReplacementCycleYears(10);
        return registerFacilityEquipment(FacilityEquipmentService.CATEGORY_AIRCON, "air-conditioners", qrKey, request, photo);
    }

    @PostMapping(value = "/water-purifiers/{qrKey}/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerWaterPurifier(
            @PathVariable String qrKey,
            @RequestParam(required = false) String serialNumber,
            @RequestParam Long buildingId,
            @RequestParam Long floorId,
            @RequestParam(required = false) String manufactureDate,
            @RequestParam(required = false) BigDecimal x,
            @RequestParam(required = false) BigDecimal y,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) MultipartFile photo) {
        FacilityEquipmentSaveRequest request = baseRegisterRequest(serialNumber, buildingId, floorId, manufactureDate, x, y);
        request.setEquipmentType("정수기");
        request.setNote(note);
        request.setReplacementCycleYears(10);
        return registerFacilityEquipment(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, "water-purifiers", qrKey, request, photo);
    }

    @PostMapping(value = "/air-conditioners/{qrKey}/fault-reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitAirconFaultReport(
            @PathVariable String qrKey,
            @RequestParam(required = false) String reporterName,
            @RequestParam(required = false) String inspectionResult,
            @RequestParam(required = false) String faultDescription) {
        FacilityEquipment equipment = findByQrKey(FacilityEquipmentService.CATEGORY_AIRCON, qrKey);
        String inspector = trimToNull(reporterName);
        if (inspector == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("점검자 이름을 입력하세요."));
        }
        String resultValue = trimToNull(inspectionResult);
        if (resultValue == null) {
            resultValue = trimToNull(faultDescription);
        }
        if (!Set.of("정상", "비정상").contains(resultValue)) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("에어컨 작동 상태를 정상 또는 비정상으로 선택하세요."));
        }
        FacilityAirconFaultReport report = airconFaultReportRepository.save(FacilityAirconFaultReport.builder()
                .equipment(equipment)
                .reporterName(inspector)
                .reporterDepartment(null)
                .faultDescription(resultValue)
                .status("정상".equals(resultValue) ? "NORMAL" : "ABNORMAL")
                .build());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", report.getReportId());
        result.put("inspectionResult", report.getFaultDescription());
        result.put("status", report.getStatus());
        result.put("createdAt", report.getCreatedAt());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/water-purifiers/{qrKey}/disinfections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitWaterDisinfection(
            @PathVariable String qrKey,
            @RequestParam String workerName,
            @RequestParam(required = false) String inspectionResult) {
        FacilityEquipment equipment = findByQrKey(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, qrKey);
        String worker = trimToNull(workerName);
        if (worker == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("점검자 이름을 입력하세요."));
        }
        String resultValue = trimToNull(inspectionResult);
        if (!Set.of("완료", "미완료").contains(resultValue)) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("정수기 점검 상태를 완료 또는 미완료로 선택하세요."));
        }
        FacilityWaterDisinfection disinfection = waterDisinfectionRepository.save(FacilityWaterDisinfection.builder()
                .equipment(equipment)
                .disinfectionDate(LocalDate.now())
                .workerName(worker)
                .note(resultValue)
                .photoPath("")
                .build());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("disinfectionId", disinfection.getDisinfectionId());
        result.put("inspectionResult", disinfection.getNote());
        result.put("disinfectionDate", disinfection.getDisinfectionDate());
        result.put("createdAt", disinfection.getCreatedAt());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/files/{kind}/{filename:.+}")
    public ResponseEntity<Resource> getMobileImage(@PathVariable String kind, @PathVariable String filename) {
        if (!Set.of("aircon-faults", "water-disinfections").contains(kind)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String clean = filename == null ? "" : filename.replace("\\", "/");
            if (clean.contains("..") || clean.contains("/")) {
                return ResponseEntity.badRequest().build();
            }
            Path base = uploadDir(kind).toAbsolutePath().normalize();
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

    private FacilityEquipment findByQrKey(String category, String key) {
        String qrKey = trimToNull(key);
        if (qrKey == null) {
            throw new IllegalArgumentException("QR 키가 비어 있습니다.");
        }
        FacilityEquipment equipment = equipmentRepository.findByQrKey(qrKey)
                .orElseThrow(() -> new IllegalArgumentException("QR에 연결된 설비를 찾을 수 없습니다."));
        if (!category.equals(equipment.getCategory())) {
            throw new IllegalArgumentException("QR 설비 유형이 일치하지 않습니다.");
        }
        return equipment;
    }

    private Map<String, Object> equipmentDetail(FacilityEquipment equipment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("equipmentId", equipment.getEquipmentId());
        result.put("category", equipment.getCategory());
        result.put("serialNumber", equipment.getSerialNumber());
        result.put("equipmentCode", equipment.getEquipmentCode());
        result.put("qrKey", equipment.getQrKey());
        result.put("buildingName", equipment.getBuilding() != null ? equipment.getBuilding().getBuildingName() : "-");
        result.put("floorName", equipment.getFloor() != null ? equipment.getFloor().getFloorName() : "-");
        result.put("equipmentType", equipment.getEquipmentType());
        result.put("manufacturer", equipment.getManufacturer());
        result.put("locationDescription", equipment.getLocationDescription());
        result.put("outdoorUnitCount", equipment.getOutdoorUnitCount());
        result.put("manufactureDate", equipment.getManufactureDate());
        result.put("replacementDueDate", equipment.getReplacementDueDate());
        result.put("x", equipment.getX());
        result.put("y", equipment.getY());
        result.put("imagePath", equipment.getImagePath());
        result.put("note", equipment.getNote());
        return result;
    }

    private Map<String, Object> faultReportRow(FacilityAirconFaultReport report) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportId", report.getReportId());
        row.put("reporterName", report.getReporterName());
        row.put("reporterDepartment", report.getReporterDepartment());
        row.put("faultDescription", report.getFaultDescription());
        row.put("inspectionResult", report.getFaultDescription());
        row.put("status", report.getStatus());
        row.put("createdAt", report.getCreatedAt());
        return row;
    }

    private Map<String, Object> disinfectionRow(FacilityWaterDisinfection disinfection) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("disinfectionId", disinfection.getDisinfectionId());
        row.put("disinfectionDate", disinfection.getDisinfectionDate());
        row.put("workerName", disinfection.getWorkerName());
        row.put("note", disinfection.getNote());
        row.put("inspectionResult", disinfection.getNote());
        row.put("photoPath", disinfection.getPhotoPath());
        row.put("createdAt", disinfection.getCreatedAt());
        return row;
    }


    private FacilityEquipmentSaveRequest baseRegisterRequest(String serialNumber, Long buildingId, Long floorId,
                                                             String manufactureDate, BigDecimal x, BigDecimal y) {
        FacilityEquipmentSaveRequest request = new FacilityEquipmentSaveRequest();
        request.setSerialNumber(trimToNull(serialNumber));
        request.setBuildingId(buildingId);
        request.setFloorId(floorId);
        request.setManufactureDate(parseDateOrToday(manufactureDate));
        request.setX(x);
        request.setY(y);
        return request;
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> registerFacilityEquipment(String category, String kind, String qrKey,
                                                                                       FacilityEquipmentSaveRequest request, MultipartFile photo) {
        ResponseEntity<ApiResponse<Map<String, Object>>> validation = validateImage(photo, false);
        if (validation != null) return validation;
        String imagePath = null;
        if (photo != null && !photo.isEmpty()) {
            try {
                imagePath = saveFacilityImage(photo, kind);
            } catch (IOException ex) {
                return ResponseEntity.internalServerError().body(ApiResponse.fail("사진 저장에 실패했습니다."));
            }
        }
        FacilityEquipmentResponse saved = facilityEquipmentService.registerMobileEquipment(category, qrKey, request);
        if (imagePath != null) {
            facilityEquipmentService.updateImagePath(category, saved.getEquipmentId(), imagePath);
        } else {
            imagePath = saved.getImagePath();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("equipmentId", saved.getEquipmentId());
        result.put("serialNumber", saved.getSerialNumber());
        result.put("equipmentCode", saved.getEquipmentCode());
        result.put("qrKey", saved.getQrKey());
        result.put("imagePath", imagePath);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private LocalDate parseDateOrToday(String value) {
        String clean = trimToNull(value);
        if (clean == null) return LocalDate.now();
        try {
            return LocalDate.parse(clean);
        } catch (Exception ex) {
            throw new IllegalArgumentException("설치일 형식이 올바르지 않습니다.");
        }
    }

    private String saveFacilityImage(MultipartFile file, String kind) throws IOException {
        Path dir = facilityUploadDir(kind);
        Files.createDirectories(dir);
        String ext = resolveExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Files.copy(file.getInputStream(), dir.resolve(filename).normalize(), StandardCopyOption.REPLACE_EXISTING);
        return "/facility-api/" + kind + "/files/" + filename;
    }

    private Path facilityUploadDir(String kind) {
        String root = System.getenv("MODULE_FIRE_FACILITY_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            Path dataRoot = Paths.get("/data/upload/module_fire/facility");
            if (Files.exists(dataRoot) || Files.isWritable(dataRoot.getParent())) {
                root = dataRoot.toString();
            } else {
                root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_fire", "facility").toString();
            }
        }
        return Paths.get(root).resolve(kind).normalize();
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> validateImage(MultipartFile file, boolean required) {
        if (file == null || file.isEmpty()) {
            return required ? ResponseEntity.badRequest().body(ApiResponse.fail("완료 사진을 촬영하거나 선택하세요.")) : null;
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("이미지 크기는 10MB 이하만 가능합니다."));
        }
        if (!isAllowedImageFile(file)) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("이미지 파일만 업로드할 수 있습니다."));
        }
        return null;
    }

    private String saveMobileImage(MultipartFile file, String kind) throws IOException {
        Path dir = uploadDir(kind);
        Files.createDirectories(dir);
        String ext = resolveExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Files.copy(file.getInputStream(), dir.resolve(filename).normalize(), StandardCopyOption.REPLACE_EXISTING);
        return "/facility-api/mobile/files/" + kind + "/" + filename;
    }

    private Path uploadDir(String child) {
        String root = System.getenv("MODULE_FIRE_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_fire").toString();
        }
        return Paths.get(root).resolve("facility-mobile").resolve(child).normalize();
    }

    private boolean isAllowedImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String original = file.getOriginalFilename();
        if (original == null) {
            return false;
        }
        int idx = original.lastIndexOf('.');
        if (idx < 0 || idx >= original.length() - 1) {
            return false;
        }
        String ext = original.substring(idx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif").contains(ext);
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null) return "jpg";
        int idx = originalFilename.lastIndexOf('.');
        if (idx < 0 || idx >= originalFilename.length() - 1) return "jpg";
        String ext = originalFilename.substring(idx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return ext.isBlank() ? "jpg" : ext;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
