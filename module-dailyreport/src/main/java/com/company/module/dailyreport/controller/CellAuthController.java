package com.company.module.dailyreport.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.CellAuthRequest;
import com.company.module.dailyreport.dto.CellAuthResponse;
import com.company.module.dailyreport.service.CellAuthService;
import com.company.module.dailyreport.service.MenuPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 셀 접근 권한 관리 REST Controller (★ Phase 4 신규)
 * - '세부공장일보 접근권한' 페이지의 백엔드 API
 * - 관리자가 사용자별 담당 셀 좌표 / 입력 주기를 설정·수정·삭제
 * - 접근 권한: core_menu_permission에서 DAILY_REPORT_AUTH (MENU_ID=102) 확인
 *
 * 엔드포인트:
 *   GET    /dailyreport-api/cell-auths                  → 전체 조회
 *   GET    /dailyreport-api/cell-auths?userId=2         → 사용자별 조회
 *   GET    /dailyreport-api/cell-auths?tableCode=TBL_XX → 표별 조회
 *   GET    /dailyreport-api/cell-auths/{authId}         → 단건 조회
 *   POST   /dailyreport-api/cell-auths                  → 등록
 *   PUT    /dailyreport-api/cell-auths/{authId}         → 수정
 *   PATCH  /dailyreport-api/cell-auths/{authId}/deactivate → 비활성화
 *   DELETE /dailyreport-api/cell-auths/{authId}         → 삭제
 */
@RestController
@RequestMapping("/dailyreport-api/cell-auths")
@RequiredArgsConstructor
public class CellAuthController {

    private final CellAuthService cellAuthService;
    private final MenuPermissionService menuPermissionService;

    /**
     * 접근권한 관리 페이지 접근 가능 여부 확인 + 전체/필터 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CellAuthResponse>>> getAuths(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String tableCode,
            @AuthenticationPrincipal(expression = "id") Long currentUserId) {

        verifyAuthPageAccess(currentUserId);

        if (userId != null) {
            return ResponseEntity.ok(
                    ApiResponse.success(cellAuthService.getAuthsByUser(userId)));
        }
        if (tableCode != null) {
            return ResponseEntity.ok(
                    ApiResponse.success(cellAuthService.getAuthsByTable(tableCode)));
        }
        return ResponseEntity.ok(
                ApiResponse.success(cellAuthService.getAllActiveAuths()));
    }

    /**
     * 단건 조회
     */
    @GetMapping("/{authId}")
    public ResponseEntity<ApiResponse<CellAuthResponse>> getAuth(
            @PathVariable Long authId,
            @AuthenticationPrincipal(expression = "id") Long currentUserId) {

        verifyAuthPageAccess(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(cellAuthService.getAuth(authId)));
    }

    /**
     * 셀 권한 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CellAuthResponse>> createAuth(
            @Valid @RequestBody CellAuthRequest request,
            @AuthenticationPrincipal(expression = "id") Long currentUserId) {

        verifyAuthPageAdmin(currentUserId);
        return ResponseEntity.ok(
                ApiResponse.created(cellAuthService.createAuth(request, currentUserId)));
    }

    /**
     * 셀 권한 수정
     */
    @PutMapping("/{authId}")
    public ResponseEntity<ApiResponse<CellAuthResponse>> updateAuth(
            @PathVariable Long authId,
            @Valid @RequestBody CellAuthRequest request,
            @AuthenticationPrincipal(expression = "id") Long currentUserId) {

        verifyAuthPageAdmin(currentUserId);
        return ResponseEntity.ok(
                ApiResponse.success(cellAuthService.updateAuth(authId, request, currentUserId)));
    }

    /**
     * 셀 권한 비활성화 (논리 삭제)
     */
    @PatchMapping("/{authId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateAuth(
            @PathVariable Long authId,
            @AuthenticationPrincipal(expression = "id") Long currentUserId) {

        verifyAuthPageAdmin(currentUserId);
        cellAuthService.deactivateAuth(authId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 셀 권한 삭제 (물리 삭제)
     */
    @DeleteMapping("/{authId}")
    public ResponseEntity<ApiResponse<Void>> deleteAuth(
            @PathVariable Long authId,
            @AuthenticationPrincipal(expression = "id") Long currentUserId) {

        verifyAuthPageAdmin(currentUserId);
        cellAuthService.deleteAuth(authId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ─────────────────────────────────────────────
    // 내부: 페이지 접근 권한 검증
    // ─────────────────────────────────────────────

    private void verifyAuthPageAccess(Long userId) {
        if (!menuPermissionService.canAccessAuthPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "세부공장일보 접근권한 페이지에 대한 접근 권한이 없습니다.");
        }
    }

    private void verifyAuthPageAdmin(Long userId) {
        if (!menuPermissionService.canAdminAuthPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "셀 권한 관리에 대한 관리자 권한이 없습니다.");
        }
    }
}
