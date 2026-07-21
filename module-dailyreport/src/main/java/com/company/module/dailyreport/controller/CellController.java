package com.company.module.dailyreport.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.CellResponse;
import com.company.module.dailyreport.dto.CellSaveRequest;
import com.company.module.dailyreport.dto.ReportTableResponse;
import com.company.module.dailyreport.service.CellService;
import com.company.module.dailyreport.service.MenuPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 셀 데이터 입력/조회 REST Controller (★ Phase 4 개선)
 *
 * 변경사항:
 * - 1계층 권한 확인: MenuPermissionService로 입력 페이지 접근 권한 사전 검증
 * - CellAuth 기반 셀 권한 (CellService에서 내부 처리)
 */
@RestController
@RequestMapping("/dailyreport-api/reports/{reportId}/cells")
@RequiredArgsConstructor
public class CellController {

    private final CellService cellService;
    private final MenuPermissionService menuPermissionService;

    /**
     * 표별 셀 데이터 조회 (사용자 기준 편집 가능 여부 포함)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ReportTableResponse>> getTableData(
            @PathVariable Long reportId,
            @RequestParam String tableCode,
            @AuthenticationPrincipal(expression = "id") Long userId,
            @AuthenticationPrincipal(expression = "username") String loginId) {

        // ★ 1계층: 입력 페이지 접근 권한 확인
        verifyInputPageAccess(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        cellService.getTableDataForUser(reportId, tableCode, userId, loginId)));
    }

    /**
     * 셀 값 일괄 저장 (권한 검증 포함)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<List<CellResponse>>> saveCells(
            @PathVariable Long reportId,
            @Valid @RequestBody CellSaveRequest request,
            @AuthenticationPrincipal(expression = "id") Long userId,
            @AuthenticationPrincipal(expression = "username") String loginId) {

        // ★ 1계층: 입력 페이지 쓰기 권한 확인
        if (!menuPermissionService.canWriteInputPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "세부공장일보 입력 페이지에 대한 쓰기 권한이 없습니다.");
        }

        return ResponseEntity.ok(
                ApiResponse.success(
                        cellService.saveCells(reportId, request, userId, loginId)));
    }

    /**
     * 입력 주기별 셀 잠금/해제 (관리자 전용)
     */
    @PatchMapping("/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> toggleCellLock(
            @PathVariable Long reportId,
            @RequestParam Long tableId,
            @RequestParam String inputCycle,
            @RequestParam boolean locked) {
        int affected = cellService.toggleCellLockByCycle(tableId, inputCycle, locked);
        return ResponseEntity.ok(ApiResponse.success(affected));
    }

    // ─────────────────────────────────────────────

    private void verifyInputPageAccess(Long userId) {
        if (!menuPermissionService.canAccessInputPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "세부공장일보 입력 페이지에 대한 접근 권한이 없습니다.");
        }
    }
}
