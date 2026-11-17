package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.personalinfo.dto.VendorChecklistPreviewResponse;
import com.company.module.personalinfo.dto.VendorChecklistUploadResponse;
import com.company.module.personalinfo.dto.VendorSummaryResponse;
import com.company.module.personalinfo.service.PiRoles;
import com.company.module.personalinfo.service.PiVendorChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 계약 업체별 점검 체크리스트 업로드 API.
 * 업체 1곳의 체크리스트 1건을 그 업체의 여러 계약에 한 번에 점검 이력으로 등록한다.
 */
@RestController
@RequestMapping("/personalinfo-api/vendors")
@RequiredArgsConstructor
public class PiVendorChecklistController {

    private final PiVendorChecklistService vendorChecklistService;

    /** 업체 목록 (업체명 기준 계약 묶음, 업체별 점검 현황) */
    @GetMapping
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<List<VendorSummaryResponse>>> getVendors(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeTerminated) {
        return ResponseEntity.ok(ApiResponse.success(vendorChecklistService.getVendors(keyword, includeTerminated)));
    }

    /** 업로드 전 미리보기 (저장하지 않음). 체크리스트 회사명으로 업체 후보를 찾아준다 */
    @PostMapping(value = "/checklist-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<VendorChecklistPreviewResponse>> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "inspectionType", required = false) String inspectionType) {
        return ResponseEntity.ok(ApiResponse.success(vendorChecklistService.preview(file, inspectionType)));
    }

    /**
     * 업체별 체크리스트 업로드 (multipart/form-data)
     * <ul>
     *   <li>vendorKey : 업체 키 (업체 목록의 vendorKey) — 필수</li>
     *   <li>contractIds : 등록할 계약 ID (여러 개). 비우면 업체의 미종료 계약 전체</li>
     *   <li>file, inspectionType, inspectionDate, inspectorName, actionDueDate, remark : 개별 업로드와 동일</li>
     * </ul>
     */
    @PostMapping(value = "/checklist", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<VendorChecklistUploadResponse>> upload(
            @RequestParam("vendorKey") String vendorKey,
            @RequestParam(value = "contractIds", required = false) List<Long> contractIds,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "inspectionType", required = false) String inspectionType,
            @RequestParam(value = "inspectionDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectionDate,
            @RequestParam(value = "inspectorName", required = false) String inspectorName,
            @RequestParam(value = "actionDueDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate actionDueDate,
            @RequestParam(value = "remark", required = false) String remark,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.created(vendorChecklistService.upload(
                vendorKey, contractIds, file, inspectionType, inspectionDate, inspectorName,
                actionDueDate, remark, currentUserId)));
    }
}
