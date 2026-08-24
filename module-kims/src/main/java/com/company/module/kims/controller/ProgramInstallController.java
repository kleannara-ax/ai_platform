package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.ProgramInstallCreateRequest;
import com.company.module.kims.dto.request.ProgramInstallUpdateRequest;
import com.company.module.kims.dto.response.ProgramInstallResponse;
import com.company.module.kims.service.ProgramInstallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.time.LocalDate;

/**
 * 프로그램 설치 내역 REST API.
 * <p>URL prefix: {@code /kims-api/program-installs}
 * <p>쓰기 작업은 관리자(ADMIN) 또는 전산담당자(STAFF) 권한이 필요하다.
 */
@RestController
@RequestMapping("/kims-api/program-installs")
@RequiredArgsConstructor
public class ProgramInstallController {

    private final ProgramInstallService programInstallService;

    /** 설치 내역 등록 */
    @PostMapping
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<ProgramInstallResponse>> register(
            @Valid @RequestBody ProgramInstallCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.created(programInstallService.register(request)));
    }

    /** 설치 내역 목록 (프로그램/요청자/PC 검색, 부서·담당자·기간 필터 + 페이징) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProgramInstallResponse>>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String installedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                programInstallService.getList(keyword, department, installedBy, from, to, page, size)));
    }

    /** 설치 내역 상세 */
    @GetMapping("/{installId}")
    public ResponseEntity<ApiResponse<ProgramInstallResponse>> getDetail(@PathVariable Long installId) {
        return ResponseEntity.ok(ApiResponse.success(programInstallService.getDetail(installId)));
    }

    /** 설치 내역 수정 */
    @PatchMapping("/{installId}")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<ProgramInstallResponse>> update(
            @PathVariable Long installId, @Valid @RequestBody ProgramInstallUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(programInstallService.update(installId, request)));
    }

    /** 설치 내역 삭제 (관리자 전용) */
    @DeleteMapping("/{installId}")
    @PreAuthorize("@kimsPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long installId) {
        programInstallService.delete(installId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 설치 내역 Excel 다운로드 */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String installedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = programInstallService.exportExcel(keyword, department, installedBy, from, to);
        String filename = "program_installs.xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
