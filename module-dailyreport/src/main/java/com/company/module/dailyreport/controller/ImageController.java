package com.company.module.dailyreport.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.ImageResponse;
import com.company.module.dailyreport.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 이미지 첨부 REST Controller
 * - 일보별 이미지 추가·미리보기·삭제
 * - 실제 파일 업로드는 플랫폼의 공통 파일 업로드 API를 사용하고,
 *   이 컨트롤러에서는 메타 정보만 관리
 */
@RestController
@RequestMapping("/dailyreport-api/reports/{reportId}/images")
@RequiredArgsConstructor
public class ImageController {

    private final DailyReportService dailyReportService;

    /**
     * 이미지 목록 조회
     * GET /dailyreport-api/reports/{reportId}/images
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ImageResponse>>> getImages(
            @PathVariable Long reportId) {
        return ResponseEntity.ok(ApiResponse.success(dailyReportService.getImages(reportId)));
    }

    /**
     * 이미지 메타 등록 (파일 업로드 후 메타 정보 저장)
     * POST /dailyreport-api/reports/{reportId}/images
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ImageResponse>> addImage(
            @PathVariable Long reportId,
            @RequestParam String originalName,
            @RequestParam String storedPath,
            @RequestParam Long fileSize,
            @RequestParam String contentType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tableCode,
            @AuthenticationPrincipal(expression = "id") Long userId) {
        return ResponseEntity.ok(
                ApiResponse.created(dailyReportService.addImage(
                        reportId, originalName, storedPath, fileSize,
                        contentType, description, tableCode, userId)));
    }

    /**
     * 이미지 설명 수정
     * PATCH /dailyreport-api/reports/{reportId}/images/{imageId}
     */
    @PatchMapping("/{imageId}")
    public ResponseEntity<ApiResponse<ImageResponse>> updateImageDescription(
            @PathVariable Long reportId,
            @PathVariable Long imageId,
            @RequestParam String description) {
        return ResponseEntity.ok(
                ApiResponse.success(dailyReportService.updateImageDescription(imageId, description)));
    }

    /**
     * 이미지 삭제
     * DELETE /dailyreport-api/reports/{reportId}/images/{imageId}
     */
    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long reportId,
            @PathVariable Long imageId) {
        dailyReportService.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
