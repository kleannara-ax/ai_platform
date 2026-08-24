package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.SupplyIssueCreateRequest;
import com.company.module.kims.dto.response.SupplyIssueResponse;
import com.company.module.kims.service.SupplyIssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 소모품 지급 REST API.
 * <p>URL prefix: {@code /kims-api/supply-issues}
 */
@RestController
@RequestMapping("/kims-api/supply-issues")
@RequiredArgsConstructor
public class SupplyIssueController {

    private final SupplyIssueService supplyIssueService;

    /** 8. 소모품 지급 등록 (재고 자동 차감) - 관리자 권한 */
    @PostMapping
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<SupplyIssueResponse>> issue(
            @Valid @RequestBody SupplyIssueCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(supplyIssueService.issue(request)));
    }

    /** 소모품 지급 내역 목록 조회 (기간/품목/부서 검색 + 페이징) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SupplyIssueResponse>>> getList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                supplyIssueService.getList(from, to, itemId, department, page, size)));
    }

    /** 10. 소모품 지급 내역 Excel 다운로드 */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String department) {
        byte[] body = supplyIssueService.exportExcel(from, to, itemId, department);

        String filename = "supply_issues.xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
