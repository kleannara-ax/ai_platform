package com.company.module.cckb.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.cckb.dto.request.CckbAdminAddRequest;
import com.company.module.cckb.dto.response.CckbAdminResponse;
import com.company.module.cckb.dto.response.CckbUserOptionResponse;
import com.company.module.cckb.service.CckbAdminService;
import com.company.module.cckb.support.CckbPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * CCKB_PERM(공통코드) 관리자 명단을 UI에서 직접 관리하기 위한 API.
 *
 * <p>권한 체계:
 * <ul>
 *   <li>조회(GET /me, /list): 인증된 사용자 누구나 가능 — 본인이 관리자인지 확인 용도</li>
 *   <li>추가/제거(POST, DELETE): {@link CckbPermission#isAdmin(Authentication)} 를 통과한 사람만
 *       가능 — 플랫폼 ROLE_ADMIN 이거나, 이미 CCKB_PERM 명단에 등록된 사람.
 *       즉 ROLE_ADMIN 이 최초 관리자를 추가하면, 그 사람도 이후 새 관리자를 추가할 수 있다.</li>
 * </ul>
 */
@RestController
@RequestMapping("/cckb-api/admins")
@RequiredArgsConstructor
public class CckbAdminController {

    private final CckbAdminService cckbAdminService;
    private final CckbPermission cckbPermission;

    /** 현재 로그인한 사용자의 CCKB 관리 권한 여부 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(Authentication authentication) {
        boolean isAdmin = cckbPermission.isAdmin(authentication);
        String loginId = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "loginId", loginId == null ? "" : loginId,
                "isAdmin", isAdmin
        )));
    }

    /** CCKB_PERM 명단 조회 (조회는 인증만 되면 가능) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CckbAdminResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(cckbAdminService.listAdmins()));
    }

    /**
     * 관리자 추가 모달의 사용자 검색(이름/아이디)용 — AI플랫폼 전체 활성 사용자 목록.
     * (세부공장일보 컬럼관리 '권한추가'의 사용자 검색 박스와 동일한 용도)
     */
    @GetMapping("/platform-users")
    public ResponseEntity<ApiResponse<List<CckbUserOptionResponse>>> platformUsers() {
        return ResponseEntity.ok(ApiResponse.success(cckbAdminService.listPlatformUsers()));
    }

    /** 관리자 추가 — ROLE_ADMIN 이거나 CCKB_PERM 기존 등록자만 가능 */
    @PostMapping
    public ResponseEntity<ApiResponse<List<CckbAdminResponse>>> add(
            @Valid @RequestBody CckbAdminAddRequest request,
            Authentication authentication) {
        verifyAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success("관리자가 추가되었습니다.", cckbAdminService.addAdmin(request)));
    }

    /** 관리자 제거(비활성화) — ROLE_ADMIN 이거나 CCKB_PERM 기존 등록자만 가능 */
    @DeleteMapping("/{loginId}")
    public ResponseEntity<ApiResponse<List<CckbAdminResponse>>> remove(
            @PathVariable String loginId,
            Authentication authentication) {
        verifyAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success("관리자가 제외되었습니다.", cckbAdminService.removeAdmin(loginId)));
    }

    private void verifyAdmin(Authentication authentication) {
        if (!cckbPermission.isAdmin(authentication)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "CCKB 관리자 권한이 없습니다.");
        }
    }
}
