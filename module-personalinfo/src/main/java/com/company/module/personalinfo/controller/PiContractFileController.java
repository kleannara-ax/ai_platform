package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.personalinfo.dto.ContractFileResponse;
import com.company.module.personalinfo.service.PiContractFileService;
import com.company.module.personalinfo.service.PiRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 위수탁 계약서/증빙 파일 API.
 */
@RestController
@RequestMapping("/personalinfo-api")
@RequiredArgsConstructor
public class PiContractFileController {

    private final PiContractFileService fileService;

    @GetMapping("/contracts/{contractId}/files")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<ApiResponse<List<ContractFileResponse>>> getList(@PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponse.success(fileService.getList(contractId)));
    }

    /**
     * 계약서 업로드 (multipart/form-data)
     * <ul>
     *   <li>file : 파일 (pdf, doc(x), hwp(x), xls(x), jpg, png, zip)</li>
     *   <li>fileType : CONTRACT / SUBCONTRACT_CONSENT / SECURITY_PLEDGE / DESTRUCTION_CONFIRM / ETC (기본 CONTRACT)</li>
     *   <li>description : 설명 (선택)</li>
     * </ul>
     */
    @PostMapping(value = "/contracts/{contractId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractFileResponse>> upload(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.created(
                fileService.upload(contractId, file, fileType, description, currentUserId)));
    }

    @GetMapping("/files/{fileId}/download")
    @PreAuthorize(PiRoles.CAN_READ)
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
        return PiDownloads.of(fileService.download(fileId));
    }

    /** 첨부파일 삭제 (소프트 삭제, 관리자 전용) */
    @DeleteMapping("/files/{fileId}")
    @PreAuthorize(PiRoles.CAN_DELETE)
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long fileId,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        fileService.delete(fileId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
