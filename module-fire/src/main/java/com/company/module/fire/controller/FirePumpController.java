package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FirePumpResponse;
import com.company.module.fire.dto.FirePumpSaveRequest;
import com.company.module.fire.service.FirePumpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ContentDisposition;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/fire-api/pumps")
@RequiredArgsConstructor
public class FirePumpController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Path PUMP_IMAGE_DIR = uploadDir("pumps");
    private static final Path PUMP_INSPECTION_IMAGE_DIR = uploadDir("pump-inspections");

    private final FirePumpService firePumpService;
    private final com.company.module.fire.service.InspectorNameResolver inspectorNameResolver;
    private final com.company.core.menu.service.CoreMenuService coreMenuService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FirePumpResponse>>> getList(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(firePumpService.getPumps(q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FirePumpResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(firePumpService.getPumpDetail(id)));
    }

    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<FirePumpResponse>> save(
            @Valid @RequestBody FirePumpSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(firePumpService.save(request)));
    }

    @PostMapping("/{id}/inspect")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<FirePumpResponse>> inspect(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionRequest request,
            Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok(ApiResponse.success(firePumpService.inspect(
                id,
                request,
                inspectorNameResolver.resolveUserId(username),
                inspectorNameResolver.resolveDisplayName(username)
        )));
    }

    @PatchMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<FirePumpResponse>> updateInspection(
            @PathVariable Long id,
            @PathVariable Long inspectionId,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                firePumpService.updateInspection(id, inspectionId, request)
        ));
    }

    @PostMapping("/{id}/inspections")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<FirePumpResponse>> addInspection(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                firePumpService.addInspection(id, request)
        ));
    }

    @DeleteMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<FirePumpResponse>> deleteInspection(
            @PathVariable Long id,
            @PathVariable Long inspectionId) {
        return ResponseEntity.ok(ApiResponse.success(
                firePumpService.deleteInspection(id, inspectionId)
        ));
    }

    @GetMapping("/{id}/inspections/export")
    public ResponseEntity<byte[]> exportInspections(
            @PathVariable Long id,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = firePumpService.exportInspectionWorkbook(id, from, to);
        String filename = "pump-inspections-" + id + "-" + from + "-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/inspections/export-all")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<byte[]> exportAllInspections(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = firePumpService.exportAllInspectionWorkbook(from, to);
        String filename = "pump-inspections-all-" + from + "-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image file is empty."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image size must be <= 10MB."));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Only image files are allowed."));
        }

        try {
            Files.createDirectories(PUMP_IMAGE_DIR);

            String filename = buildSafeImageFilename(file);
            Path target = PUMP_IMAGE_DIR.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = "/fire-api/pumps/files/" + filename;
            firePumpService.updateImagePath(id, publicPath);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imagePath", publicPath);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("Image save failed."));
        }
    }

    @PostMapping("/{id}/inspections/{inspectionId}/image")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadInspectionImage(
            @PathVariable Long id,
            @PathVariable Long inspectionId,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image file is empty."));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Image size must be <= 10MB."));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Only image files are allowed."));
        }

        try {
            Files.createDirectories(PUMP_INSPECTION_IMAGE_DIR);

            String filename = buildSafeImageFilename(file);
            Path target = PUMP_INSPECTION_IMAGE_DIR.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = "/fire-api/pumps/files/" + filename;
            firePumpService.updateInspectionImagePath(id, inspectionId, publicPath);

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
            for (Path candidateBase : List.of(PUMP_IMAGE_DIR, PUMP_INSPECTION_IMAGE_DIR)) {
                Path base = candidateBase.toAbsolutePath().normalize();
                Path file = base.resolve(clean).normalize();
                if (!file.startsWith(base) || !Files.exists(file)) {
                    continue;
                }
                Resource resource = new UrlResource(file.toUri());
                MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                        .contentType(mediaType)
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String buildSafeImageFilename(MultipartFile file) {
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
        return UUID.randomUUID().toString().replace("-", "") + "." + ext;
    }

    private static Path uploadDir(String child) {
        String root = System.getenv("MODULE_FIRE_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_fire").toString();
        }
        return Paths.get(root).resolve(child).normalize();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_PUMP')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        firePumpService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
