package com.company.module.fire.facility;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.service.InspectorNameResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/facility-api")
@RequiredArgsConstructor
public class FacilityEquipmentController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final FacilityEquipmentService facilityEquipmentService;
    private final InspectorNameResolver inspectorNameResolver;

    @GetMapping("/air-conditioners")
    public ResponseEntity<ApiResponse<Page<FacilityEquipmentResponse>>> getAirConditioners(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) List<Long> buildingIds,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Long> selectedBuildingIds = selectedBuildingIds(buildingId, buildingIds);
        return ResponseEntity.ok(ApiResponse.success(facilityEquipmentService.getEquipmentList(
                FacilityEquipmentService.CATEGORY_AIRCON, selectedBuildingIds, floorId, q, page, size)));
    }

    @GetMapping("/water-purifiers")
    public ResponseEntity<ApiResponse<Page<FacilityEquipmentResponse>>> getWaterPurifiers(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) List<Long> buildingIds,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Long> selectedBuildingIds = selectedBuildingIds(buildingId, buildingIds);
        return ResponseEntity.ok(ApiResponse.success(facilityEquipmentService.getEquipmentList(
                FacilityEquipmentService.CATEGORY_WATER_PURIFIER, selectedBuildingIds, floorId, q, page, size)));
    }

    @GetMapping("/qr/list")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFacilityQrList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("airConditioners", facilityEquipmentService.getEquipmentList(
                FacilityEquipmentService.CATEGORY_AIRCON, buildingId, floorId, null, 0, 10000).getContent());
        result.put("waterPurifiers", facilityEquipmentService.getEquipmentList(
                FacilityEquipmentService.CATEGORY_WATER_PURIFIER, buildingId, floorId, null, 0, 10000).getContent());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/qr/unregistered-keys")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnregisteredFacilityQrKeys(
            @RequestParam(defaultValue = "0") int airconCount,
            @RequestParam(defaultValue = "0") int waterCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unregisteredAirconQrKeys", facilityEquipmentService.generateUnregisteredQrKeys(
                FacilityEquipmentService.CATEGORY_AIRCON, airconCount));
        result.put("unregisteredWaterQrKeys", facilityEquipmentService.generateUnregisteredQrKeys(
                FacilityEquipmentService.CATEGORY_WATER_PURIFIER, waterCount));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/air-conditioners/{id}")
    public ResponseEntity<ApiResponse<FacilityEquipmentResponse>> getAirConditioner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(facilityEquipmentService.getDetail(FacilityEquipmentService.CATEGORY_AIRCON, id)));
    }

    @GetMapping("/water-purifiers/{id}")
    public ResponseEntity<ApiResponse<FacilityEquipmentResponse>> getWaterPurifier(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(facilityEquipmentService.getDetail(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, id)));
    }

    @PostMapping("/air-conditioners")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<FacilityEquipmentResponse>> saveAirConditioner(@Valid @RequestBody FacilityEquipmentSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(facilityEquipmentService.saveEquipment(FacilityEquipmentService.CATEGORY_AIRCON, request)));
    }

    @PostMapping("/water-purifiers")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<FacilityEquipmentResponse>> saveWaterPurifier(@Valid @RequestBody FacilityEquipmentSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(facilityEquipmentService.saveEquipment(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, request)));
    }

    @PostMapping("/air-conditioners/{id}/image")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadAirConditionerImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return uploadImage(FacilityEquipmentService.CATEGORY_AIRCON, "air-conditioners", id, file);
    }

    @PostMapping("/water-purifiers/{id}/image")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadWaterPurifierImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return uploadImage(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, "water-purifiers", id, file);
    }

    @GetMapping("/{kind}/files/{filename:.+}")
    public ResponseEntity<Resource> getImageFile(@PathVariable String kind, @PathVariable String filename) {
        if (!"air-conditioners".equals(kind) && !"water-purifiers".equals(kind)) {
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

    @PostMapping("/air-conditioners/inspect")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> inspectAirConditioner(@Valid @RequestBody FacilityEquipmentInspectRequest request, Principal principal) {
        inspect(FacilityEquipmentService.CATEGORY_AIRCON, request, principal);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/water-purifiers/inspect")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> inspectWaterPurifier(@Valid @RequestBody FacilityEquipmentInspectRequest request, Principal principal) {
        inspect(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, request, principal);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/air-conditioners/{id}/inspections")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> addAirConditionerInspection(@PathVariable Long id, @Valid @RequestBody FacilityEquipmentInspectionUpdateRequest request, Principal principal) {
        addInspection(FacilityEquipmentService.CATEGORY_AIRCON, id, request, principal);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/water-purifiers/{id}/inspections")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> addWaterPurifierInspection(@PathVariable Long id, @Valid @RequestBody FacilityEquipmentInspectionUpdateRequest request, Principal principal) {
        addInspection(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, id, request, principal);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/air-conditioners/{id}/inspections/{inspectionId}")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> updateAirConditionerInspection(@PathVariable Long id, @PathVariable Long inspectionId, @Valid @RequestBody FacilityEquipmentInspectionUpdateRequest request, Principal principal) {
        updateInspection(FacilityEquipmentService.CATEGORY_AIRCON, id, inspectionId, request, principal);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/water-purifiers/{id}/inspections/{inspectionId}")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> updateWaterPurifierInspection(@PathVariable Long id, @PathVariable Long inspectionId, @Valid @RequestBody FacilityEquipmentInspectionUpdateRequest request, Principal principal) {
        updateInspection(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, id, inspectionId, request, principal);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/air-conditioners/{id}/inspections/{inspectionId}")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> deleteAirConditionerInspection(@PathVariable Long id, @PathVariable Long inspectionId) {
        facilityEquipmentService.deleteInspection(FacilityEquipmentService.CATEGORY_AIRCON, id, inspectionId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/water-purifiers/{id}/inspections/{inspectionId}")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> deleteWaterPurifierInspection(@PathVariable Long id, @PathVariable Long inspectionId) {
        facilityEquipmentService.deleteInspection(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, id, inspectionId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/air-conditioners/{id}")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> deleteAirConditioner(@PathVariable Long id) {
        facilityEquipmentService.deleteEquipment(FacilityEquipmentService.CATEGORY_AIRCON, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/water-purifiers/{id}")
    @PreAuthorize("@facilityPermissionService.hasOtherFacilityAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> deleteWaterPurifier(@PathVariable Long id) {
        facilityEquipmentService.deleteEquipment(FacilityEquipmentService.CATEGORY_WATER_PURIFIER, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    private List<Long> selectedBuildingIds(Long buildingId, List<Long> buildingIds) {
        return buildingIds == null || buildingIds.isEmpty()
                ? (buildingId == null ? null : List.of(buildingId))
                : buildingIds;
    }

    private void inspect(String category, FacilityEquipmentInspectRequest request, Principal principal) {
        String username = principal.getName();
        facilityEquipmentService.inspect(category, request,
                inspectorNameResolver.resolveUserId(username),
                inspectorNameResolver.resolveDisplayName(username));
    }

    private void addInspection(String category, Long id, FacilityEquipmentInspectionUpdateRequest request, Principal principal) {
        String username = principal.getName();
        facilityEquipmentService.addInspection(category, id, request.getInspectionDate(), Boolean.TRUE.equals(request.getIsFaulty()),
                request.getFaultReason(), inspectorNameResolver.resolveDisplayName(username), inspectorNameResolver.resolveUserId(username));
    }

    private void updateInspection(String category, Long id, Long inspectionId, FacilityEquipmentInspectionUpdateRequest request, Principal principal) {
        String username = principal.getName();
        facilityEquipmentService.updateInspection(category, id, inspectionId, request.getInspectionDate(), Boolean.TRUE.equals(request.getIsFaulty()),
                request.getFaultReason(), inspectorNameResolver.resolveDisplayName(username));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> uploadImage(String category, String kind, Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image file is empty."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image size must be <= 10MB."));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Only image files are allowed."));
        }
        try {
            Path dir = uploadDir(kind);
            Files.createDirectories(dir);
            FacilityEquipmentResponse detail = facilityEquipmentService.getDetail(category, id);
            deleteOldImage(detail.getImagePath(), dir);

            String ext = resolveExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename).normalize(), StandardCopyOption.REPLACE_EXISTING);
            String publicPath = "/facility-api/" + kind + "/files/" + filename;
            facilityEquipmentService.updateImagePath(category, id, publicPath);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imagePath", publicPath);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("Image save failed."));
        }
    }

    private Path uploadDir(String kind) {
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

    private void deleteOldImage(String oldPath, Path dir) throws IOException {
        if (oldPath == null || oldPath.isBlank()) return;
        String oldName = oldPath.substring(oldPath.lastIndexOf('/') + 1).replace("\\", "");
        if (oldName.isBlank() || oldName.contains("..")) return;
        Path base = dir.toAbsolutePath().normalize();
        Path target = base.resolve(oldName).normalize();
        if (target.startsWith(base)) {
            Files.deleteIfExists(target);
        }
    }

    private String resolveExtension(String original) {
        String ext = "png";
        if (original != null) {
            int idx = original.lastIndexOf('.');
            if (idx > -1 && idx < original.length() - 1) {
                String parsed = original.substring(idx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                if (!parsed.isBlank()) ext = parsed;
            }
        }
        return ext;
    }
}
