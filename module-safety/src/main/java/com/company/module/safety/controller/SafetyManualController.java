package com.company.module.safety.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.safety.dto.request.ManualCreateRequest;
import com.company.module.safety.dto.request.ManualUpdateRequest;
import com.company.module.safety.dto.request.StepCreateRequest;
import com.company.module.safety.dto.request.StepUpdateRequest;
import com.company.module.safety.dto.response.ManualDetailResponse;
import com.company.module.safety.dto.response.ManualSummaryResponse;
import com.company.module.safety.dto.response.StepResponse;
import com.company.module.safety.service.SafetyManualService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 안전작업 매뉴얼(본문) REST API.
 * <p>조회는 인증된 사용자 누구나, 등록/수정/삭제는 SAFETY 관리자만 가능하다.
 */
@RestController
@RequiredArgsConstructor
public class SafetyManualController {

    private final SafetyManualService manualService;

    /** 매뉴얼 목록/검색 (페이지) */
    @GetMapping("/safety-api/manuals")
    public ResponseEntity<ApiResponse<PageResponse<ManualSummaryResponse>>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(manualService.getList(keyword, categoryId, page, size)));
    }

    /** 특정 분류에 직접 속한 매뉴얼 목록 (소분류 클릭 시 목록 화면) */
    @GetMapping("/safety-api/categories/{categoryId}/manuals")
    public ResponseEntity<ApiResponse<List<ManualSummaryResponse>>> getListByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(manualService.getListByCategory(categoryId)));
    }

    /**
     * 분류 하위 전체 매뉴얼 목록 (좌측 트리에서 대/중/소 어느 단계를 눌러도 그 아래 매뉴얼을 모두 조회).
     * <p>{@code categoryId} 를 생략하면 전체 매뉴얼을 반환한다.
     * <p>{@code content} 를 주면 매뉴얼 <b>내용</b>(단계의 공정 설명/위험요인/안전보호구/비고)에서 찾아,
     * 걸린 단계 수와 발췌를 함께 돌려준다. (제목 검색은 화면에서 처리한다)
     */
    @GetMapping("/safety-api/manuals/by-category")
    public ResponseEntity<ApiResponse<List<ManualSummaryResponse>>> getListInSubtree(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String content) {
        if (content != null && !content.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(manualService.searchByContent(categoryId, content)));
        }
        return ResponseEntity.ok(ApiResponse.success(manualService.getListInSubtree(categoryId)));
    }

    /** 매뉴얼 상세 (원본 엑셀과 같은 레이아웃: 단계 + 단계별 사진) */
    @GetMapping("/safety-api/manuals/{manualId}")
    public ResponseEntity<ApiResponse<ManualDetailResponse>> getDetail(@PathVariable Long manualId) {
        return ResponseEntity.ok(ApiResponse.success(manualService.getDetail(manualId)));
    }

    @PostMapping("/safety-api/manuals")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ManualDetailResponse>> create(
            @Valid @RequestBody ManualCreateRequest request, Authentication authentication) {
        String createdBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.created(manualService.create(request, createdBy)));
    }

    @PutMapping("/safety-api/manuals/{manualId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ManualDetailResponse>> update(
            @PathVariable Long manualId, @Valid @RequestBody ManualUpdateRequest request,
            Authentication authentication) {
        String updatedBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(manualService.update(manualId, request, updatedBy)));
    }

    @DeleteMapping("/safety-api/manuals/{manualId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long manualId, Authentication authentication) {
        String deletedBy = (authentication != null) ? authentication.getName() : null;
        manualService.delete(manualId, deletedBy);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ================================================================
    // 단계(순서) 관리 — 매뉴얼 상세 화면에서 개별 추가/수정/삭제
    // ================================================================

    @PostMapping("/safety-api/manuals/{manualId}/steps")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<StepResponse>> addStep(
            @PathVariable Long manualId, @RequestBody StepCreateRequest request, Authentication authentication) {
        String createdBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.created(manualService.addStep(manualId, request, createdBy)));
    }

    @PutMapping("/safety-api/steps/{stepId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<StepResponse>> updateStep(
            @PathVariable Long stepId, @RequestBody StepUpdateRequest request, Authentication authentication) {
        String updatedBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(manualService.updateStep(stepId, request, updatedBy)));
    }

    @DeleteMapping("/safety-api/steps/{stepId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> deleteStep(@PathVariable Long stepId, Authentication authentication) {
        String deletedBy = (authentication != null) ? authentication.getName() : null;
        manualService.deleteStep(stepId, deletedBy);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
