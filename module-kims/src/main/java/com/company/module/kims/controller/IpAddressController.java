package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.IpCreateRequest;
import com.company.module.kims.dto.request.IpModifyRequest;
import com.company.module.kims.dto.request.IpReclaimRequest;
import com.company.module.kims.dto.response.IpAddressDetailResponse;
import com.company.module.kims.dto.response.IpAddressResponse;
import com.company.module.kims.dto.response.IpHistoryResponse;
import com.company.module.kims.dto.response.IpExcelUploadResult;
import com.company.module.kims.entity.enums.IpStatus;
import com.company.module.kims.service.IpAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.List;

/**
 * IP 관리 REST API.
 * <p>URL prefix: {@code /kims-api/ips}
 * <p>쓰기 작업은 관리자(ADMIN) 또는 전산담당자(STAFF) 권한이 필요하다.
 */
@RestController
@RequestMapping("/kims-api/ips")
@RequiredArgsConstructor
public class IpAddressController {

    private final IpAddressService ipAddressService;

    /** 신규 IP 등록 */
    @PostMapping
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<IpAddressResponse>> create(@Valid @RequestBody IpCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(ipAddressService.create(request)));
    }

    /** IP 목록 조회 (IP/사용자 검색, 상태/부서 필터 + 페이징) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IpAddressResponse>>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String searchField,
            @RequestParam(required = false) IpStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ipGroup,
            @RequestParam(required = false) String excludeGroup,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                ipAddressService.getList(keyword, searchField, status, department, ipGroup, excludeGroup, site, page, size)));
    }

    /** 등록된 IP 그룹 목록 (분류 필터용). site 미지정 시 전체. */
    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<List<String>>> getGroups(@RequestParam(required = false) String site) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.getGroups(site)));
    }

    /** 미품의 IP 변경 내역 (고정 경로를 /{ipId} 보다 먼저 선언). site 미지정 시 전체. */
    @GetMapping("/unapproved-changes")
    public ResponseEntity<ApiResponse<List<IpHistoryResponse>>> getUnapprovedChanges(
            @RequestParam(required = false) String site) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.getUnapprovedChanges(site)));
    }

    /** 대시보드: 대역별 사용중/미사용 현황. site 미지정 시 전체. */
    @GetMapping("/utilization")
    public ResponseEntity<ApiResponse<List<com.company.module.kims.dto.response.IpGroupUtilResponse>>> getUtilization(
            @RequestParam(required = false) String site) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.getUtilization(site)));
    }

    /** 변경내역이 있는 월 목록 (yyyy-MM) */
    @GetMapping("/history/months")
    public ResponseEntity<ApiResponse<List<String>>> getHistoryMonths(@RequestParam(required = false) String site) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.getHistoryMonths(site)));
    }

    /** 이력 검색 (제조번호별/IP별 — 해당 장비/주소의 전체 변경 이력). site 미지정 시 전체. */
    @GetMapping("/history/search")
    public ResponseEntity<ApiResponse<List<IpHistoryResponse>>> searchHistory(
            @RequestParam String field, @RequestParam String keyword,
            @RequestParam(required = false) String site) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.searchHistory(field, keyword, site)));
    }

    /** 사용자별 사용 이력 (관여 장비의 사용자 변천 — 일시/IP/제조번호/부서/사용자). site 미지정 시 전체. */
    @GetMapping("/history/by-user")
    public ResponseEntity<ApiResponse<List<IpHistoryResponse>>> searchUserUsage(
            @RequestParam String keyword, @RequestParam(required = false) String site) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.searchUserUsage(keyword, site)));
    }

    /** 특정 연·월의 IP 변경 내역 (당월/월별 조회). site 미지정 시 전체. */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<IpHistoryResponse>>> getMonthlyHistory(
            @RequestParam int year, @RequestParam int month, @RequestParam(required = false) String site) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.getMonthlyHistory(year, month, site)));
    }

    /** IP 상세 (변경 이력 포함) */
    @GetMapping("/{ipId}")
    public ResponseEntity<ApiResponse<IpAddressDetailResponse>> getDetail(@PathVariable Long ipId) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.getDetail(ipId)));
    }

    /** IP 사용 이력 조회 */
    @GetMapping("/{ipId}/history")
    public ResponseEntity<ApiResponse<List<IpHistoryResponse>>> getHistory(@PathVariable Long ipId) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.getHistory(ipId)));
    }

    /** IP 정보/사용자 변경 */
    @PatchMapping("/{ipId}")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<IpAddressResponse>> modify(
            @PathVariable Long ipId, @Valid @RequestBody IpModifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.modify(ipId, request)));
    }

    /** IP 회수 처리 (IP만 미사용) */
    @PostMapping("/{ipId}/reclaim")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<IpAddressResponse>> reclaim(
            @PathVariable Long ipId, @Valid @RequestBody IpReclaimRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.reclaim(ipId, request)));
    }

    /** PC+IP 반납 처리 (PC/장비도 함께 반납 → PC 정보까지 비움) */
    @PostMapping("/{ipId}/return")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<IpAddressResponse>> returnPc(
            @PathVariable Long ipId, @Valid @RequestBody IpReclaimRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.returnPc(ipId, request)));
    }

    /** IP 변경(이동) — 같은 PC 를 다른 IP 로 이동 */
    @PostMapping("/{ipId}/move")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<IpAddressResponse>> move(
            @PathVariable Long ipId,
            @Valid @RequestBody com.company.module.kims.dto.request.IpMoveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.moveIp(ipId, request)));
    }

    /** IP 목록 Excel 다운로드 */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String searchField,
            @RequestParam(required = false) IpStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ipGroup,
            @RequestParam(required = false) String site) {
        byte[] body = ipAddressService.exportExcel(keyword, searchField, status, department, ipGroup, site);
        String filename = "ip_addresses.xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    /** PC 목록 Excel 업로드 (대량 등록). 헤더가 양식과 일치할 때만 적재, 아니면 ok=false 로 반환. */
    @PostMapping("/excel/upload")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<ApiResponse<IpExcelUploadResult>> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String site,
            Authentication authentication) {
        String changedBy = (authentication != null) ? authentication.getName() : "upload";
        return ResponseEntity.ok(ApiResponse.success(ipAddressService.importExcel(file, site, changedBy)));
    }
}
