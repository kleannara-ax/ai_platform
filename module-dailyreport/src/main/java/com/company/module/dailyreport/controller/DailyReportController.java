package com.company.module.dailyreport.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.DailyReportRequest;
import com.company.module.dailyreport.dto.DailyReportResponse;
import com.company.module.dailyreport.service.DailyReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 세부공장일보 REST Controller
 * - 일보 CRUD, 상태 관리
 */
@RestController
@RequestMapping("/dailyreport-api/reports")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailyReportService dailyReportService;

    /**
     * 일보 목록 조회 (기간·상태 필터 + 페이징)
     * GET /dailyreport-api/reports?startDate=2024-07-01&endDate=2024-07-31&status=DRAFT&page=0&size=50
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<DailyReportResponse>>> getReportList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                ApiResponse.success(dailyReportService.getReportList(startDate, endDate, status, page, size)));
    }

    /**
     * 일보 상세 조회 (표 + 셀 + 특이사항 + 이미지 모두 포함)
     * GET /dailyreport-api/reports/{reportId}
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<DailyReportResponse>> getReport(
            @PathVariable Long reportId) {
        return ResponseEntity.ok(ApiResponse.success(dailyReportService.getReport(reportId)));
    }

    /**
     * 날짜별 일보 조회
     * GET /dailyreport-api/reports/by-date?date=2024-07-20
     */
    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse<DailyReportResponse>> getReportByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(dailyReportService.getReportByDate(date)));
    }

    /**
     * 일보 생성 (5개 기본 표 자동 생성)
     * POST /dailyreport-api/reports
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DailyReportResponse>> createReport(
            @Valid @RequestBody DailyReportRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(dailyReportService.createReport(request, userId)));
    }

    /**
     * 일보 상태 변경 (DRAFT → SUBMITTED → CONFIRMED)
     * PATCH /dailyreport-api/reports/{reportId}/status?status=SUBMITTED
     */
    @PatchMapping("/{reportId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DailyReportResponse>> updateStatus(
            @PathVariable Long reportId,
            @RequestParam String status,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(
                ApiResponse.success(dailyReportService.updateReportStatus(reportId, status, userId)));
    }

    /**
     * 일보 삭제 (DRAFT 상태만)
     * DELETE /dailyreport-api/reports/{reportId}
     */
    @DeleteMapping("/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReport(@PathVariable Long reportId) {
        dailyReportService.deleteReport(reportId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
