package com.company.module.security_log.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.security_log.dto.DashboardResponse;
import com.company.module.security_log.dto.MonthlyStatusResponse;
import com.company.module.security_log.dto.OriginalFileResponse;
import com.company.module.security_log.service.SecLogMonthlyReportService;
import com.company.module.security_log.service.SecLogUploadService;
import com.company.module.security_log.dto.SystemResponse;
import com.company.module.security_log.dto.SystemSaveRequest;
import com.company.module.security_log.dto.SystemUseYnRequest;
import com.company.module.security_log.service.SecLogDashboardService;
import com.company.module.security_log.service.SecLogMonthlyStatusService;
import com.company.module.security_log.service.SecLogSystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 점검 대상 시스템 마스터 + 월별 점검 현황 API
 * - 조회: ADMIN, MANAGER, USER
 * - 시스템 등록/수정/삭제: ADMIN
 */
@RestController
@RequestMapping("/security_log-api")
@RequiredArgsConstructor
public class SecLogSystemController {

    private final SecLogSystemService systemService;
    private final SecLogMonthlyStatusService monthlyStatusService;
    private final SecLogDashboardService dashboardService;
    private final SecLogMonthlyReportService reportService;
    private final SecLogUploadService uploadService;

    @GetMapping("/systems")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<SystemResponse>>> getSystems(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(systemService.getList(activeOnly)));
    }

    @PostMapping("/systems")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemResponse>> create(@Valid @RequestBody SystemSaveRequest request,
                                                              @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(systemService.create(request, userId)));
    }

    @PutMapping("/systems/{systemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemResponse>> update(@PathVariable Long systemId,
                                                              @Valid @RequestBody SystemSaveRequest request,
                                                              @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(systemService.update(systemId, request, userId)));
    }

    /** 사용/미사용 토글 */
    @PatchMapping("/systems/{systemId}/use-yn")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemResponse>> changeUseYn(@PathVariable Long systemId,
                                                                   @Valid @RequestBody SystemUseYnRequest request,
                                                                   @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(systemService.changeUseYn(systemId, request.getUseYn(), userId)));
    }

    @DeleteMapping("/systems/{systemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long systemId,
                                                    @AuthenticationPrincipal(expression = "userId") Long userId) {
        systemService.delete(systemId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 월별 시스템별 점검 현황 (접속 행위로그 + 장기 미접속 계정) */
    @GetMapping("/monthly-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<MonthlyStatusResponse>> getMonthlyStatus(@RequestParam String targetYm) {
        return ResponseEntity.ok(ApiResponse.success(monthlyStatusService.getStatus(targetYm)));
    }

    /** ISMS 로그 점검 대시보드 (메뉴 첫 화면) */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(@RequestParam(required = false) String targetYm) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.get(targetYm)));
    }

    /**
     * 월 점검 결과 내보내기 (엑셀, ISMS 증적)
     * - ApiResponse 규칙에 맞춰 Base64 로 전달 (원본 파일 다운로드와 같은 방식)
     */
    @GetMapping("/dashboard/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<OriginalFileResponse>> exportMonthly(
            @RequestParam(required = false) String targetYm,
            @AuthenticationPrincipal(expression = "username") String loginId) {
        SecLogMonthlyReportService.ReportFile f = reportService.export(targetYm, loginId);
        return ResponseEntity.ok(ApiResponse.success(OriginalFileResponse.builder()
                .fileName(f.fileName()).fileSize(f.content().length)
                .contentBase64(java.util.Base64.getEncoder().encodeToString(f.content())).build()));
    }

    /** 일별 집계가 없는 업로드(06 스크립트 적용 전 등록분)의 일별 집계 만들기 */
    @PostMapping("/dashboard/rebuild-daily")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> rebuildDaily(
            @RequestParam String targetYm,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("rebuilt", uploadService.rebuildDailyIfMissing(targetYm, userId))));
    }

    /** 대시보드 로그 수집 → 시스템 상세 (이번 달 접속·행위 로그 / 관리 계정 목록) */
    @GetMapping("/dashboard/systems/{systemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<com.company.module.security_log.dto.SystemLogResponse>> getSystemLogs(
            @PathVariable Long systemId, @RequestParam(required = false) String targetYm) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.systemLogs(systemId, targetYm)));
    }
}
