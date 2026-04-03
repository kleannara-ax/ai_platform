package com.company.core.user.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserResponse;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.entity.Role;
import com.company.core.user.service.CoreUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 관리 API (Core)
 * URL Prefix: /api/core/users
 */
@RestController
@RequestMapping("/api/core/users")
@RequiredArgsConstructor
public class CoreUserController {

    private final CoreUserService coreUserService;

    /**
     * 사용자 생성
     * ADMIN만 가능
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request) {
        UserResponse response = coreUserService.createUser(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * 사용자 단건 조회
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        UserResponse response = coreUserService.getUser(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 사용자 목록 조회 (페이징)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<UserResponse> response = PageResponse.of(coreUserService.getUsers(pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 사용자 정보 수정
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = coreUserService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 사용자 비활성화
     */
    @PatchMapping("/{userId}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> disableUser(@PathVariable Long userId) {
        coreUserService.disableUser(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 사용자 활성화
     */
    @PatchMapping("/{userId}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> enableUser(@PathVariable Long userId) {
        coreUserService.enableUser(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 사용자 역할 변경
     */
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            @PathVariable Long userId,
            @RequestParam Role role) {
        UserResponse response = coreUserService.changeRole(userId, role);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
