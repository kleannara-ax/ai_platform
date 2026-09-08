package com.company.module.safety.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 엑셀 파일을 <b>여러 조각으로 나눠 받아</b> 서버에 하나로 이어 붙이는 임시 보관소.
 *
 * <p>왜 필요한가: 앞단 웹서버(nginx {@code client_max_body_size} 기본 10M 등)가 요청 본문 크기를
 * 제한하고 있어, 사진이 많은 50~90MB 짜리 매뉴얼 파일을 한 번에 올리면 애플리케이션에 닿기도 전에
 * 413 으로 잘린다. 웹서버 설정을 바꿀 수 없는 환경을 위해, 브라우저가 파일을 잘라 여러 번 보내고
 * 여기서 이어 붙인다. 요청 하나하나는 작으므로 어떤 제한에도 걸리지 않는다.
 *
 * <p>덤으로 미리보기(1단계)와 확정(2단계)이 <b>같은 임시 파일</b>을 재사용하므로,
 * 기존처럼 같은 파일을 두 번 전송하지 않아도 된다.
 *
 * <p>보관 위치는 {@code safety.upload-dir/_excel-staging} 이고, 확정이 끝나면 지운다.
 * 중간에 브라우저를 닫아 버려진 조각은 다음 업로드가 시작될 때 오래된 것부터 청소한다.
 */
@Service
@RequiredArgsConstructor
public class SafetyExcelStagingService {

    /** 임시 조각을 모아 두는 하위 디렉토리 (사진 저장 경로와 섞이지 않게 분리) */
    private static final String STAGING_DIR = "_excel-staging";
    /** 버려진 임시 파일을 정리하는 기준 시간 */
    private static final Duration STALE_AFTER = Duration.ofHours(6);
    /** 조각 개수 상한 (4MB 씩이면 400MB 분량 — 사실상 사고 방지용) */
    private static final int MAX_CHUNKS = 100;

    @Value("${safety.upload-dir}")
    private String uploadDir;

    /** 1회 업로드 총 용량 상한 */
    @Value("${safety.excel.max-upload-size:157286400}")
    private long maxUploadSize;

    /** 진행 중인 업로드 상태. 인스턴스 1개로 운영하므로 메모리에 들고 있어도 충분하다. */
    private final Map<String, Staged> staged = new HashMap<>();

    /**
     * 조각 하나를 받아 임시 파일 뒤에 붙인다.
     *
     * @param uploadId    첫 조각이면 null/빈 값 — 새로 발급해 돌려준다
     * @param chunkIndex  0 부터 순서대로. 순서가 어긋나면 거부한다(이어붙이기라 순서가 곧 내용이다)
     * @param owner       업로드를 시작한 로그인 ID — 다른 사용자가 남의 임시 파일을 건드리지 못하게 한다
     */
    public synchronized Staged appendChunk(String uploadId, int chunkIndex, int totalChunks,
                                           String fileName, MultipartFile chunk, String owner) {
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 조각이 비어 있습니다.");
        }
        if (totalChunks < 1 || totalChunks > MAX_CHUNKS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "조각 개수가 올바르지 않습니다. (" + totalChunks + "개, 최대 " + MAX_CHUNKS + "개)");
        }

        Staged entry;
        if (uploadId == null || uploadId.isBlank()) {
            sweepStale();
            entry = create(totalChunks, fileName, owner);
        } else {
            entry = find(uploadId, owner);
            if (entry.totalChunks != totalChunks) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "업로드 도중 조각 개수가 바뀌었습니다. 파일을 다시 선택해 주세요.");
            }
        }

        if (chunkIndex != entry.receivedChunks) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "조각 순서가 어긋났습니다. (기대 " + entry.receivedChunks + ", 받은 값 " + chunkIndex + ") "
                            + "파일을 다시 선택해 주세요.");
        }
        if (entry.bytes + chunk.getSize() > maxUploadSize) {
            discard(entry.uploadId, owner);
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "업로드 가능한 크기를 넘었습니다. (최대 " + (maxUploadSize / 1024 / 1024) + "MB)");
        }

        try (InputStream in = chunk.getInputStream();
             OutputStream out = Files.newOutputStream(entry.path, StandardOpenOption.APPEND)) {
            in.transferTo(out);
        } catch (IOException e) {
            discard(entry.uploadId, owner);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "업로드 조각을 저장하지 못했습니다: " + e.getMessage());
        }

        entry.receivedChunks++;
        entry.bytes += chunk.getSize();
        return entry;
    }

    /** 조각이 모두 도착한 임시 파일의 경로. 아직 덜 왔으면 거부한다. */
    public synchronized Staged complete(String uploadId, String owner) {
        Staged entry = find(uploadId, owner);
        if (entry.receivedChunks < entry.totalChunks) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "업로드가 끝나지 않았습니다. (" + entry.receivedChunks + "/" + entry.totalChunks + ") "
                            + "파일을 다시 선택해 주세요.");
        }
        return entry;
    }

    /** 임시 파일과 상태를 지운다. 없으면 조용히 넘어간다. */
    public synchronized void discard(String uploadId, String owner) {
        if (uploadId == null || uploadId.isBlank()) return;
        Staged entry = staged.remove(normalizeId(uploadId));
        if (entry == null) return;
        if (owner != null && !owner.equals(entry.owner)) {
            staged.put(entry.uploadId, entry);   // 남의 것은 되돌려 놓는다
            return;
        }
        deleteQuietly(entry.path);
    }

    // ----------------------------------------------------------------
    // 내부
    // ----------------------------------------------------------------

    private Staged create(int totalChunks, String fileName, String owner) {
        String uploadId = UUID.randomUUID().toString();
        Path path = stagingDir().resolve(uploadId + ".xlsx");
        try {
            Files.createDirectories(stagingDir());
            Files.createFile(path);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "임시 파일을 만들지 못했습니다: " + e.getMessage());
        }
        Staged entry = new Staged(uploadId, path, totalChunks,
                (fileName != null && !fileName.isBlank()) ? fileName : "upload.xlsx", owner);
        staged.put(uploadId, entry);
        return entry;
    }

    private Staged find(String uploadId, String owner) {
        Staged entry = staged.get(normalizeId(uploadId));
        if (entry == null || !Files.exists(entry.path)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "업로드 정보를 찾을 수 없습니다. 시간이 오래 지났거나 서버가 재시작되었습니다. 파일을 다시 선택해 주세요.");
        }
        if (owner != null && !owner.equals(entry.owner)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "다른 사용자의 업로드입니다.");
        }
        return entry;
    }

    /**
     * uploadId 는 반드시 UUID 다. 파일명을 UUID 로 다시 만들어 쓰므로
     * {@code ../} 같은 경로 조작이 끼어들 수 없다.
     */
    private String normalizeId(String uploadId) {
        try {
            return UUID.fromString(uploadId.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드 식별자 형식이 올바르지 않습니다.");
        }
    }

    private Path stagingDir() {
        return Paths.get(uploadDir, STAGING_DIR);
    }

    /** 브라우저를 닫는 등으로 버려진 임시 파일을 정리한다. (새 업로드가 시작될 때마다) */
    private void sweepStale() {
        Instant deadline = Instant.now().minus(STALE_AFTER);

        Iterator<Staged> it = staged.values().iterator();
        while (it.hasNext()) {
            Staged entry = it.next();
            if (entry.createdAt.isBefore(deadline)) {
                deleteQuietly(entry.path);
                it.remove();
            }
        }
        // 서버 재시작으로 상태 맵이 비어도 파일은 남으므로, 디스크도 함께 훑는다.
        if (!Files.isDirectory(stagingDir())) return;
        try (Stream<Path> files = Files.list(stagingDir())) {
            files.filter(Files::isRegularFile)
                 .filter(p -> isOlderThan(p, deadline))
                 .forEach(this::deleteQuietly);
        } catch (IOException ignored) {
            // 정리는 부가 작업이라 실패해도 업로드는 진행한다
        }
    }

    private boolean isOlderThan(Path path, Instant deadline) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(deadline);
        } catch (IOException e) {
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 남아 있어도 다음 청소 때 다시 시도한다
        }
    }

    /** 진행 중이거나 완료된 업로드 하나의 상태 */
    public static final class Staged {
        private final String uploadId;
        private final Path path;
        private final int totalChunks;
        private final String fileName;
        private final String owner;
        private final Instant createdAt = Instant.now();
        private int receivedChunks;
        private long bytes;

        private Staged(String uploadId, Path path, int totalChunks, String fileName, String owner) {
            this.uploadId = uploadId;
            this.path = path;
            this.totalChunks = totalChunks;
            this.fileName = fileName;
            this.owner = owner;
        }

        public String getUploadId() { return uploadId; }
        public Path getPath() { return path; }
        public int getTotalChunks() { return totalChunks; }
        public int getReceivedChunks() { return receivedChunks; }
        public String getFileName() { return fileName; }
        public boolean isCompleted() { return receivedChunks >= totalChunks; }
    }
}
