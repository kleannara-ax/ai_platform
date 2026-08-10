package com.company.module.autodrawing.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.security.CustomUserDetails;
import com.company.module.autodrawing.service.VisionAnalyzeService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 자동도면 Vision AI 분석 컨트롤러
 *
 * POST /api/autodrawing/analyze — 이미지 분석 (OpenAI Vision)
 */
@Slf4j
@RestController
@RequestMapping("/api/autodrawing")
@RequiredArgsConstructor
public class AutodrawingAnalyzeController {

    private final VisionAnalyzeService visionService;

    /** 도면 이미지 분석 */
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<JsonNode>> analyze(
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal CustomUserDetails user) {

        if (image.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("이미지 파일이 필요합니다."));
        }

        try {
            log.info("[AutoDrawing] 도면 분석 요청 (user: {}, file: {}, size: {})",
                    user.getUsername(), image.getOriginalFilename(), image.getSize());

            JsonNode result = visionService.analyze(
                    image.getBytes(),
                    image.getContentType(),
                    image.getOriginalFilename());

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[AutoDrawing] 분석 오류: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500,
                            e.getMessage() != null ? e.getMessage() : "분석 중 오류가 발생했습니다."));
        }
    }
}
