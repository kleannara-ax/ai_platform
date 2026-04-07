package com.company.module.code.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.code.dto.*;
import com.company.module.code.service.CodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 공통코드 관리 REST API
 *
 * <p>ADMIN, MANAGER: 코드 그룹/상세 CRUD
 * <p>인증된 사용자: 코드 조회
 */
@RestController
@RequestMapping("/api/codes")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    // ══════════════════════════════════════
    //  코드 그룹 API
    // ══════════════════════════════════════

    /** 전체 코드 그룹 목록 */
    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<List<CodeGroupResponse>>> getGroups() {
        return ResponseEntity.ok(ApiResponse.success(codeService.getGroups()));
    }

    /** 코드 그룹 상세 (하위 코드 포함) */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponse<CodeGroupResponse>> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(codeService.getGroup(groupId)));
    }

    /** 그룹 코드(문자열)로 조회 */
    @GetMapping("/groups/code/{groupCode}")
    public ResponseEntity<ApiResponse<CodeGroupResponse>> getGroupByCode(@PathVariable String groupCode) {
        return ResponseEntity.ok(ApiResponse.success(codeService.getGroupByCode(groupCode)));
    }

    /** 코드 그룹 생성 (ADMIN) */
    @PostMapping("/groups")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CodeGroupResponse>> createGroup(@Valid @RequestBody CodeGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(codeService.createGroup(request)));
    }

    /** 코드 그룹 수정 (ADMIN) */
    @PutMapping("/groups/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CodeGroupResponse>> updateGroup(
            @PathVariable Long groupId, @Valid @RequestBody CodeGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(codeService.updateGroup(groupId, request)));
    }

    /** 코드 그룹 삭제 (ADMIN) */
    @DeleteMapping("/groups/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long groupId) {
        codeService.deleteGroup(groupId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ══════════════════════════════════════
    //  코드 상세 API
    // ══════════════════════════════════════

    /** 그룹 하위 코드 목록 */
    @GetMapping("/groups/{groupId}/details")
    public ResponseEntity<ApiResponse<List<CodeDetailResponse>>> getDetails(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(codeService.getDetails(groupId)));
    }

    /** 그룹 코드(문자열)로 활성 코드 목록 (드롭다운용) */
    @GetMapping("/lookup/{groupCode}")
    public ResponseEntity<ApiResponse<List<CodeDetailResponse>>> getDetailsByGroupCode(@PathVariable String groupCode) {
        return ResponseEntity.ok(ApiResponse.success(codeService.getDetailsByGroupCode(groupCode)));
    }

    /** 코드 상세 단건 */
    @GetMapping("/details/{codeId}")
    public ResponseEntity<ApiResponse<CodeDetailResponse>> getDetail(@PathVariable Long codeId) {
        return ResponseEntity.ok(ApiResponse.success(codeService.getDetail(codeId)));
    }

    /** 코드 추가 (ADMIN) */
    @PostMapping("/groups/{groupId}/details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CodeDetailResponse>> createDetail(
            @PathVariable Long groupId, @Valid @RequestBody CodeDetailRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(codeService.createDetail(groupId, request)));
    }

    /** 코드 수정 (ADMIN) */
    @PutMapping("/details/{codeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CodeDetailResponse>> updateDetail(
            @PathVariable Long codeId, @Valid @RequestBody CodeDetailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(codeService.updateDetail(codeId, request)));
    }

    /** 코드 삭제 (ADMIN) */
    @DeleteMapping("/details/{codeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteDetail(@PathVariable Long codeId) {
        codeService.deleteDetail(codeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
