package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FireReceiverResponse;
import com.company.module.fire.dto.FireReceiverSaveRequest;
import com.company.module.fire.service.FireReceiverService;
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
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/fire-api/receivers")
@RequiredArgsConstructor
public class FireReceiverController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final FireReceiverService fireReceiverService;
    private final com.company.module.fire.service.InspectorNameResolver inspectorNameResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FireReceiverResponse>>> getList(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(fireReceiverService.getReceivers(q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FireReceiverResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fireReceiverService.getReceiverDetail(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FireReceiverResponse>> save(
            @Valid @RequestBody FireReceiverSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireReceiverService.save(request)));
    }

    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FireReceiverResponse>> inspect(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionRequest request,
            Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok(ApiResponse.success(fireReceiverService.inspect(
                id,
                request,
                inspectorNameResolver.resolveUserId(username),
                inspectorNameResolver.resolveDisplayName(username)
        )));
    }

    @PatchMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FireReceiverResponse>> updateInspection(
            @PathVariable Long id,
            @PathVariable Long inspectionId,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                fireReceiverService.updateInspection(id, inspectionId, request)
        ));
    }

    @PostMapping("/{id}/inspections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FireReceiverResponse>> addInspection(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                fireReceiverService.addInspection(id, request)
        ));
    }

    @GetMapping("/{id}/inspections/export")
    public ResponseEntity<byte[]> exportInspections(
            @PathVariable Long id,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = fireReceiverService.exportInspectionCsv(id, from, to);
        String filename = "receiver-inspections-" + id + "-" + from + "-" + to + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }

    @GetMapping("/inspections/export-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportAllInspections(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = fireReceiverService.exportAllInspectionCsv(from, to);
        String filename = "receiver-inspections-all-" + from + "-" + to + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }

    @PostMapping("/{id}/inspections/{inspectionId}/image")
    @PreAuthorize("hasRole('ADMIN')")
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
            Path dir = Paths.get("/data/upload/module_fire/receiver-inspections");
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

            String publicPath = "/fire-api/receivers/files/" + filename;
            fireReceiverService.updateInspectionImagePath(id, inspectionId, publicPath);

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
            Path base = Paths.get("/data/upload/module_fire/receiver-inspections").toAbsolutePath().normalize();
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        fireReceiverService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
