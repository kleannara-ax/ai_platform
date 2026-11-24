package com.company.module.epr.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** POST /epr-api/products — 단건 등록 (같은 제품코드가 있으면 수정) */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductMasterResponse>> create(
            @Valid @RequestBody ProductMasterSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(masterService.saveProduct(request, userId)));
    }

    /** PUT /epr-api/products/{productCode} — 단건 수정 */
    @PutMapping("/{productCode}")
    public ResponseEntity<ApiResponse<ProductMasterResponse>> update(
            @PathVariable String productCode,
            @Valid @RequestBody ProductMasterSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        if (request.getProductCode() != null && !request.getProductCode().trim().equalsIgnoreCase(productCode.trim())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주소의 제품코드와 본문의 제품코드가 다릅니다.");
        }
        request.setProductCode(productCode);
        return ResponseEntity.ok(ApiResponse.success(masterService.saveProduct(request, userId)));
    }
}
