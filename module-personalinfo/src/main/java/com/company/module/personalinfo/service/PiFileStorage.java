package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 업로드 파일 저장소 (로컬 디스크).
 *
 * <ul>
 *   <li>저장 위치: {@code personalinfo.upload-dir}/{구분}/{yyyy}/{MM}/{UUID}.{확장자}</li>
 *   <li>DB 에는 업로드 루트 기준 상대경로만 저장한다 (서버 이전 시 루트만 바꾸면 됨)</li>
 *   <li>원본 파일명은 저장 경로에 쓰지 않는다 (경로 조작/한글 파일명 문제 방지)</li>
 *   <li>삭제는 DB 소프트 삭제만 하고 실제 파일은 남긴다 (감사 증적 보존)</li>
 * </ul>
 */
@Slf4j
@Component
public class PiFileStorage {

    public static final Set<String> CONTRACT_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "hwp", "hwpx", "xls", "xlsx", "jpg", "jpeg", "png", "zip");
    public static final Set<String> CHECKLIST_EXTENSIONS = Set.of("xlsx");

    private final Path root;
    private final long maxContractFileBytes;

    public PiFileStorage(
            @Value("${personalinfo.upload-dir:${user.home}/personalinfo-uploads}") String uploadDir,
            @Value("${personalinfo.max-file-size-mb:30}") long maxFileSizeMb) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxContractFileBytes = maxFileSizeMb * 1024L * 1024L;
    }

    public long getMaxContractFileBytes() {
        return maxContractFileBytes;
    }

    /** 파일 저장 */
    public StoredFile store(MultipartFile file, String category, Set<String> allowedExtensions, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 파일이 없습니다.");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "파일 크기는 " + (maxBytes / 1024 / 1024) + "MB 이하만 업로드할 수 있습니다.");
        }
        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (!allowedExtensions.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "허용되지 않는 파일 형식입니다. (허용: " + String.join(", ", allowedExtensions) + ")");
        }

        verifyContent(file, ext);

        LocalDate today = LocalDate.now();
        String relative = String.format("%s/%04d/%02d/%s.%s",
                category, today.getYear(), today.getMonthValue(), UUID.randomUUID(), ext);
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            return new StoredFile(originalName, relative, normalizeContentType(file.getContentType(), ext),
                    file.getSize(), hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("[personalinfo] 파일 저장 실패: {}", relative, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  파일 내용 확인 (확장자만 바꾼 위장 파일 차단)
    // ─────────────────────────────────────────────────────────────

    private static final byte[] SIG_PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] SIG_ZIP = {'P', 'K', 3, 4};
    private static final byte[] SIG_OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] SIG_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] SIG_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * 파일 첫 부분(서명 바이트)과 내부 구조가 확장자와 맞는지 확인한다.
     * <ul>
     *   <li>pdf: 앞 1KB 안에 '%PDF-'</li>
     *   <li>docx / xlsx / hwpx: ZIP 이고 각각 word/ · xl/ · Contents/(또는 mimetype) 항목이 있음</li>
     *   <li>zip: ZIP 서명</li>
     *   <li>doc / xls / hwp: OLE2(복합 문서) 서명</li>
     *   <li>jpg / jpeg / png: 이미지 서명</li>
     * </ul>
     * 맞지 않으면 저장하지 않고 400.
     */
    public static void verifyContent(MultipartFile file, String ext) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(1024);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일을 읽을 수 없습니다.");
        }
        boolean ok = switch (ext) {
            case "pdf" -> indexOf(head, SIG_PDF) >= 0;
            case "zip" -> startsWith(head, SIG_ZIP);
            case "docx" -> startsWith(head, SIG_ZIP) && zipHasEntry(file, "word/");
            case "xlsx" -> startsWith(head, SIG_ZIP) && zipHasEntry(file, "xl/");
            case "hwpx" -> startsWith(head, SIG_ZIP) && (zipHasEntry(file, "Contents/") || zipHasEntry(file, "mimetype"));
            case "doc", "xls", "hwp" -> startsWith(head, SIG_OLE);
            case "jpg", "jpeg" -> startsWith(head, SIG_JPG);
            case "png" -> startsWith(head, SIG_PNG);
            default -> true;
        };
        if (!ok) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "파일 내용이 확장자(." + ext + ")와 맞지 않습니다. 파일이 손상됐거나 확장자만 바꾼 파일일 수 있습니다. 원본 파일을 다시 저장해 올려 주세요.");
        }
    }

    private static boolean startsWith(byte[] data, byte[] sig) {
        if (data.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (data[i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] sig) {
        outer:
        for (int i = 0; i + sig.length <= data.length; i++) {
            for (int j = 0; j < sig.length; j++) {
                if (data[i + j] != sig[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** ZIP 안에 이 이름으로 시작하는 항목이 있는지 (최대 5,000개 항목만 확인) */
    private static boolean zipHasEntry(MultipartFile file, String prefix) {
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry e;
            int n = 0;
            while ((e = z.getNextEntry()) != null && n++ < 5000) {
                if (e.getName().startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** 저장된 파일 읽기 */
    public byte[] read(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "저장된 파일을 찾을 수 없습니다.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("[personalinfo] 파일 읽기 실패: {}", relativePath, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일을 읽는 중 오류가 발생했습니다.");
        }
    }

    /** 업로드 루트 밖으로 벗어나는 경로(../ 등)를 차단한다 */
    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "잘못된 파일 경로입니다.");
        }
        return resolved;
    }

    public static String sanitizeOriginalName(String name) {
        String cleaned = StringUtils.getFilename(StringUtils.cleanPath(name == null ? "" : name.replace('\\', '/')));
        if (cleaned == null || cleaned.isBlank()) {
            return "file";
        }
        cleaned = cleaned.replaceAll("[\\r\\n\"<>|:*?]", "_").trim();
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    public static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType, String ext) {
        if (StringUtils.hasText(contentType) && !"application/octet-stream".equals(contentType)) {
            return contentType;
        }
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    /** 저장 결과 */
    public record StoredFile(String originalName, String relativePath, String contentType, long size, String sha256) {
    }

    /** 다운로드용 파일 */
    public record DownloadFile(String fileName, String contentType, byte[] data) {
    }
}
