package com.company.module.cckb.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.cckb.dto.response.InspectionRecordResponse;
import com.company.module.cckb.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * "색상 검사" 탭(검사 실행) + "검사 이력" 탭 API.
 * 원본 V4 HTML 의 analyze()/getFilteredHistory()/renderHistoryList()/startReinspect() 를 이관.
 */
@RestController
@RequestMapping("/cckb-api/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    /**
     * 색상 검사 실행 (업로드 + 분석 + 판정 + 이력 저장을 한 번에 수행)
     * POST /cckb-api/inspections/analyze (multipart/form-data)
     *
     * @param file                 비교(CMP) 이미지
     * @param originalInspectionId 재검사인 경우 원본 검사 ID (data-seqno, 🔁 재검사 버튼에서 전달)
     */
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<InspectionRecordResponse>> analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String originalInspectionId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(
                inspectionService.analyze(file, originalInspectionId, userId)));
    }

    /**
     * 검사 이력 조회(검색 + 기간 필터 + 페이지네이션)
     * GET /cckb-api/inspections?keyword=&from=&to=&page=0&size=10
     * (원본 V4 HTML HISTORY_PAGE_SIZE=10 과 동일한 기본 size)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InspectionRecordResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                inspectionService.search(keyword, from, to, page, size)));
    }

    /** GET /cckb-api/inspections/{inspectionId} — 검사 1건 상세(재검사 대상 조회 등에 사용) */
    @GetMapping("/{inspectionId}")
    public ResponseEntity<ApiResponse<InspectionRecordResponse>> getById(@PathVariable String inspectionId) {
        return ResponseEntity.ok(ApiResponse.success(inspectionService.getById(inspectionId)));
    }
}
