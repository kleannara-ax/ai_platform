package com.company.module.user.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.user.dto.UserProfileRequest;
import com.company.module.user.dto.UserProfileResponse;
import com.company.module.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자 프로필 API
 * URL Prefix: /api/module-user/profiles
 */
@RestController
@RequestMapping("/api/module-user/profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 프로필 생성
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> createProfile(
            @Valid @RequestBody UserProfileRequest request) {
        UserProfileResponse response = userProfileService.createProfile(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * 사용자 ID로 프로필 조회
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @PathVariable Long userId) {
        UserProfileResponse response = userProfileService.getProfileByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 부서별 프로필 목록 조회
     */
    @GetMapping("/department/{deptId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getProfilesByDept(
            @PathVariable Long deptId) {
        List<UserProfileResponse> response = userProfileService.getProfilesByDeptId(deptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 프로필 수정
     */
    @PutMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UserProfileRequest request) {
        UserProfileResponse response = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
