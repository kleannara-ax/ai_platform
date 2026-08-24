package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.kims.dto.response.AttachmentResponse;
import com.company.module.kims.service.AttachmentService;
import com.company.module.kims.service.AttachmentService.DownloadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 업무 요청 첨부파일 REST API.
 * <ul>
 *   <li>업로드/목록: {@code /kims-api/requests/{requestId}/attachments}</li>
 *   <li>다운로드/삭제: {@code /kims-api/attachments/{attachmentId}}</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /** 첨부파일 업로드 (multipart/form-data, 파트명 file) - 인증된 사용자 누구나 */
    @PostMapping("/kims-api/requests/{requestId}/attachments")
    public ResponseEntity<ApiResponse<AttachmentResponse>> upload(
            @PathVariable Long requestId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String uploadedBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.created(attachmentService.upload(requestId, file, uploadedBy)));
    }

    /** 첨부파일 목록 */
    @GetMapping("/kims-api/requests/{requestId}/attachments")
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> getList(@PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.getList(requestId)));
    }

    /** 첨부파일 다운로드 */
    @GetMapping("/kims-api/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long attachmentId) {
        DownloadFile f = attachmentService.loadForDownload(attachmentId);
        String encoded = URLEncoder.encode(f.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType type = (f.contentType() != null)
                ? MediaType.parseMediaType(f.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + f.originalName() + "\"; filename*=UTF-8''" + encoded)
                .contentType(type)
                .body(f.data());
    }

    /** 첨부파일 삭제 (관리자/담당자) */
    @DeleteMapping("/kims-api/attachments/{attachmentId}")
    @PreAuthorize("@kimsPerm.canWork(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
