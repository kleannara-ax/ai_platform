package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.dto.ExtinguisherInspectRequest;
import com.company.module.fire.dto.ExtinguisherInspectionUpdateRequest;
import com.company.module.fire.dto.ExtinguisherResponse;
import com.company.module.fire.dto.ExtinguisherSaveRequest;
import com.company.module.fire.service.ExtinguisherService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

@RestController
@RequestMapping("/fire-api/extinguishers")
@RequiredArgsConstructor
public class ExtinguisherController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final ExtinguisherService extinguisherService;
    private final InspectorNameResolver inspectorNameResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExtinguisherResponse>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ExtinguisherResponse> result = extinguisherService.getExtinguishers(
                buildingId, floorId, q, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtinguisherResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(extinguisherService.getExtinguisherDetail(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<ExtinguisherResponse>> save(
            @Valid @RequestBody ExtinguisherSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(extinguisherService.saveExtinguisher(request)));
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
            Path dir = Paths.get("/data/upload/module_fire/extinguishers");
            Files.createDirectories(dir);

            ExtinguisherResponse detail = extinguisherService.getExtinguisherDetail(id);
            String serial = detail.getSerialNumber();
            if (serial == null || serial.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.fail("Serial number is empty."));
            }
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
                    if (!parsed.isBlank()) {
                        ext = parsed;
                    }
                }
            }

            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = "/fire-api/extinguishers/files/" + filename;
            extinguisherService.updateImagePath(id, publicPath);

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

    @PostMapping("/inspect")
    public ResponseEntity<ApiResponse<Void>> inspect(
            @Valid @RequestBody ExtinguisherInspectRequest request,
            Principal principal) {
        String username = principal.getName();
        extinguisherService.inspect(
                request,
                inspectorNameResolver.resolveUserId(username),
                inspectorNameResolver.resolveDisplayName(username));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateInspectionDate(
            @PathVariable("id") Long extinguisherId,
            @PathVariable Long inspectionId,
            @Valid @RequestBody ExtinguisherInspectionUpdateRequest request,
            Principal principal) {
        String username = principal.getName();
        extinguisherService.updateInspectionDate(
                extinguisherId,
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
            @PathVariable("id") Long extinguisherId,
            @Valid @RequestBody ExtinguisherInspectionUpdateRequest request,
            Principal principal) {
        String username = principal.getName();
        extinguisherService.addInspection(
                extinguisherId,
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
            @PathVariable("id") Long extinguisherId,
            @PathVariable Long inspectionId) {
        extinguisherService.deleteInspection(extinguisherId, inspectionId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FIRE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        extinguisherService.deleteExtinguisher(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
