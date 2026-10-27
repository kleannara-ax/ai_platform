package com.company.module.personalinfo.controller;

import com.company.module.personalinfo.service.PiFileStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * 파일 다운로드 응답 생성.
 *
 * <p>파일(바이너리) 다운로드는 JSON 이 아니므로 ApiResponse 로 감싸지 않는다.
 * (플랫폼 module-kims 첨부파일 다운로드와 같은 방식. 오류 시에는 core GlobalExceptionHandler 가 ApiResponse 로 응답)
 * 한글 파일명은 RFC 5987(filename*=UTF-8'') 로 인코딩한다.
 */
final class PiDownloads {

    private PiDownloads() {
    }

    static ResponseEntity<byte[]> of(PiFileStorage.DownloadFile f) {
        MediaType type;
        try {
            type = f.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(f.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .contentType(type)
                .contentLength(f.data().length)
                .body(f.data());
    }
}
