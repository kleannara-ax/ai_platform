package com.company.module.epr.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.epr.dto.EprOverviewResponse;
import com.company.module.epr.dto.ProductMasterResponse;
import com.company.module.epr.dto.ProductMasterSaveRequest;
import com.company.module.epr.service.EprMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 제품 마스터 (02 탭) */
@RestController
@RequestMapping("/epr-api/products")
@RequiredArgsConstructor
@PreAuthorize(EprAccess.HAS_MENU)
public class EprProductController {

    private final EprMasterService masterService;

    /** GET /epr-api/products?q=&part= — 제품코드·제품명 검색, 파트 필터 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductMasterResponse>>> getList(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String part) {
        return ResponseEntity.ok(ApiResponse.success(masterService.searchProducts(q, part)));
    }

    /** GET /epr-api/products/overview — 등록 제품 수 */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<EprOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success(new EprOverviewResponse(masterService.countProducts())));
    }

    /**
     * POST /epr-api/products — 단건 등록 · 수정 (본문의 제품코드가 이미 있으면 그 제품을 수정한다).
     * 수정도 이 주소를 쓴다. 제품코드를 주소(/products/{code})에 넣으면 "/" 가 든 코드를 서버가 거부하기 때문이다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductMasterResponse>> save(
            @Valid @RequestBody ProductMasterSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(masterService.saveProduct(request, userId)));
    }
}
