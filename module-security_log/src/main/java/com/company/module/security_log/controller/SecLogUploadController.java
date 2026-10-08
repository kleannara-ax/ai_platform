package com.company.module.security_log.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.security_log.dto.LogUploadDetailResponse;
import com.company.module.security_log.dto.LogUploadRequest;
import com.company.module.security_log.dto.LogUploadResponse;
import com.company.module.security_log.dto.LogUploadUpdateRequest;
import com.company.module.security_log.dto.MonthlySummaryResponse;
import com.company.module.security_log.dto.OriginalFileResponse;
import com.company.module.security_log.dto.UploadReviewRequest;
import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.entity.UploadStatus;
import com.company.module.security_log.service.SecLogUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

/**
 * 로그 업로드 / 분석 / 월별 모니터링 API
 * - 조회: ADMIN, MANAGER, USER
 * - 업로드/수정/재분석: ADMIN, MANAGER
 * - 최종 검토완료/검토취소/삭제: ADMIN
 */
@RestController
@RequestMapping("/security_log-api/uploads")
@RequiredArgsConstructor
public class SecLogUploadController {

    private final SecLogUploadService uploadService;

    /** 목록 화면 - 월별 조회 (targetYm: YYYYMM 필수) */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<Page<LogUploadResponse>>> getList(
            @RequestParam String targetYm,
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) LogType logType,
            @RequestParam(required = false) UploadStatus status,
            @RequestParam(required = false) Severity maxSeverity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                uploadService.getList(targetYm, systemName, logType, status, maxSeverity, page, size)));
    }

    /** 월별 모니터링 요약 */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<MonthlySummaryResponse>> getMonthlySummary(@RequestParam String targetYm) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.getMonthlySummary(targetYm)));
    }

    /** 상세 화면 */
    @GetMapping("/{uploadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<LogUploadDetailResponse>> getDetail(@PathVariable Long uploadId) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.getDetail(uploadId)));
    }

    /** 등록 화면 - 로그 파일 업로드 (multipart/form-data) + 즉시 분석 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<LogUploadResponse>> upload(
            @Valid @ModelAttribute LogUploadRequest request,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "userId") Long userId) {

        return ResponseEntity.ok(ApiResponse.created(uploadService.upload(request, file, userId)));
    }

    /** 수정 화면 - 기본정보 수정 */
    @PutMapping("/{uploadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<LogUploadResponse>> update(@PathVariable Long uploadId,
                                                                 @Valid @RequestBody LogUploadUpdateRequest request,
                                                                 @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.update(uploadId, request, userId)));
    }

    /** 현재 룰셋으로 재분석 */
    @PostMapping("/{uploadId}/reanalyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<LogUploadResponse>> reanalyze(@PathVariable Long uploadId,
                                                                    @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.reanalyze(uploadId, userId)));
    }

    /** 정보보안팀 최종 검토완료 */
    @PostMapping("/{uploadId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LogUploadResponse>> completeReview(@PathVariable Long uploadId,
                                                                         @Valid @RequestBody UploadReviewRequest request,
                                                                         @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.completeReview(uploadId, request, userId)));
    }

    /** 최종 검토완료 취소 */
    @PostMapping("/{uploadId}/review-cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LogUploadResponse>> cancelReview(@PathVariable Long uploadId,
                                                                       @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.cancelReview(uploadId, userId)));
    }

    /** 원본 로그 파일 (Base64) */
    @GetMapping("/{uploadId}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<OriginalFileResponse>> getOriginalFile(@PathVariable Long uploadId) {
        SecLogUploadService.DownloadFile file = uploadService.getOriginalFile(uploadId);
        return ResponseEntity.ok(ApiResponse.success(OriginalFileResponse.builder()
                .uploadId(uploadId)
                .fileName(file.fileName())
                .fileSize(file.content().length)
                .contentBase64(Base64.getEncoder().encodeToString(file.content()))
                .build()));
    }

    /** 삭제 (소프트 삭제) - 관리자 전용 */
    @DeleteMapping("/{uploadId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long uploadId,
                                                    @AuthenticationPrincipal(expression = "userId") Long userId) {
        uploadService.delete(uploadId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
