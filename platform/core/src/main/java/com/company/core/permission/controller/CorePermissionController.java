package com.company.core.permission.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.permission.dto.*;
import com.company.core.permission.service.CorePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/core/permissions")
@RequiredArgsConstructor
public class CorePermissionController {

    private final CorePermissionService permService;

    /** 전체 권한 목록 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(permService.getAllPermissions()));
    }

    /** 권한 상세 */
    @GetMapping("/{permId}")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermission(@PathVariable Long permId) {
        return ResponseEntity.ok(ApiResponse.success(permService.getPermission(permId)));
    }

    /** 권한 생성 (ADMIN) */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(@Valid @RequestBody PermissionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(permService.createPermission(req)));
    }

    /** 권한 수정 (ADMIN) */
    @PutMapping("/{permId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable Long permId, @Valid @RequestBody PermissionRequest req) {
        return ResponseEntity.ok(ApiResponse.success(permService.updatePermission(permId, req)));
    }

    /** 모든 역할별 매핑 조회 */
    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleMappingResponse>>> getAllRoleMappings() {
        return ResponseEntity.ok(ApiResponse.success(permService.getAllRoleMappings()));
    }

    /** 특정 역할 매핑 조회 */
    @GetMapping("/roles/{role}")
    public ResponseEntity<ApiResponse<RoleMappingResponse>> getRoleMapping(@PathVariable String role) {
        return ResponseEntity.ok(ApiResponse.success(permService.getRoleMapping(role)));
    }

    /** 역할별 메뉴/권한 매핑 갱신 (ADMIN) */
    @PutMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleMappingResponse>> updateRoleMapping(
            @Valid @RequestBody RoleMappingRequest req) {
        return ResponseEntity.ok(ApiResponse.success(permService.updateRoleMapping(req)));
    }
}
