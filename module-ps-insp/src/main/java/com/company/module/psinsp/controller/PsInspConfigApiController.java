package com.company.module.psinsp.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.psinsp.service.PsInspConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PS 지분 검사 설정 REST API 컨트롤러 (공통코드 기반)
 *
 * <p>URL API Prefix: /ps-insp-api/config
 * <p>PPM 기준값: code_group 'PS_INSP_DEFAULT' > 'PPM_LIMIT'
 * <p>PPM 수정 권한자: code_group 'PS_INSP_ADMIN' > 'PPM_ADMIN' (extraValue1에 콤마 구분 ID)
 *
 * <p>권한 체계:
 * <ul>
 *   <li>조회: PS_INSP_MGMT 메뉴 권한</li>
 *   <li>수정: PS_INSP_MGMT 메뉴 권한 + PS_INSP_ADMIN 그룹 PPM_ADMIN에 등록된 사용자 ID</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ps-insp-api/config")
@RequiredArgsConstructor
public class PsInspConfigApiController {

    private final PsInspConfigService configService;

    // ──────────── PPM 기준값 ────────────

    /**
     * PPM 기준값 조회
     * GET /ps-insp-api/config/ppm-limit
     */
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'PS_INSP_MGMT')")
    @GetMapping("/ppm-limit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPpmLimit(Authentication authentication) {
        double ppmLimit = configService.getPpmLimit();
        String operatorId = authentication != null ? authentication.getName() : "";
        boolean isAdmin = configService.isPpmAdmin(operatorId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "ppmLimit", ppmLimit,
                "enabled", ppmLimit > 0,
                "isAdmin", isAdmin
        )));
    }

    /**
     * PPM 기준값 저장 (PS_INSP_ADMIN > PPM_ADMIN에 등록된 사용자만 가능)
     * POST /ps-insp-api/config/ppm-limit
     * Body: { "ppmLimit": 300.0 }
     */
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'PS_INSP_MGMT')")
    @PostMapping("/ppm-limit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> savePpmLimit(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            double ppmLimit = 0;
            Object ppmVal = body.get("ppmLimit");
            if (ppmVal instanceof Number) {
                ppmLimit = ((Number) ppmVal).doubleValue();
            } else if (ppmVal instanceof String) {
                ppmLimit = Double.parseDouble((String) ppmVal);
            }

            String operatorId = authentication != null ? authentication.getName() : "unknown";
            Map<String, Object> result = configService.savePpmLimit(ppmLimit, operatorId);
            return ResponseEntity.ok(ApiResponse.success("PPM 기준값이 저장되었습니다.", result));
        } catch (IllegalArgumentException e) {
            log.warn("[PS-INSP-CONFIG] PPM 기준값 저장 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        }
    }

    // ──────────── 권한자 관리 ────────────

    /**
     * PPM 수정 권한자 목록 조회
     * GET /ps-insp-api/config/ppm-admins
     */
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'PS_INSP_MGMT')")
    @GetMapping("/ppm-admins")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPpmAdmins(Authentication authentication) {
        List<String> adminIds = configService.getPpmAdminIds();
        String operatorId = authentication != null ? authentication.getName() : "";
        boolean isAdmin = configService.isPpmAdmin(operatorId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "adminIds", adminIds,
                "isAdmin", isAdmin,
                "currentUser", operatorId
        )));
    }

    /**
     * PPM 수정 권한자 목록 업데이트 (기존 권한자만 가능)
     * POST /ps-insp-api/config/ppm-admins
     * Body: { "adminIds": ["admin", "ykcho", "hsjeong"] }
     */
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'PS_INSP_MGMT')")
    @PostMapping("/ppm-admins")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePpmAdmins(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            List<String> adminIds = (List<String>) body.get("adminIds");
            if (adminIds == null) {
                return ResponseEntity.badRequest().body(ApiResponse.fail("adminIds 필드가 필요합니다."));
            }

            String operatorId = authentication != null ? authentication.getName() : "unknown";
            List<String> result = configService.updatePpmAdminIds(adminIds, operatorId);

            return ResponseEntity.ok(ApiResponse.success("권한자 목록이 업데이트되었습니다.", Map.of(
                    "adminIds", result,
                    "updatedBy", operatorId
            )));
        } catch (IllegalArgumentException e) {
            log.warn("[PS-INSP-CONFIG] 권한자 목록 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        }
    }

    // ──────────── 전체 설정 ────────────

    /**
     * 전체 설정 조회
     * GET /ps-insp-api/config
     */
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'PS_INSP_MGMT')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success(configService.getAllConfigs()));
    }
}
