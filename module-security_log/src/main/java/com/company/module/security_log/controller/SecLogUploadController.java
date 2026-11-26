package com.company.module.security_log.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.security_log.dto.AccessReportResponse;
import com.company.module.security_log.dto.AccountReviewRequest;
import com.company.module.security_log.dto.AccountReviewResponse;
import com.company.module.security_log.dto.DlpLogRowResponse;
import com.company.module.security_log.dto.DlpReportResponse;
import com.company.module.security_log.dto.RuleEvidenceResponse;
import com.company.module.security_log.dto.PiiDownloadResponse;
import com.company.module.security_log.dto.PiiDownloadReviewRequest;
import com.company.module.security_log.dto.PiiDownloadReviewResponse;
import com.company.module.security_log.dto.RuleResultsResponse;
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
import com.company.module.security_log.service.SecLogAccountReviewService;
import com.company.module.security_log.service.SecLogDlpReportService;
import com.company.module.security_log.service.SecLogPiiDownloadService;
import com.company.module.security_log.service.SecLogRuleEvidenceService;
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
import java.util.List;

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
    private final SecLogAccountReviewService accountReviewService;
    private final SecLogDlpReportService dlpReportService;
    private final SecLogRuleEvidenceService ruleEvidenceService;
    private final SecLogPiiDownloadService piiDownloadService;

    /** 목록 화면 - 월별 조회 (targetYm: YYYYMM 필수) */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<Page<LogUploadResponse>>> getList(
            @RequestParam String targetYm,
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) LogType logType,
            @RequestParam(required = false) UploadStatus status,
            @RequestParam(required = false) Severity maxSeverity,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "false") boolean latestOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                uploadService.getList(targetYm, systemName, logType, status, maxSeverity, sort, activeOnly, latestOnly, page, size)));
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

    /** 접속 행위 분석 (계정별 로그인 성공/실패, 업무시간 외, 외부 IP, 다중 IP, 특권계정) */
    @GetMapping("/{uploadId}/access")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<AccessReportResponse>> getAccessReport(@PathVariable Long uploadId) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.getAccessReport(uploadId)));
    }

    /**
     * 접속 계정 검토 저장 (선택한 계정 일괄) - ADMIN, MANAGER (모든 시스템)
     * reviewStatus: NORMAL / CONFIRMED / RESOLVED / PENDING(미검토로 되돌리기). CONFIRMED·RESOLVED 는 소명·조치 내용 필수
     */
    @PutMapping("/{uploadId}/access/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AccountReviewResponse>> reviewAccounts(
            @PathVariable Long uploadId,
            @Valid @RequestBody AccountReviewRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(accountReviewService.review(uploadId, request, userId)));
    }

    /** 개인정보 다운로드 내역 + 사유 검토 현황 (자사몰·임직원몰만 해당, 그 외 target=false) */
    @GetMapping("/{uploadId}/pii-downloads")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<PiiDownloadResponse>> getPiiDownloads(@PathVariable Long uploadId) {
        return ResponseEntity.ok(ApiResponse.success(piiDownloadService.list(uploadId)));
    }

    /**
     * 개인정보 다운로드 사유 검토 저장 (선택한 건 일괄) - ADMIN, MANAGER
     * reviewStatus: NORMAL / CONFIRMED / RESOLVED / PENDING(미검토로 되돌리기). PENDING 외에는 사유·확인 내용 필수
     */
    @PutMapping("/{uploadId}/pii-downloads/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PiiDownloadReviewResponse>> reviewPiiDownloads(
            @PathVariable Long uploadId,
            @Valid @RequestBody PiiDownloadReviewRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(piiDownloadService.review(uploadId, request, userId)));
    }

    /** 룰셋별 분석 결과 (적용 룰마다 분석 완료·실패·미분석 + 검토 대상·검토 완료 건수) */
    @GetMapping("/{uploadId}/rule-results")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<RuleResultsResponse>> getRuleResults(@PathVariable Long uploadId) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.getRuleResults(uploadId)));
    }

    /** 룰셋 분석 근거 (룰 1개: 탐지 조건 · 왜 검토 대상인지 · 일치한 로그 줄과 일치 부분) */
    @GetMapping("/{uploadId}/rule-results/evidence")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<RuleEvidenceResponse>> getRuleEvidence(@PathVariable Long uploadId,
                                                                             @RequestParam String ruleCode) {
        return ResponseEntity.ok(ApiResponse.success(ruleEvidenceService.getEvidence(uploadId, ruleCode)));
    }

    /** DLP 관리자 감사 로그 점검 보고서 (규칙 기반, DLP 가 아니면 applicable=false) */
    @GetMapping("/{uploadId}/dlp-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DlpReportResponse>> getDlpReport(@PathVariable Long uploadId) {
        return ResponseEntity.ok(ApiResponse.success(dlpReportService.getReport(uploadId)));
    }

    /** DLP 점검 보고서 근거 로그 원문 (rows: 원본 행 번호, 쉼표 구분, 최대 200개) */
    @GetMapping("/{uploadId}/dlp-report/rows")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<DlpLogRowResponse>>> getDlpReportRows(
            @PathVariable Long uploadId, @RequestParam List<Integer> rows) {
        return ResponseEntity.ok(ApiResponse.success(dlpReportService.getRows(uploadId, rows)));
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
