package com.company.module.user.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.user.dto.DepartmentCreateRequest;
import com.company.module.user.dto.DepartmentResponse;
import com.company.module.user.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 부서 관리 API
 * URL Prefix: /api/module-common/departments
 * → 모듈 단위 API Prefix 적용
 */
@RestController
@RequestMapping("/api/module-common/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * 부서 생성
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody DepartmentCreateRequest request) {
        DepartmentResponse response = departmentService.createDepartment(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * 부서 단건 조회
     */
    @GetMapping("/{deptId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartment(
            @PathVariable Long deptId) {
        DepartmentResponse response = departmentService.getDepartment(deptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 활성화된 전체 부서 목록 조회
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAllDepartments() {
        List<DepartmentResponse> response = departmentService.getAllActiveDepartments();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 하위 부서 조회
     */
    @GetMapping("/{parentDeptId}/children")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getChildDepartments(
            @PathVariable Long parentDeptId) {
        List<DepartmentResponse> response = departmentService.getChildDepartments(parentDeptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 부서 비활성화
     */
    @PatchMapping("/{deptId}/disable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> disableDepartment(@PathVariable Long deptId) {
        departmentService.disableDepartment(deptId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
