package com.company.module.safety.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.safety.dto.request.CategoryCreateRequest;
import com.company.module.safety.dto.request.CategoryUpdateRequest;
import com.company.module.safety.dto.response.CategoryResponse;
import com.company.module.safety.service.SafetyCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 안전작업방식 매뉴얼 분류(카테고리) REST API.
 * <p>조회는 인증된 사용자 누구나, 등록/수정/삭제는 SAFETY 관리자만 가능하다.
 */
@RestController
@RequiredArgsConstructor
public class SafetyCategoryController {

    private final SafetyCategoryService categoryService;

    /** 분류 트리 전체 조회 (좌측 트리/분류 목록 화면) */
    @GetMapping("/safety-api/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getTree()));
    }

    /** 특정 부모의 하위 분류 목록 (parentId 없으면 대분류 목록). 단계별 선택(드릴다운) UI 및 엑셀 업로드 모달의 분류 선택기에서 사용. */
    @GetMapping("/safety-api/categories/children")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getChildren(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getChildren(parentId)));
    }

    @GetMapping("/safety-api/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getDetail(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getDetail(categoryId)));
    }

    @PostMapping("/safety-api/categories")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryCreateRequest request, Authentication authentication) {
        String createdBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.created(categoryService.create(request, createdBy)));
    }

    @PutMapping("/safety-api/categories/{categoryId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long categoryId, @Valid @RequestBody CategoryUpdateRequest request,
            Authentication authentication) {
        String updatedBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(categoryService.update(categoryId, request, updatedBy)));
    }

    @DeleteMapping("/safety-api/categories/{categoryId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long categoryId, Authentication authentication) {
        String deletedBy = (authentication != null) ? authentication.getName() : null;
        categoryService.delete(categoryId, deletedBy);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
