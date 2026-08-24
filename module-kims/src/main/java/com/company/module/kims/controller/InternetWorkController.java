package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.InternetWorkCreateRequest;
import com.company.module.kims.dto.request.InternetWorkStatusRequest;
import com.company.module.kims.dto.request.InternetWorkUpdateRequest;
import com.company.module.kims.dto.response.InternetWorkResponse;
import com.company.module.kims.entity.enums.InternetWorkStatus;
import com.company.module.kims.entity.enums.InternetWorkType;
import com.company.module.kims.service.InternetWorkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 인터넷 공사 REST API.
 * <p>URL prefix: {@code /kims-api/internet-works}
 * <p>쓰기 작업은 관리자(ADMIN) 또는 전산담당자(STAFF) 권한이 필요하다.
 */
@RestController
@RequestMapping("/kims-api/internet-works")
@RequiredArgsConstructor
public class InternetWorkController {

    private final InternetWorkService internetWorkService;

    /** 공사 등록 */
    @PostMapping
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<InternetWorkResponse>> register(
            @Valid @RequestBody InternetWorkCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(internetWorkService.register(request)));
    }

    /** 공사 목록 (요청자/위치/내용 검색, 공사유형·상태·부서·기간 필터 + 페이징) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InternetWorkResponse>>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InternetWorkType workType,
            @RequestParam(required = false) InternetWorkStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                internetWorkService.getList(keyword, workType, status, department, from, to, page, size)));
    }

    /** 공사 상세 */
    @GetMapping("/{workId}")
    public ResponseEntity<ApiResponse<InternetWorkResponse>> getDetail(@PathVariable Long workId) {
        return ResponseEntity.ok(ApiResponse.success(internetWorkService.getDetail(workId)));
    }

    /** 공사 정보 수정 */
    @PatchMapping("/{workId}")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<InternetWorkResponse>> update(
            @PathVariable Long workId, @Valid @RequestBody InternetWorkUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(internetWorkService.update(workId, request)));
    }

    /** 공사 상태 변경 (완료 시 완료일 자동 입력) */
    @PatchMapping("/{workId}/status")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<InternetWorkResponse>> changeStatus(
            @PathVariable Long workId, @Valid @RequestBody InternetWorkStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(internetWorkService.changeStatus(workId, request)));
    }

    /** 공사 삭제 (관리자 전용) */
    @DeleteMapping("/{workId}")
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long workId, Authentication authentication) {
        String deletedBy = (authentication != null) ? authentication.getName() : null;
        internetWorkService.delete(workId, deletedBy);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 공사 내역 Excel 다운로드 */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InternetWorkType workType,
            @RequestParam(required = false) InternetWorkStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        byte[] body = internetWorkService.exportExcel(keyword, workType, status, department, from, to);
        String filename = "internet_works.xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
