package com.company.module.dailyreport.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.CellPermissionRequest;
import com.company.module.dailyreport.dto.CellPermissionResponse;
import com.company.module.dailyreport.service.CellPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 셀 편집 권한 관리 REST Controller
 * - 관리자가 사용자별 편집 가능 셀 범위를 설정
 */
@RestController
@RequestMapping("/dailyreport-api/permissions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CellPermissionController {

    private final CellPermissionService cellPermissionService;

    /**
     * 사용자별 권한 조회
     * GET /dailyreport-api/permissions?userId=1
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CellPermissionResponse>>> getPermissions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String tableCode) {
        if (userId != null) {
            return ResponseEntity.ok(
                    ApiResponse.success(cellPermissionService.getPermissionsByUser(userId)));
        }
        if (tableCode != null) {
            return ResponseEntity.ok(
                    ApiResponse.success(cellPermissionService.getPermissionsByTable(tableCode)));
        }
        // 둘 다 없으면 빈 목록
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    /**
     * 권한 등록
     * POST /dailyreport-api/permissions
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CellPermissionResponse>> createPermission(
            @Valid @RequestBody CellPermissionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.created(cellPermissionService.createPermission(request)));
    }

    /**
     * 권한 수정
     * PUT /dailyreport-api/permissions/{permissionId}
     */
    @PutMapping("/{permissionId}")
    public ResponseEntity<ApiResponse<CellPermissionResponse>> updatePermission(
            @PathVariable Long permissionId,
            @Valid @RequestBody CellPermissionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(cellPermissionService.updatePermission(permissionId, request)));
    }

    /**
     * 권한 비활성화 (논리 삭제)
     * PATCH /dailyreport-api/permissions/{permissionId}/deactivate
     */
    @PatchMapping("/{permissionId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivatePermission(
            @PathVariable Long permissionId) {
        cellPermissionService.deactivatePermission(permissionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 권한 삭제 (물리 삭제)
     * DELETE /dailyreport-api/permissions/{permissionId}
     */
    @DeleteMapping("/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> deletePermission(
            @PathVariable Long permissionId) {
        cellPermissionService.deletePermission(permissionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
