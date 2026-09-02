package com.company.module.safety.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.safety.dto.request.NoticeSaveRequest;
import com.company.module.safety.dto.response.NoticeResponse;
import com.company.module.safety.service.SafetyNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 안전작업방식 매뉴얼 화면의 공지사항 REST API.
 * <p>조회는 인증된 사용자 누구나, 등록/수정/삭제는 SAFETY 관리자만 가능하다.
 */
@RestController
@RequiredArgsConstructor
public class SafetyNoticeController {

    private final SafetyNoticeService noticeService;

    /** 공지 목록 (상단 고정 글이 먼저, 그 다음 최신순) */
    @GetMapping("/safety-api/notices")
    public ResponseEntity<ApiResponse<List<NoticeResponse>>> getList() {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getList()));
    }

    @PostMapping("/safety-api/notices")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<NoticeResponse>> create(
            @Valid @RequestBody NoticeSaveRequest request, Authentication authentication) {
        String createdBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.created(noticeService.create(request, createdBy)));
    }

    @PutMapping("/safety-api/notices/{noticeId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<NoticeResponse>> update(
            @PathVariable Long noticeId, @Valid @RequestBody NoticeSaveRequest request,
            Authentication authentication) {
        String updatedBy = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(noticeService.update(noticeId, request, updatedBy)));
    }

    @DeleteMapping("/safety-api/notices/{noticeId}")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long noticeId, Authentication authentication) {
        String deletedBy = (authentication != null) ? authentication.getName() : null;
        noticeService.delete(noticeId, deletedBy);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
