package com.company.module.dailyreport.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.RemarkRequest;
import com.company.module.dailyreport.dto.RemarkResponse;
import com.company.module.dailyreport.service.DailyReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 특이사항 REST Controller
 * - 일보별 특이사항 메모 CRUD
 */
@RestController
@RequestMapping("/dailyreport-api/reports/{reportId}/remarks")
@RequiredArgsConstructor
public class RemarkController {

    private final DailyReportService dailyReportService;

    /**
     * 특이사항 목록 조회 (사업부별 5행 고정, 담당자/편집가능여부/최종저장자 포함)
     * GET /dailyreport-api/reports/{reportId}/remarks
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RemarkResponse>>> getRemarks(
            @PathVariable Long reportId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(dailyReportService.getRemarksForUser(reportId, userId)));
    }

    /**
     * 특이사항 추가
     * POST /dailyreport-api/reports/{reportId}/remarks
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RemarkResponse>> addRemark(
            @PathVariable Long reportId,
            @Valid @RequestBody RemarkRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(
                ApiResponse.created(dailyReportService.addRemark(reportId, request, userId)));
    }

    /**
     * 특이사항 수정
     * PUT /dailyreport-api/reports/{reportId}/remarks/{remarkId}
     */
    @PutMapping("/{remarkId}")
    public ResponseEntity<ApiResponse<RemarkResponse>> updateRemark(
            @PathVariable Long reportId,
            @PathVariable Long remarkId,
            @Valid @RequestBody RemarkRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(
                ApiResponse.success(dailyReportService.updateRemark(remarkId, request, userId)));
    }

    /**
     * 특이사항 삭제
     * DELETE /dailyreport-api/reports/{reportId}/remarks/{remarkId}
     */
    @DeleteMapping("/{remarkId}")
    public ResponseEntity<ApiResponse<Void>> deleteRemark(
            @PathVariable Long reportId,
            @PathVariable Long remarkId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        dailyReportService.deleteRemark(remarkId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
