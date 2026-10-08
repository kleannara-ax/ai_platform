package com.company.module.security_log.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.security_log.dto.RuleResponse;
import com.company.module.security_log.dto.RuleSaveRequest;
import com.company.module.security_log.dto.RuleTestRequest;
import com.company.module.security_log.dto.RuleTestResponse;
import com.company.module.security_log.dto.RuleUseYnRequest;
import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.service.SecLogRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 탐지 룰셋 편집 API
 * - 조회: ADMIN, MANAGER, USER
 * - 등록/수정/사용여부/테스트/삭제: ADMIN (정보보안팀 관리자)
 */
@RestController
@RequestMapping("/security_log-api/rules")
@RequiredArgsConstructor
public class SecLogRuleController {

    private final SecLogRuleService ruleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<Page<RuleResponse>>> getList(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LogType logType,
            @RequestParam(required = false) String useYn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(ApiResponse.success(ruleService.getList(q, logType, useYn, page, size)));
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<RuleResponse>> getDetail(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ApiResponse.success(ruleService.getDetail(ruleId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RuleResponse>> create(@Valid @RequestBody RuleSaveRequest request,
                                                            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(ruleService.create(request, userId)));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RuleResponse>> update(@PathVariable Long ruleId,
                                                            @Valid @RequestBody RuleSaveRequest request,
                                                            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(ruleService.update(ruleId, request, userId)));
    }

    @PatchMapping("/{ruleId}/use-yn")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RuleResponse>> changeUseYn(@PathVariable Long ruleId,
                                                                 @Valid @RequestBody RuleUseYnRequest request,
                                                                 @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(ruleService.changeUseYn(ruleId, request.getUseYn(), userId)));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long ruleId,
                                                    @AuthenticationPrincipal(expression = "userId") Long userId) {
        ruleService.delete(ruleId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 패턴 사전 테스트 (저장하지 않음) */
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RuleTestResponse>> test(@Valid @RequestBody RuleTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ruleService.test(request)));
    }
}
