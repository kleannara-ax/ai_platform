package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.personalinfo.dto.InspectionResponse;
import com.company.module.personalinfo.dto.InspectionUpdateRequest;
import com.company.module.personalinfo.service.PiChecklistTemplateService;
import com.company.module.personalinfo.service.PiInspectionService;
import com.company.module.personalinfo.service.PiRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 위수탁 점검 체크리스트 다운로드/업로드 API.
 */
@RestController
@RequestMapping("/personalinfo-api")
@RequiredArgsConstructor
public class PiInspectionController {

    private final PiInspectionService inspectionService;
    private final PiChecklistTemplateService templateService;

    /** 점검 체크리스트 빈 양식 다운로드 */
    @GetMapping("/checklist-template")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<byte[]> downloadBlankTemplate() {
        return PiDownloads.of(templateService.blankTemplate());
    }

    /** 계약 정보(회사명/수탁업무)를 미리 채운 점검 체크리스트 양식 다운로드 */
    @GetMapping("/contracts/{contractId}/checklist-template")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long contractId) {
        return PiDownloads.of(templateService.templateFor(contractId));
    }

    @GetMapping("/contracts/{contractId}/inspections")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<List<InspectionResponse>>> getList(@PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponse.success(inspectionService.getList(contractId)));
    }

    /**
     * 작성된 점검 체크리스트 업로드 (multipart/form-data)
     * <ul>
     *   <li>file : 점검 체크리스트 .xlsx</li>
     *   <li>inspectionType : REGULAR(정기, 기본) / TERMINATION(계약변경·만료)</li>
     *   <li>inspectionDate : 점검일 yyyy-MM-dd (미입력 시 엑셀 평가일 → 오늘)</li>
     *   <li>inspectorName, actionDueDate, remark : 선택</li>
     * </ul>
     */
    @PostMapping(value = "/contracts/{contractId}/inspections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<InspectionResponse>> upload(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "inspectionType", required = false) String inspectionType,
            @RequestParam(value = "inspectionDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectionDate,
            @RequestParam(value = "inspectorName", required = false) String inspectorName,
            @RequestParam(value = "actionDueDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate actionDueDate,
            @RequestParam(value = "remark", required = false) String remark,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.created(inspectionService.upload(
                contractId, file, inspectionType, inspectionDate, inspectorName, actionDueDate, remark, currentUserId)));
    }

    /** 점검 상세 (항목별 결과 포함) */
    @GetMapping("/inspections/{inspectionId}")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<InspectionResponse>> getDetail(@PathVariable Long inspectionId) {
        return ResponseEntity.ok(ApiResponse.success(inspectionService.getDetail(inspectionId)));
    }

    /** 점검 후속조치(조치 예정일/완료 여부 등) 수정 */
    @PutMapping("/inspections/{inspectionId}")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<InspectionResponse>> update(
            @PathVariable Long inspectionId,
            @Valid @RequestBody InspectionUpdateRequest request,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(inspectionService.update(inspectionId, request, currentUserId)));
    }

    /** 업로드한 체크리스트 원본 다운로드 */
    @GetMapping("/inspections/{inspectionId}/download")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<byte[]> download(@PathVariable Long inspectionId) {
        return PiDownloads.of(inspectionService.download(inspectionId));
    }

    /** 점검 이력 삭제 (소프트 삭제, 관리자 전용) */
    @DeleteMapping("/inspections/{inspectionId}")
    @PreAuthorize(PiRoles.CAN_DELETE)
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long inspectionId,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        inspectionService.delete(inspectionId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
