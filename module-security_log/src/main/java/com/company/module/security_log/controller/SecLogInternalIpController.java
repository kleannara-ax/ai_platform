package com.company.module.security_log.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.security_log.dto.InternalIpResponse;
import com.company.module.security_log.dto.InternalIpSaveRequest;
import com.company.module.security_log.dto.InternalIpTestRequest;
import com.company.module.security_log.dto.InternalIpTestResponse;
import com.company.module.security_log.dto.InternalIpUseYnRequest;
import com.company.module.security_log.service.SecLogInternalIpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리 > 내부 IP 설정 API
 * - 조회·IP 확인: ADMIN, MANAGER, USER
 * - 등록/수정/사용여부/삭제: ADMIN
 */
@RestController
@RequestMapping("/security_log-api/internal-ips")
@RequiredArgsConstructor
public class SecLogInternalIpController {

    private final SecLogInternalIpService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<InternalIpResponse>>> getList() {
        return ResponseEntity.ok(ApiResponse.success(service.getList()));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<InternalIpTestResponse>> test(@Valid @RequestBody InternalIpTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.test(request.getIp())));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InternalIpResponse>> create(@Valid @RequestBody InternalIpSaveRequest request,
                                                                  @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(service.create(request, userId)));
    }

    @PutMapping("/{internalIpId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InternalIpResponse>> update(@PathVariable Long internalIpId,
                                                                  @Valid @RequestBody InternalIpSaveRequest request,
                                                                  @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(service.update(internalIpId, request, userId)));
    }

    @PatchMapping("/{internalIpId}/use-yn")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InternalIpResponse>> changeUseYn(@PathVariable Long internalIpId,
                                                                       @Valid @RequestBody InternalIpUseYnRequest request,
                                                                       @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(service.changeUseYn(internalIpId, request.getUseYn(), userId)));
    }

    @DeleteMapping("/{internalIpId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long internalIpId,
                                                    @AuthenticationPrincipal(expression = "userId") Long userId) {
        service.delete(internalIpId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
