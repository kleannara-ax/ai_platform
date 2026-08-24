package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.QrLocationCreateRequest;
import com.company.module.kims.dto.request.QrLocationUpdateRequest;
import com.company.module.kims.dto.response.QrLocationResponse;
import com.company.module.kims.service.QrLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

/**
 * QR 구역 관리 REST API (관리자).
 * <p>URL prefix: {@code /kims-api/qr-locations}
 */
@RestController
@RequestMapping("/kims-api/qr-locations")
@RequiredArgsConstructor
public class QrLocationController {

    private final QrLocationService qrLocationService;

    /**
     * QR 코드가 가리킬 외부 베이스 URL (예: https://kims.example.com).
     * 비어 있으면 현재 요청의 호스트로 자동 구성한다. (운영 시 KIMS_QR_BASE_URL 로 주입)
     */
    @Value("${kims.qr.base-url:}")
    private String configuredBaseUrl;

    /** QR 구역 생성 (위치·부서 입력) */
    @PostMapping
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<QrLocationResponse>> create(@Valid @RequestBody QrLocationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(qrLocationService.create(request)));
    }

    /** QR 구역 목록 (구역명/위치/부서 검색) */
    @GetMapping
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<PageResponse<QrLocationResponse>>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(qrLocationService.getList(keyword, page, size)));
    }

    /** QR 구역 상세 (인코딩 URL 포함) */
    @GetMapping("/{qrId}")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDetail(@PathVariable Long qrId) {
        QrLocationResponse data = qrLocationService.getDetail(qrId);
        Map<String, Object> body = Map.of(
                "location", data,
                "url", qrLocationService.buildUrl(qrId, baseUrl()),
                "imagePath", "/kims-api/qr-locations/" + qrId + "/image");
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** QR 구역 수정 */
    @PatchMapping("/{qrId}")
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<QrLocationResponse>> update(
            @PathVariable Long qrId, @Valid @RequestBody QrLocationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(qrLocationService.update(qrId, request)));
    }

    /** QR 구역 삭제 */
    @DeleteMapping("/{qrId}")
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long qrId) {
        qrLocationService.delete(qrId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** QR 이미지(PNG) — 화면에서 표시/다운로드/인쇄 */
    @GetMapping("/{qrId}/image")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<byte[]> image(@PathVariable Long qrId) {
        byte[] png = qrLocationService.generatePng(qrId, baseUrl());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache())
                .body(png);
    }

    /** QR 베이스 URL: 설정값이 있으면 사용, 없으면 현재 요청 호스트 기준 */
    private String baseUrl() {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.replaceAll("/+$", "");
        }
        // 예: http://localhost:8080
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
