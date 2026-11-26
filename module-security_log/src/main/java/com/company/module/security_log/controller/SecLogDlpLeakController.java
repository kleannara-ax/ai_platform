package com.company.module.security_log.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.security_log.dto.DlpKakaoApprovalResponse;
import com.company.module.security_log.dto.DlpKakaoApprovalSaveRequest;
import com.company.module.security_log.dto.DlpMailRuleResponse;
import com.company.module.security_log.dto.DlpMailRuleSaveRequest;
import com.company.module.security_log.dto.DlpMailSendLogResponse;
import com.company.module.security_log.dto.DlpOrgResponse;
import com.company.module.security_log.dto.DlpOrgSaveRequest;
import com.company.module.security_log.dto.DlpLeakMonthlyResponse;
import com.company.module.security_log.dto.DlpLeakUploadRequest;
import com.company.module.security_log.dto.DlpLeakUploadResponse;
import com.company.module.security_log.dto.DlpReportDataResponse;
import com.company.module.security_log.dto.DlpReportSaveRequest;
import com.company.module.security_log.dto.OriginalFileResponse;
import com.company.module.security_log.service.SecLogDlpKakaoApprovalService;
import com.company.module.security_log.service.SecLogDlpMailRuleService;
import com.company.module.security_log.service.SecLogDlpOrgService;
import com.company.module.security_log.service.SecLogDlpLeakReportService;
import com.company.module.security_log.service.SecLogDlpLeakService;
import com.company.module.security_log.service.SecLogDlpMonitorReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.Map;

/**
 * DLP 모니터링 API (ISMS 로그 점검과 별도 · 월 1회 유출 로그 업로드 → 보고서 작성(입력) → 보고서 PPT)
 * - 조회: ADMIN, MANAGER, USER
 * - 업로드: ADMIN, MANAGER
 * - 삭제: ADMIN
 */
@RestController
@RequestMapping("/security_log-api/dlp-leak")
@RequiredArgsConstructor
public class SecLogDlpLeakController {

    private final SecLogDlpLeakService dlpLeakService;
    private final SecLogDlpLeakReportService reportService;
    private final SecLogDlpMonitorReportService monitorReportService;
    private final SecLogDlpKakaoApprovalService kakaoApprovalService;
    private final SecLogDlpMailRuleService mailRuleService;
    private final SecLogDlpOrgService orgService;

    // ================================================================== DLP 관리 > 사업부·부서 관리

    /** 사업부·부서 목록 (사업부마다 아래 부서, 사용 안 함 포함 - 승인 대장 드롭다운은 사용 중만 씀) */
    @GetMapping("/orgs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<DlpOrgResponse>>> getOrgs() {
        return ResponseEntity.ok(ApiResponse.success(orgService.getList()));
    }

    /** 사업부 추가 (parentOrgId 없음) / 부서 추가 (parentOrgId = 사업부 ID) */
    @PostMapping("/orgs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Long>> createOrg(
            @Valid @RequestBody DlpOrgSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(orgService.create(request, userId)));
    }

    /** 사업부·부서 이름·사용 여부 수정 (삭제 대신 사용 안 함) */
    @PutMapping("/orgs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateOrg(
            @PathVariable Long id, @Valid @RequestBody DlpOrgSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        orgService.update(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ================================================================== DLP 관리 > 메일 알림 관리

    /** 메일 알림 규칙 목록 (메일 서버 설정 여부, 자리표시 안내, 규칙별 대기·보냄·실패 수) */
    @GetMapping("/mail-rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DlpMailRuleResponse>> getMailRules() {
        return ResponseEntity.ok(ApiResponse.success(mailRuleService.getRules()));
    }

    /** 메일 알림 규칙 추가 */
    @PostMapping("/mail-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Long>> createMailRule(
            @Valid @RequestBody DlpMailRuleSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(mailRuleService.create(request, userId)));
    }

    /** 메일 알림 규칙 수정 */
    @PutMapping("/mail-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateMailRule(
            @PathVariable Long id, @Valid @RequestBody DlpMailRuleSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        mailRuleService.update(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 메일 알림 규칙 소프트 삭제 (보낸 기록은 남김) */
    @DeleteMapping("/mail-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMailRule(
            @PathVariable Long id, @AuthenticationPrincipal(expression = "userId") Long userId) {
        mailRuleService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 메일 알림 규칙 지금 보내기 (자동 실행과 같은 규칙, 이미 보낸 건 제외) */
    @PostMapping("/mail-rules/{id}/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runMailRule(
            @PathVariable Long id, @AuthenticationPrincipal(expression = "userId") Long userId) {
        String result = mailRuleService.run(id, true, userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("result", result)));
    }

    /** 메일 알림 보낸 기록 (최근 300건, ruleId 로 거르기) */
    @GetMapping("/mail-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<DlpMailSendLogResponse>>> getMailLogs(
            @RequestParam(required = false) Long ruleId) {
        return ResponseEntity.ok(ApiResponse.success(mailRuleService.getLogs(ruleId)));
    }

    /** 카카오톡 사용 승인 대장 목록 (상태·최신여부는 오늘 기준 계산) */
    @GetMapping("/kakao-approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DlpKakaoApprovalResponse>> getKakaoApprovals() {
        return ResponseEntity.ok(ApiResponse.success(kakaoApprovalService.getList()));
    }

    /** 승인 대장 등록 (전자결재 승인 건, 신청 상태 = 신청중) */
    @PostMapping("/kakao-approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Long>> createKakaoApproval(
            @Valid @RequestBody DlpKakaoApprovalSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(kakaoApprovalService.create(request, userId)));
    }

    /** 승인 대장 수정 */
    @PutMapping("/kakao-approvals/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateKakaoApproval(
            @PathVariable Long id, @Valid @RequestBody DlpKakaoApprovalSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        kakaoApprovalService.update(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 승인 대장 삭제 (소프트 삭제) */
    @DeleteMapping("/kakao-approvals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteKakaoApproval(
            @PathVariable Long id, @AuthenticationPrincipal(expression = "userId") Long userId) {
        kakaoApprovalService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 승인 대장 엑셀 내려받기 (화면과 같은 거르기: state, latestOnly, q) - Base64 */
    @GetMapping("/kakao-approvals/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<OriginalFileResponse>> exportKakaoApprovals(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "false") boolean latestOnly,
            @RequestParam(required = false) String q) {
        SecLogDlpKakaoApprovalService.ExcelFile f = kakaoApprovalService.export(state, latestOnly, q);
        return ResponseEntity.ok(ApiResponse.success(OriginalFileResponse.builder()
                .fileName(f.fileName()).fileSize(f.content().length)
                .contentBase64(java.util.Base64.getEncoder().encodeToString(f.content())).build()));
    }

    /** 보고서 자료 (회사 양식 8장 기준: 계산값 + 사람이 적은 값) */
    @GetMapping("/report-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DlpReportDataResponse>> getReportData(@RequestParam String targetYm) {
        return ResponseEntity.ok(ApiResponse.success(monitorReportService.getData(targetYm)));
    }

    /** 보고서 입력값 저장 (표지 정보 + 승인여부·검토 결과·사전승인·사유·조치·부서 인원·보안 위험도) */
    @PutMapping("/report-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DlpReportDataResponse>> saveReportData(
            @RequestParam String targetYm,
            @Valid @RequestBody DlpReportSaveRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        monitorReportService.save(targetYm, request, userId);
        return ResponseEntity.ok(ApiResponse.success(monitorReportService.getData(targetYm)));
    }

    /**
     * Privacy-I 목록 파일 올리기 (multipart: file, targetYm, kind=AGENT|REINSTALL|OFFLINE) - 같은 달·같은 종류는 교체
     * - AGENT = Privacy-I Agent 현황 파일 1개로 재설치(설치 일시가 그 달) + 장기 미접속(미접속일 7일 이상) 함께
     */
    @PostMapping(value = "/privacyi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadPrivacyi(
            @RequestParam String targetYm,
            @RequestParam String kind,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        if (SecLogDlpMonitorReportService.KIND_AGENT.equalsIgnoreCase(kind == null ? "" : kind.trim())) {
            SecLogDlpMonitorReportService.AgentUploadResult a = monitorReportService.uploadAgent(targetYm, file, userId);
            return ResponseEntity.ok(ApiResponse.created(Map.of("targetYm", targetYm, "kind", SecLogDlpMonitorReportService.KIND_AGENT,
                    "fileName", a.fileName(), "totalCount", a.offline().total(),
                    "reinstallCount", a.reinstall().saved(), "reinstallOtherMonth", a.reinstall().shortCount(), "reinstallNoDate", a.reinstall().unknownCount(),
                    "offlineCount", a.offline().saved(), "offlineShort", a.offline().shortCount(), "offlineNoDate", a.offline().unknownCount())));
        }
        SecLogDlpMonitorReportService.PiUploadResult r = monitorReportService.uploadPrivacyi(targetYm, kind, file, userId);
        return ResponseEntity.ok(ApiResponse.created(Map.of("targetYm", targetYm, "kind", kind.toUpperCase(), "rowCount", r.saved(),
                "totalCount", r.total(), "shortCount", r.shortCount(), "unknownCount", r.unknownCount())));
    }

    /** DLP 모니터링 보고서 PPT (그 달 가장 최근 업로드 기준, Base64) */
    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<OriginalFileResponse>> getReport(@RequestParam String targetYm) {
        return ResponseEntity.ok(ApiResponse.success(reportService.build(targetYm)));
    }

    /** 그 달 올린 유출 로그 파일 (첫 번째 = 보고서 기준) */
    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<DlpLeakMonthlyResponse>> getMonthly(@RequestParam String targetYm) {
        return ResponseEntity.ok(ApiResponse.success(dlpLeakService.getMonthly(targetYm)));
    }

    /** DLP 유출 로그 업로드 + 즉시 분석 (multipart: file, targetYm, remark) */
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DlpLeakUploadResponse>> upload(
            @Valid @ModelAttribute DlpLeakUploadRequest request,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(dlpLeakService.upload(request, file, userId)));
    }

    /** 원본 파일 (Base64) */
    @GetMapping("/uploads/{leakUploadId}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<OriginalFileResponse>> getFile(@PathVariable Long leakUploadId) {
        return ResponseEntity.ok(ApiResponse.success(dlpLeakService.getOriginalFile(leakUploadId)));
    }

    /** 삭제 (소프트 삭제: 업로드 + 원본 + 이벤트) */
    @DeleteMapping("/uploads/{leakUploadId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long leakUploadId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        dlpLeakService.delete(leakUploadId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
