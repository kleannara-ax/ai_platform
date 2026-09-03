package com.company.module.safety.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.safety.dto.response.StepPhotoResponse;
import com.company.module.safety.service.SafetyPhotoService;
import com.company.module.safety.service.SafetyPhotoService.ViewFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 매뉴얼 단계별 사진 REST API.
 * <ul>
 *   <li>업로드/삭제: 인증 + SAFETY 관리자만 가능 (JWT 보호, core 메인 SecurityFilterChain)</li>
 *   <li>조회(view): {@code <img>} 태그에서 바로 쓰므로 Authorization 헤더를 보낼 수 없다 —
 *       {@code /safety-api/photos/*}{@code /view} 경로만 {@code SafetySecurityConfig} 에서 공개 처리한다.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class SafetyPhotoController {

    private final SafetyPhotoService photoService;

    /** 사진 업로드 (multipart/form-data, 파트명 file) — SAFETY 관리자만 */
    @PostMapping("/safety-api/steps/{stepId}/photos")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<StepPhotoResponse>> upload(
            @PathVariable Long stepId, @RequestParam("file") MultipartFile file, Authentication authentication) {
        String uploadedBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.created(photoService.upload(stepId, file, uploadedBy)));
    }

    /** 사진 조회 (화면 표시용, 공개) */
    @GetMapping("/safety-api/photos/{photoId}/view")
    public ResponseEntity<byte[]> view(@PathVariable Long photoId) {
        ViewFile f = photoService.loadForView(photoId);
        MediaType type = (f.contentType() != null)
                ? MediaType.parseMediaType(f.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(type)
                .body(f.data());
    }

    /** 사진 삭제 — SAFETY 관리자만 */
    @DeleteMapping("/safety-api/photos/{photoId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long photoId, Authentication authentication) {
        String deletedBy = (authentication != null) ? authentication.getName() : null;
        photoService.delete(photoId, deletedBy);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
