package com.company.app.user.controller;

import com.company.app.user.dto.IntegratedUserResponse;
import com.company.app.user.service.IntegratedUserService;
import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.service.CoreUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 통합 사용자 관리 API
 * URL: /api/integrated/users
 * 사용자 기본정보 + 프로필(부서, 직급, 사번 등)을 하나의 API로 처리
 */
@RestController
@RequestMapping("/api/integrated/users")
@RequiredArgsConstructor
public class IntegratedUserController {

    private final IntegratedUserService integratedUserService;
    private final CoreUserService coreUserService;

    /** 사용자 목록 (프로필 포함) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IntegratedUserResponse>>> getUsers(
            @PageableDefault(size = 100) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(integratedUserService.getUsers(pageable))));
    }

    /** 사용자 단건 (프로필 포함) */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<IntegratedUserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(integratedUserService.getUser(userId)));
    }

    /** 사용자 생성 (프로필 포함) - 사용자관리 메뉴 접근 권한 보유 시 가능 */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<IntegratedUserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(integratedUserService.createUser(request)));
    }

    /** 사용자 수정 (프로필 포함) - 사용자관리 메뉴 접근 권한 보유 시 가능 */
    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<IntegratedUserResponse>> updateUser(
            @PathVariable Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(integratedUserService.updateUser(userId, request)));
    }

    /** 비활성화 - 사용자관리 메뉴 접근 권한 보유 시 가능 */
    @PatchMapping("/{userId}/disable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> disableUser(@PathVariable Long userId) {
        coreUserService.disableUser(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 활성화 - 사용자관리 메뉴 접근 권한 보유 시 가능 */
    @PatchMapping("/{userId}/enable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> enableUser(@PathVariable Long userId) {
        coreUserService.enableUser(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 역할 변경 (다중 역할 지원)
     * 쿼리 파라미터 role을 여러 번 전달하면 다중 역할로 저장된다.
     * 예: PATCH /api/integrated/users/1/role?role=ROLE_ADMIN&role=ROLE_FIRE_MANAGER
     * 단일 role 파라미터만 전달해도 기존과 동일하게 동작한다(하위호환).
     */
    @PatchMapping("/{userId}/role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<IntegratedUserResponse>> changeRole(
            @PathVariable Long userId, @RequestParam(name = "role") List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최소 1개 이상의 역할을 선택해야 합니다.");
        }
        return ResponseEntity.ok(ApiResponse.success(integratedUserService.changeRoles(userId, roles)));
    }
}
