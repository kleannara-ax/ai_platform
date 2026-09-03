package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.RequestLogCreateRequest;
import com.company.module.kims.dto.request.ServiceRequestCreateRequest;
import com.company.module.kims.dto.request.StatusChangeRequest;
import com.company.module.kims.dto.response.RequestLogResponse;
import com.company.module.kims.dto.response.ServiceRequestDetailResponse;
import com.company.module.kims.dto.response.ServiceRequestResponse;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import com.company.module.kims.service.ServiceRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.List;

/**
 * 업무 요청 REST API.
 * <p>URL prefix: {@code /kims-api/requests}
 */
@RestController
@RequestMapping("/kims-api/requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService serviceRequestService;

    /** 1. 업무 요청 등록 (인증된 사용자라면 누구나 등록 가능) */
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> register(
            @Valid @RequestBody ServiceRequestCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(serviceRequestService.register(request)));
    }

    /** 홈 카드: 유형별 신규(접수)/전체 건수 (고정 경로를 /{requestId} 보다 먼저 선언) */
    @GetMapping("/type-summary")
    public ResponseEntity<ApiResponse<List<com.company.module.kims.dto.response.RequestTypeSummaryResponse>>> getTypeSummary() {
        return ResponseEntity.ok(ApiResponse.success(serviceRequestService.getTypeSummary()));
    }

    /** 요청이 있는 월 목록 (yyyy-MM) */
    @GetMapping("/months")
    public ResponseEntity<ApiResponse<List<String>>> getMonths() {
        return ResponseEntity.ok(ApiResponse.success(serviceRequestService.getMonths()));
    }

    /** 2. 업무 요청 목록 조회 (기간/유형/상태/부서/요청자 검색 + 페이징) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ServiceRequestResponse>>> getList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) RequestType requestType,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String requester,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                serviceRequestService.getList(from, to, requestType, status, department, requester,
                        assignee, location, page, size)));
    }

    /** 3. 업무 요청 상세 조회 (로그 + 소모품 지급 내역 포함) */
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<ServiceRequestDetailResponse>> getDetail(
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success(serviceRequestService.getDetail(requestId)));
    }

    /** 4. 처리상태 변경 (상태변경 로그 자동 기록) - 담당자/관리자 권한 */
    @PatchMapping("/{requestId}/status")
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> changeStatus(
            @PathVariable Long requestId,
            @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(serviceRequestService.changeStatus(requestId, request)));
    }

    /** 5. 처리내용(메모) 로그 추가 - 담당자/관리자 권한 */
    @PostMapping("/{requestId}/logs")
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<RequestLogResponse>> addProcessLog(
            @PathVariable Long requestId,
            @Valid @RequestBody RequestLogCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(serviceRequestService.addProcessLog(requestId, request)));
    }

    /** 10. 업무 요청 목록 Excel 다운로드 (목록 검색 조건과 동일) */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) RequestType requestType,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String requester,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String location) {
        byte[] body = serviceRequestService.exportExcel(from, to, requestType, status, department,
                requester, assignee, location);
        return excelResponse(body, "service_requests.xlsx");
    }

    /** Excel 다운로드용 공통 응답 헤더 구성 */
    private ResponseEntity<byte[]> excelResponse(byte[] body, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
