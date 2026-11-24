package com.company.module.epr.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.epr.dto.PartCategoryResponse;
import com.company.module.epr.dto.PartCategorySaveRequest;
import com.company.module.epr.service.EprMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 파트 코드 (03 탭) */
@RestController
@RequestMapping("/epr-api/parts")
@RequiredArgsConstructor
@PreAuthorize(EprAccess.HAS_MENU)
public class EprPartCategoryController {

    private final EprMasterService masterService;

    /** GET /epr-api/parts — 파트 코드와 파트별 등록 SKU 수 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PartCategoryResponse>>> getList() {
        return ResponseEntity.ok(ApiResponse.success(masterService.getParts()));
    }

    /** POST /epr-api/parts — 추가 또는 파트명 수정 */
    @PostMapping
    public ResponseEntity<ApiResponse<PartCategoryResponse>> save(
            @Valid @RequestBody PartCategorySaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(masterService.savePart(request, userId)));
    }

    /** DELETE /epr-api/parts/{partPrefix} — 소프트 삭제 (쓰는 제품이 있으면 거부) */
    @DeleteMapping("/{partPrefix}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String partPrefix,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        masterService.deletePart(partPrefix, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
