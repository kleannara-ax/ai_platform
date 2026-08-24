package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.kims.dto.response.DashboardResponse;
import com.company.module.kims.dto.response.DashboardResponse.CountItem;
import com.company.module.kims.dto.response.DashboardResponse.MonthCount;
import com.company.module.kims.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 대시보드 REST API.
 * <p>URL prefix: {@code /kims-api/dashboard}
 * <p>집계/알림이 포함되므로 관리자(ADMIN) 또는 전산담당자(STAFF) 권한이 필요하다.
 */
@RestController
@RequestMapping("/kims-api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("@kimsPerm.canWork(authentication)")
public class DashboardController {

    private final DashboardService dashboardService;

    /** 대시보드 집계 전체 조회 (기준 연도 = 최신 연도) */
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboard()));
    }

    /** 특정 연도의 월별 접수 건수 (연도 선택 차트용) */
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<List<MonthCount>>> getMonthly(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getMonthly(year)));
    }

    /**
     * 요청유형별 건수 (도넛 차트용).
     * <p>year+month → 해당 월, year만 → 해당 연도 전체, 둘 다 생략 → 전체 기간.
     */
    @GetMapping("/by-type")
    public ResponseEntity<ApiResponse<List<CountItem>>> getTypeBreakdown(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getTypeBreakdown(year, month)));
    }
}
