package com.company.module.dailyreport.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.ImageResponse;
import com.company.module.dailyreport.service.DailyReportService;
import com.company.module.dailyreport.service.MenuPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 이미지 첨부 REST Controller
 * - 일보별 이미지 실제 파일 업로드(멀티파트) 저장, 조회(다운로드), 삭제
 *
 * ※ 파일 저장/조회 방식은 module-fire의 ExtinguisherController 패턴을 따른다.
 *   (uploads/module_dailyreport 디렉터리에 UUID 파일명으로 저장, path-traversal 방지)
 * ※ /dailyreport-api/** 는 SecurityConfig 상 인증이 필요하므로(=permitAll 예외 없음),
 *   파일 다운로드도 Authorization 헤더(Bearer) 인증을 그대로 요구한다.
 *   프론트엔드는 <img src="...">가 아니라 인증된 fetch()로 blob을 받아
 *   URL.createObjectURL()로 표시해야 한다.
 */
@Slf4j
@RestController
@RequestMapping("/dailyreport-api/reports/{reportId}/images")
@RequiredArgsConstructor
public class ImageController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif");

    private final DailyReportService dailyReportService;
    private final MenuPermissionService menuPermissionService;

    /**
     * 이미지 목록 조회
     * GET /dailyreport-api/reports/{reportId}/images
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ImageResponse>>> getImages(
            @PathVariable Long reportId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        verifyReadAccess(userId);
        return ResponseEntity.ok(ApiResponse.success(dailyReportService.getImages(reportId)));
    }

    /**
     * 이미지 파일 업로드
     * POST /dailyreport-api/reports/{reportId}/images/upload (multipart/form-data)
     *
     * @param file 업로드 이미지 파일 (최대 10MB, 이미지 형식만 허용)
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ImageResponse>> uploadImage(
            @PathVariable Long reportId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tableCode,
            @AuthenticationPrincipal(expression = "userId") Long userId) {

        verifyWriteAccess(userId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 이미지 파일이 없습니다.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일은 10MB 이하만 업로드할 수 있습니다.");
        }
        if (!isAllowedImageFile(file)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일(jpg/png/gif/webp 등)만 업로드할 수 있습니다.");
        }

        try {
            Path dir = uploadDir();
            Files.createDirectories(dir);

            String originalName = file.getOriginalFilename();
            String ext = extractExtension(originalName);
            String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(storedFilename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // ※ storedPath는 물리 경로가 아니라 다운로드 API 경로를 저장한다
            //   (module-fire ExtinguisherController와 동일한 방식)
            String storedPath = "/dailyreport-api/reports/" + reportId + "/images/files/" + storedFilename;

            ImageResponse response = dailyReportService.addImage(
                    reportId,
                    originalName != null ? originalName : storedFilename,
                    storedPath,
                    file.getSize(),
                    file.getContentType(),
                    description,
                    tableCode,
                    userId);

            return ResponseEntity.ok(ApiResponse.created(response));
        } catch (IOException ex) {
            log.error("이미지 저장 실패 reportId={}", reportId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지 저장 중 오류가 발생했습니다.");
        }
    }

    /**
     * 이미지 파일 조회(다운로드/미리보기)
     * GET /dailyreport-api/reports/{reportId}/images/files/{filename}
     *
     * - reportId + 저장된 파일명이 실제 DB의 이미지 메타(storedPath)와 일치하는 경우에만 서빙
     *   (다른 일보의 파일명을 추측해 접근하는 것을 방지)
     * - path-traversal 방지: 파일명에 ".."나 "/" 포함 시 거부
     */
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> getImageFile(
            @PathVariable Long reportId,
            @PathVariable String filename,
            @AuthenticationPrincipal(expression = "userId") Long userId) {

        verifyReadAccess(userId);

        String clean = filename == null ? "" : filename.replace("\\", "/");
        if (clean.contains("..") || clean.contains("/")) {
            return ResponseEntity.badRequest().build();
        }

        // ★ 이 파일이 실제로 reportId에 속한 이미지인지 DB로 확인 (imageId 없이도 접근 가능한
        //   구조이므로, storedPath 경로에 저장된 파일명 기준으로 소속 일보 목록에서 검증)
        boolean belongsToReport = dailyReportService.getImages(reportId).stream()
                .anyMatch(img -> img.getStoredPath() != null && img.getStoredPath().endsWith("/" + clean));
        if (!belongsToReport) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path base = uploadDir().toAbsolutePath().normalize();
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
            log.error("이미지 조회 실패 reportId={}, filename={}", reportId, filename, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 이미지 설명 수정
     * PATCH /dailyreport-api/reports/{reportId}/images/{imageId}
     */
    @PatchMapping("/{imageId}")
    public ResponseEntity<ApiResponse<ImageResponse>> updateImageDescription(
            @PathVariable Long reportId,
            @PathVariable Long imageId,
            @RequestParam String description,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        verifyWriteAccess(userId);
        return ResponseEntity.ok(
                ApiResponse.success(dailyReportService.updateImageDescription(imageId, description)));
    }

    /**
     * 이미지 삭제 (DB 메타 + 물리 파일 함께 삭제)
     * DELETE /dailyreport-api/reports/{reportId}/images/{imageId}
     */
    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long reportId,
            @PathVariable Long imageId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {

        verifyWriteAccess(userId);

        String storedPath = dailyReportService.deleteImage(imageId);
        deletePhysicalFileIfExists(storedPath);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────

    private void verifyReadAccess(Long userId) {
        if (!menuPermissionService.canAccessInputPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "세부공장일보 입력 페이지에 대한 접근 권한이 없습니다.");
        }
    }

    private void verifyWriteAccess(Long userId) {
        if (!menuPermissionService.canWriteInputPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "세부공장일보 입력 페이지에 대한 쓰기 권한이 없습니다.");
        }
    }

    /**
     * storedPath(다운로드 API 경로)에서 실제 파일명을 추출해 물리 파일을 삭제한다.
     * 파일이 이미 없는 경우에도 예외를 던지지 않고 조용히 넘어간다.
     */
    private void deletePhysicalFileIfExists(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        String filename = storedPath.substring(storedPath.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.contains("..") || filename.contains("/")) {
            return;
        }
        try {
            Path base = uploadDir().toAbsolutePath().normalize();
            Path file = base.resolve(filename).normalize();
            if (file.startsWith(base)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ex) {
            log.warn("이미지 물리 파일 삭제 실패 storedPath={}", storedPath, ex);
        }
    }

    private static Path uploadDir() {
        String root = System.getenv("MODULE_DAILYREPORT_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_dailyreport").toString();
        }
        return Paths.get(root).resolve("images").normalize();
    }

    private static String extractExtension(String originalName) {
        String ext = "png";
        if (originalName != null) {
            int idx = originalName.lastIndexOf('.');
            if (idx > -1 && idx < originalName.length() - 1) {
                String parsed = originalName.substring(idx + 1)
                        .replaceAll("[^a-zA-Z0-9]", "")
                        .toLowerCase(Locale.ROOT);
                if (!parsed.isBlank()) {
                    ext = parsed;
                }
            }
        }
        return ext;
    }

    private static boolean isAllowedImageFile(MultipartFile file) {
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
        return ALLOWED_EXTENSIONS.contains(ext);
    }
}
