package com.company.module.cckb.support;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * CCKB 이미지 파일 디스크 저장/삭제 헬퍼.
 * <p>module-dailyreport 의 {@code ImageController} 내부 파일 저장 로직(uploadDir/extractExtension/
 * isAllowedImageFile)을 그대로 따른다 (사용자 명시 지시 — "세부공장일보 이미지와 같은 방식").
 * UUID 파일명으로 저장하고, DB 에는 물리 경로가 아니라 다운로드 API 경로를 저장한다.
 */
@Slf4j
@Component
public class CckbFileStorageService {

    public static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif");

    /** 저장 결과: 서버에 저장된 파일명(UUID.ext)과 물리 경로 */
    public record StoredFile(String storedFilename, Path physicalPath) {
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 이미지 파일이 없습니다.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일은 10MB 이하만 업로드할 수 있습니다.");
        }
        if (!isAllowedImageFile(file)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일(jpg/png/gif/webp 등)만 업로드할 수 있습니다.");
        }
    }

    public StoredFile save(MultipartFile file) {
        try {
            Path dir = uploadDir();
            Files.createDirectories(dir);

            String ext = extractExtension(file.getOriginalFilename());
            String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(storedFilename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(storedFilename, target);
        } catch (IOException ex) {
            log.error("CCKB 이미지 저장 실패", ex);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지 저장 중 오류가 발생했습니다.");
        }
    }

    /** storedPath(다운로드 API 경로)에서 파일명만 뽑아 물리 파일을 삭제한다. 없으면 조용히 넘어간다. */
    public void deleteIfExists(String storedPath) {
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
            log.warn("CCKB 이미지 물리 파일 삭제 실패 storedPath={}", storedPath, ex);
        }
    }

    public Path resolveForServing(String filename) {
        String clean = filename == null ? "" : filename.replace("\\", "/");
        if (clean.contains("..") || clean.contains("/")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 파일명입니다.");
        }
        Path base = uploadDir().toAbsolutePath().normalize();
        Path file = base.resolve(clean).normalize();
        if (!file.startsWith(base)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 파일 경로입니다.");
        }
        return file;
    }

    public static Path uploadDir() {
        String root = System.getenv("MODULE_CCKB_UPLOAD_ROOT");
        if (root == null || root.isBlank()) {
            root = Paths.get(System.getProperty("user.dir", "."), "uploads", "module_cckb").toString();
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
