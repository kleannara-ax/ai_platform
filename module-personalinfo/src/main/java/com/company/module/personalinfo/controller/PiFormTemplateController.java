package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.personalinfo.dto.FormTemplateCheckResponse;
import com.company.module.personalinfo.dto.FormTemplateGroupResponse;
import com.company.module.personalinfo.dto.FormTemplateResponse;
import com.company.module.personalinfo.service.PiExpiryFormService;
import com.company.module.personalinfo.service.PiFormTemplateService;
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
 * 양식 관리 API (보안팀 양식: 위수탁 점검 체크리스트, 정보보호 및 비밀유지 확약서, 개인정보 파기 확인서).
 *
 * <p>등록·사용 지정·되돌리기·삭제는 보안팀 관리자(ROLE_PI_ADMIN)와 플랫폼 관리자(ROLE_ADMIN)만 할 수 있다.
 * 일반·조회 사용자는 계약 화면에서 사용 중 양식을 내려받기만 한다.
 */
@RestController
@RequestMapping("/personalinfo-api/form-templates")
@RequiredArgsConstructor
public class PiFormTemplateController {

    private final PiFormTemplateService formTemplateService;
    private final PiExpiryFormService expiryFormService;

    /** 양식 종류별 사용 중 버전과 버전 이력 */
    @GetMapping
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<List<FormTemplateGroupResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(formTemplateService.list()));
    }

    /** 저장하지 않고 양식 검사만 (등록 전 미리 확인) */
    @PostMapping(value = "/{formType}/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<FormTemplateCheckResponse>> check(
            @PathVariable String formType, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(formTemplateService.check(formType, file)));
    }

    /** 새 버전 등록 (검사 통과 시). activate=true(기본)면 바로 사용 중으로 지정 */
    @PostMapping(value = "/{formType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<FormTemplateResponse>> upload(
            @PathVariable String formType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "changeNote", required = false) String changeNote,
            @RequestParam(value = "activate", defaultValue = "true") boolean activate,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId,
            @AuthenticationPrincipal(expression = "userDisplayName") String currentUserName) {
        return ResponseEntity.ok(ApiResponse.success(
                activate ? "새 양식을 등록하고 사용 중으로 지정했습니다." : "새 양식을 등록했습니다.",
                formTemplateService.upload(formType, file, changeNote, activate, currentUserId, currentUserName)));
    }

    /** 이 버전을 사용 중으로 지정 (이전 버전으로 되돌리기) */
    @PostMapping("/versions/{formTemplateId}/activate")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<FormTemplateResponse>> activate(
            @PathVariable Long formTemplateId,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId,
            @AuthenticationPrincipal(expression = "userDisplayName") String currentUserName) {
        return ResponseEntity.ok(ApiResponse.success(
                formTemplateService.activate(formTemplateId, currentUserId, currentUserName)));
    }

    /** 모듈 내장 기본 양식으로 되돌리기 */
    @PostMapping("/{formType}/use-default")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<Void>> useDefault(
            @PathVariable String formType,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        formTemplateService.useDefault(formType, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("기본 양식으로 되돌렸습니다.", (Void) null));
    }

    /** 버전 삭제 (소프트, 사용 중 버전은 불가) */
    @DeleteMapping("/versions/{formTemplateId}")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long formTemplateId,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        formTemplateService.delete(formTemplateId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("양식 버전을 삭제했습니다.", (Void) null));
    }

    /** 버전 원본 다운로드 */
    @GetMapping("/versions/{formTemplateId}/download")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<byte[]> download(@PathVariable Long formTemplateId) {
        return PiDownloads.of(formTemplateService.download(formTemplateId));
    }

    /** 기본(모듈 내장) 양식 원본 다운로드 */
    @GetMapping("/{formType}/default/download")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<byte[]> downloadDefault(@PathVariable String formType) {
        return PiDownloads.of(formTemplateService.downloadDefault(formType));
    }

    /** 미리보기: 이 버전에 샘플 계약 정보를 채운 양식 (체크리스트는 만료 점검 시트가 열린 상태) */
    @GetMapping("/versions/{formTemplateId}/preview")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<byte[]> preview(@PathVariable Long formTemplateId) {
        return PiDownloads.of(expiryFormService.preview(formTemplateService.source(formTemplateId)));
    }

    /** 미리보기: 기본 양식에 샘플 계약 정보를 채운 양식 */
    @GetMapping("/{formType}/default/preview")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<byte[]> previewDefault(@PathVariable String formType) {
        return PiDownloads.of(expiryFormService.preview(
                formTemplateService.defaultSource(com.company.module.personalinfo.entity.FormTemplateType.of(formType))));
    }
}
