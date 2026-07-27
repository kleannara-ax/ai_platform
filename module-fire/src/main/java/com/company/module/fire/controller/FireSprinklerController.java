package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FireSprinklerResponse;
import com.company.module.fire.dto.FireSprinklerSaveRequest;
import com.company.module.fire.service.FireSprinklerService;
import com.company.module.fire.service.InspectorNameResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fire-api/sprinklers")
@RequiredArgsConstructor
public class FireSprinklerController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Path SPRINKLER_IMAGE_DIR = uploadDir("sprinklers");

    private final FireSprinklerService fireSprinklerService;
    private final InspectorNameResolver inspectorNameResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FireSprinklerResponse>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) List<Long> buildingIds,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Long> selectedBuildingIds = buildingIds == null || buildingIds.isEmpty()
                ? (buildingId == null ? null : List.of(buildingId))
                : buildingIds;
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.getSprinklers(selectedBuildingIds, floorId, q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.getSprinklerDetail(id)));
    }

    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> save(@Valid @RequestBody FireSprinklerSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.save(request)));
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
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
            Files.createDirectories(SPRINKLER_IMAGE_DIR);
            FireSprinklerResponse detail = fireSprinklerService.getSprinklerDetail(id);
            String ext = resolveExtension(file.getOriginalFilename(), contentType);
            String filename = "sprinkler-" + id + "." + ext;
            deleteOldImage(detail.getImagePath(), filename);
            deleteOtherImageVariants(id, filename);

            Path base = SPRINKLER_IMAGE_DIR.toAbsolutePath().normalize();
            Path target = base.resolve(filename).normalize();
            if (!target.startsWith(base)) {
                return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid image filename."));
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicPath = "/fire-api/sprinklers/files/" + filename;
            fireSprinklerService.updateImagePath(id, publicPath);
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
            Path base = SPRINKLER_IMAGE_DIR.toAbsolutePath().normalize();
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

    @PostMapping("/{id}/inspect")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> inspect(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionRequest request,
            Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.inspect(
                id,
                request,
                inspectorNameResolver.resolveUserId(username),
                inspectorNameResolver.resolveDisplayName(username))));
    }

    @PatchMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> updateInspection(
            @PathVariable Long id,
            @PathVariable Long inspectionId,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.updateInspection(id, inspectionId, request)));
    }

    @PostMapping("/{id}/inspections")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> addInspection(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.addInspection(id, request)));
    }

    @DeleteMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> deleteInspection(
            @PathVariable Long id,
            @PathVariable Long inspectionId) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.deleteInspection(id, inspectionId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        fireSprinklerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{id}/inspections/export")
    public ResponseEntity<byte[]> exportInspections(
            @PathVariable Long id,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = fireSprinklerService.exportInspectionWorkbook(id, from, to);
        String filename = "sprinkler-inspections-" + id + "-" + from + "-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    private void deleteOldImage(String oldPath, String newFilename) throws IOException {
        if (oldPath == null || oldPath.isBlank()) return;
        String oldName = oldPath.substring(oldPath.lastIndexOf('/') + 1).replace("\\", "");
        if (oldName.isBlank() || oldName.contains("..") || oldName.contains("/")) return;
        if (oldName.equals(newFilename)) return;
        Path base = SPRINKLER_IMAGE_DIR.toAbsolutePath().normalize();
        Path target = base.resolve(oldName).normalize();
        if (target.startsWith(base)) {
            Files.deleteIfExists(target);
        }
    }

    private void deleteOtherImageVariants(Long sprinklerId, String keepFilename) throws IOException {
        String prefix = "sprinkler-" + sprinklerId + ".";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SPRINKLER_IMAGE_DIR, prefix + "*")) {
            Path base = SPRINKLER_IMAGE_DIR.toAbsolutePath().normalize();
            for (Path candidate : stream) {
                Path target = candidate.toAbsolutePath().normalize();
                if (target.startsWith(base) && !candidate.getFileName().toString().equals(keepFilename)) {
                    Files.deleteIfExists(target);
                }
            }
        }
    }

    private String resolveExtension(String original, String contentType) {
        String parsed = "";
        if (original != null) {
            int idx = original.lastIndexOf('.');
            if (idx > -1 && idx < original.length() - 1) {
                parsed = original.substring(idx + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            }
        }
        if (isAllowedImageExtension(parsed)) {
            return parsed.equals("jpeg") ? "jpg" : parsed;
        }
        String normalizedType = contentType == null ? "" : contentType.toLowerCase();
        if (normalizedType.contains("jpeg")) return "jpg";
        if (normalizedType.contains("png")) return "png";
        if (normalizedType.contains("gif")) return "gif";
        if (normalizedType.contains("webp")) return "webp";
        if (normalizedType.contains("bmp")) return "bmp";
        if (normalizedType.contains("svg")) return "svg";
        return "png";
    }

    private boolean isAllowedImageExtension(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext)
                || "gif".equals(ext) || "webp".equals(ext) || "bmp".equals(ext) || "svg".equals(ext);
    }

    private static Path uploadDir(String child) {
        String root = System.getenv("MODULE_FIRE_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_fire").toString();
        }
        return Paths.get(root).resolve(child).normalize();
    }

    @GetMapping("/inspections/export-all")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<byte[]> exportAllInspections(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = fireSprinklerService.exportAllInspectionWorkbook(from, to);
        String filename = "sprinkler-inspections-all-" + from + "-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
