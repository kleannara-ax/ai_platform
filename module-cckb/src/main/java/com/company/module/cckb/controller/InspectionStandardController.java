package com.company.module.cckb.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.cckb.dto.request.InspectionStandardUpdateRequest;
import com.company.module.cckb.dto.response.InspectionStandardResponse;
import com.company.module.cckb.service.InspectionStandardService;
import com.company.module.cckb.support.CckbPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "⚙ 기준값 설정" 탭 API.
 * 조회는 모든 인증 사용자 가능, 수정은 CCKB_PERM 관리자만 가능
 * (원본 V4 HTML 의 isAuthorized() 스텁을 실제 권한 검증으로 대체).
 */
@RestController
@RequestMapping("/cckb-api/standards")
@RequiredArgsConstructor
public class InspectionStandardController {

    private final InspectionStandardService standardService;
    private final CckbPermission cckbPermission;

    /** GET /cckb-api/standards/active — 현재 활성 기준값 조회 */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<InspectionStandardResponse>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(standardService.getActiveStandard()));
    }

    /** PUT /cckb-api/standards/active — 기준값 수정 (관리자 전용) */
    @PutMapping("/active")
    public ResponseEntity<ApiResponse<InspectionStandardResponse>> update(
            @Valid @RequestBody InspectionStandardUpdateRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            Authentication authentication) {
        verifyAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success(standardService.update(request, userId)));
    }

    private void verifyAdmin(Authentication authentication) {
        if (!cckbPermission.isAdmin(authentication)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "기준값 설정 변경 권한이 없습니다.");
        }
    }
}
