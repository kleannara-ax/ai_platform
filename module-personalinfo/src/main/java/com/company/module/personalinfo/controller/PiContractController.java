package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.personalinfo.dto.ContactHistoryResponse;
import com.company.module.personalinfo.dto.ContractDetailResponse;
import com.company.module.personalinfo.dto.ContractFlagUpdateRequest;
import com.company.module.personalinfo.dto.ContractSaveRequest;
import com.company.module.personalinfo.dto.ContractSearchCondition;
import com.company.module.personalinfo.dto.ContractSummaryResponse;
import com.company.module.personalinfo.dto.ContractTerminateRequest;
import com.company.module.personalinfo.dto.DashboardResponse;
import com.company.module.personalinfo.dto.PermissionResponse;
import com.company.module.personalinfo.service.PiContractExcelService;
import com.company.module.personalinfo.service.PiContractService;
import com.company.module.personalinfo.service.PiRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 개인정보 위수탁 계약 API.
 *
 * <p>로그인 사용자 ID 는 core 인증 객체(CustomUserDetails.userId)에서 받는다.
 * (core 에 CurrentUserProvider 가 없어 module-dailyreport 와 같은 방식 사용 — README 참고)
 */
@RestController
@RequestMapping("/personalinfo-api")
@RequiredArgsConstructor
public class PiContractController {

    private final PiContractService contractService;
    private final PiContractExcelService excelService;

    /** 현재 사용자 권한 (화면 버튼 노출용) */
    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<PermissionResponse>> myPermissions(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(PiRoles.permissionsOf(authentication)));
    }

    /** 목록 상단 요약 */
    @GetMapping("/dashboard")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(contractService.dashboard()));
    }

    /** 계약 목록 (검색 + 페이징) */
    @GetMapping("/contracts")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<PageResponse<ContractSummaryResponse>>> getList(
            @ModelAttribute ContractSearchCondition condition,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(contractService.search(condition, page, size, sort))));
    }

    /** 계약 목록 엑셀 다운로드 (검색 조건 적용) */
    @GetMapping("/contracts/excel")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<byte[]> downloadExcel(@ModelAttribute ContractSearchCondition condition,
                                                @RequestParam(required = false) String sort) {
        return PiDownloads.of(excelService.export(condition, sort));
    }

    /** 계약 상세 (첨부파일/점검 이력/알림 이력 포함) */
    @GetMapping("/contracts/{contractId}")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<ContractDetailResponse>> getDetail(@PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponse.success(contractService.getDetail(contractId)));
    }

    /** 계약 등록 */
    @PostMapping("/contracts")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractDetailResponse>> create(
            @Valid @RequestBody ContractSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId,
            @AuthenticationPrincipal(expression = "userDisplayName") String currentUserName) {
        return ResponseEntity.ok(ApiResponse.created(contractService.create(request, currentUserId, currentUserName)));
    }

    /** 계약 수정 */
    @PutMapping("/contracts/{contractId}")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractDetailResponse>> update(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId,
            @AuthenticationPrincipal(expression = "userDisplayName") String currentUserName) {
        return ResponseEntity.ok(ApiResponse.success(contractService.update(contractId, request, currentUserId, currentUserName)));
    }

    /** 협력사 담당자 변경 이력 (vendorWide=true 이면 같은 업체의 모든 계약 이력) */
    @GetMapping("/contracts/{contractId}/contact-history")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<List<ContactHistoryResponse>>> contactHistory(
            @PathVariable Long contractId,
            @RequestParam(defaultValue = "true") boolean vendorWide) {
        return ResponseEntity.ok(ApiResponse.success(contractService.getContactHistory(contractId, vendorWide)));
    }

    /** 목록 화면에서 ISMS 인증 여부 / 재위탁 여부 Y/N 바로 변경 (보낸 항목만 변경) */
    @PatchMapping("/contracts/{contractId}/flags")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractSummaryResponse>> updateFlags(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractFlagUpdateRequest request,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(contractService.updateFlags(contractId, request, currentUserId)));
    }

    /** 계약 종료 처리 */
    @PostMapping("/contracts/{contractId}/terminate")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractDetailResponse>> terminate(
            @PathVariable Long contractId,
            @Valid @RequestBody(required = false) ContractTerminateRequest request,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(contractService.terminate(contractId, request, currentUserId)));
    }

    /** 계약 종료 취소 */
    @PostMapping("/contracts/{contractId}/reopen")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractDetailResponse>> reopen(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(contractService.reopen(contractId, currentUserId)));
    }

    /** 계약 삭제 (소프트 삭제, 관리자 전용) */
    @DeleteMapping("/contracts/{contractId}")
    @PreAuthorize(PiRoles.CAN_DELETE)
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        contractService.delete(contractId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
