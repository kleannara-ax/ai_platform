package com.company.module.security_log.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.security_log.dto.DetectionResponse;
import com.company.module.security_log.dto.DetectionReviewRequest;
import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.service.SecLogDetectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 이상징후 탐지 결과 조회 / 검토 API
 * - 조회: ADMIN, MANAGER, USER
 * - 검토/조치 등록: ADMIN, MANAGER
 */
@RestController
@RequestMapping("/security_log-api")
@RequiredArgsConstructor
public class SecLogDetectionController {

    private final SecLogDetectionService detectionService;

    @GetMapping("/uploads/{uploadId}/detections")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<Page<DetectionResponse>>> getList(
            @PathVariable Long uploadId,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                detectionService.getList(uploadId, severity, reviewStatus, page, size)));
    }

    @GetMapping("/detections/{detectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DetectionResponse>> getDetail(@PathVariable Long detectionId) {
        return ResponseEntity.ok(ApiResponse.success(detectionService.getDetail(detectionId)));
    }

    /** 탐지 건 검토/조치 등록 (단건/다건 일괄) */
    @PutMapping("/uploads/{uploadId}/detections/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<DetectionResponse>>> review(
            @PathVariable Long uploadId,
            @Valid @RequestBody DetectionReviewRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {

        return ResponseEntity.ok(ApiResponse.success(detectionService.review(uploadId, request, userId)));
    }
}
