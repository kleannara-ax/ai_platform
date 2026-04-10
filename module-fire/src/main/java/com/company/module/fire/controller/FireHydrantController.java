package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.dto.*;
import com.company.module.fire.service.InspectorNameResolver;
import com.company.module.fire.service.FireHydrantService;
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
import java.util.Map;
import java.util.UUID;

/**
 * 소화전 관리 API Controller
 * URL Prefix: /fire-api/hydrants/**
 */
@RestController
@RequestMapping("/fire-api/hydrants")
@RequiredArgsConstructor
public class FireHydrantController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final FireHydrantService fireHydrantService;
    private final InspectorNameResolver inspectorNameResolver;

    /** 소화전 목록 페이징 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FireHydrantResponse>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<FireHydrantResponse> result = fireHydrantService.getHydrants(
                buildingId, floorId, q, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 소화전 상세 조회 (점검이력 포함) */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FireHydrantResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fireHydrantService.getHydrantDetail(id)));
    }

    /** 소화전 등록/수정 (ADMIN) */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<FireHydrantResponse>> save(
            @Valid @RequestBody FireHydrantSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireHydrantService.saveHydrant(request)));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadImage(
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
            return ResponseEntity.badRequest().body(ApiResponse.fail("Only image files are allowed."));
        }

        try {
            Path dir = Paths.get("/data/upload/module_fire/hydrants");
            Files.createDirectories(dir);

            FireHydrantResponse detail = fireHydrantService.getHydrantDetail(id);
            String serial = detail.getSerialNumber();
            if (serial == null || serial.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.fail("Serial number is empty."));
            }
            // Remove old image referenced by DB path (if exists)
            String oldPath = detail.getImagePath();
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

            String publicPath = "/fire-api/hydrants/files/" + filename;
            fireHydrantService.updateImagePath(id, publicPath);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imagePath", publicPath);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("Image save failed."));
        }
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> getImageFile(@PathVariable String filename) {
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

    /** 소화전 점검 등록 */
    @PostMapping("/{id}/inspect")
    public ResponseEntity<ApiResponse<Void>> inspect(
            @PathVariable Long id,
            @RequestParam boolean isFaulty,
            @RequestParam(required = false) String faultReason,
            Principal principal) {
        String username = principal.getName();
        fireHydrantService.inspect(
                id,
                isFaulty,
                faultReason,
                inspectorNameResolver.resolveUserId(username),
                inspectorNameResolver.resolveDisplayName(username));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateInspection(
            @PathVariable("id") Long hydrantId,
            @PathVariable Long inspectionId,
            @Valid @RequestBody FireHydrantInspectionUpdateRequest request,
            Principal principal) {
        String username = principal.getName();
        fireHydrantService.updateInspection(
                hydrantId,
                inspectionId,
                request.getInspectionDate(),
                Boolean.TRUE.equals(request.getIsFaulty()),
                request.getFaultReason(),
                inspectorNameResolver.resolveDisplayName(username));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{id}/inspections")
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> addInspection(
            @PathVariable("id") Long hydrantId,
            @Valid @RequestBody FireHydrantInspectionUpdateRequest request,
            Principal principal) {
        String username = principal.getName();
        fireHydrantService.addInspection(
                hydrantId,
                request.getInspectionDate(),
                Boolean.TRUE.equals(request.getIsFaulty()),
                request.getFaultReason(),
                inspectorNameResolver.resolveDisplayName(username),
                inspectorNameResolver.resolveUserId(username));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteInspection(
            @PathVariable("id") Long hydrantId,
            @PathVariable Long inspectionId) {
        fireHydrantService.deleteInspection(hydrantId, inspectionId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 소화전 삭제 (ADMIN) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        fireHydrantService.deleteHydrant(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
