package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.personalinfo.dto.ContractDocAnalyzeResponse;
import com.company.module.personalinfo.dto.ContractFileResponse;
import com.company.module.personalinfo.service.PiContractDocAnalyzer;
import com.company.module.personalinfo.service.PiContractFileService;
import com.company.module.personalinfo.service.PiRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 위수탁 계약서/증빙 파일 API.
 */
@RestController
@RequestMapping("/personalinfo-api")
@RequiredArgsConstructor
public class PiContractFileController {

    private final PiContractFileService fileService;
    private final PiContractDocAnalyzer docAnalyzer;

    /**
     * 개인정보처리 위탁계약서 분석 (multipart/form-data, file).
     * 파일은 저장하지 않고 계약일자(체결일) 후보만 돌려준다. 등록 화면에서 계약 저장 전에도 쓸 수 있도록 계약 ID 없이 호출한다.
     */
    @PostMapping(value = "/contract-doc/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractDocAnalyzeResponse>> analyze(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(docAnalyzer.analyze(file)));
    }

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
     *   <li>signedDate : 계약서상 체결일 yyyy-MM-dd (선택, 위수탁 계약서만. 없으면 파일에서 읽음)</li>
     * </ul>
     */
    @PostMapping(value = "/contracts/{contractId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractFileResponse>> upload(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "signedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedDate,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.created(
                fileService.upload(contractId, file, fileType, description, signedDate, currentUserId)));
    }

    /** 위수탁 계약서 체결일 변경 {"signedDate":"yyyy-MM-dd"} (빈 값이면 비움). 계약 체결일은 최신 계약서 기준으로 다시 맞춘다. */
    @PatchMapping("/files/{fileId}/signed-date")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<ContractFileResponse>> changeSignedDate(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        String v = body == null ? null : body.get("signedDate");
        LocalDate date;
        try {
            date = v == null || v.isBlank() ? null : LocalDate.parse(v.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.company.core.common.exception.BusinessException(
                    com.company.core.common.exception.ErrorCode.INVALID_INPUT_VALUE, "체결일 형식이 올바르지 않습니다. (yyyy-MM-dd)");
        }
        return ResponseEntity.ok(ApiResponse.success(fileService.changeSignedDate(fileId, date, currentUserId)));
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
