package com.company.core.menu.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.menu.dto.MenuRequest;
import com.company.core.menu.dto.MenuResponse;
import com.company.core.menu.service.CoreMenuService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/core/menus")
@RequiredArgsConstructor
public class CoreMenuController {

    private final CoreMenuService menuService;

    /** 전체 메뉴 트리 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenuTree() {
        return ResponseEntity.ok(ApiResponse.success(menuService.getMenuTree()));
    }

    /** 플랫 리스트 조회 */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getAllMenus() {
        return ResponseEntity.ok(ApiResponse.success(menuService.getAllMenus()));
    }

    /** 역할별 메뉴 트리 (접속자 IP 기반 필터링 포함) */
    @GetMapping("/role/{role}")
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenusByRole(
            @PathVariable String role, HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        return ResponseEntity.ok(ApiResponse.success(menuService.getMenuTreeByRole(role, clientIp)));
    }

    /** 메뉴 상세 */
    @GetMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuResponse>> getMenu(@PathVariable Long menuId) {
        return ResponseEntity.ok(ApiResponse.success(menuService.getMenu(menuId)));
    }

    /** 메뉴 생성 (ADMIN) */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MenuResponse>> createMenu(@Valid @RequestBody MenuRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(menuService.createMenu(req)));
    }

    /** 메뉴 수정 (ADMIN) */
    @PutMapping("/{menuId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MenuResponse>> updateMenu(
            @PathVariable Long menuId, @Valid @RequestBody MenuRequest req) {
        return ResponseEntity.ok(ApiResponse.success(menuService.updateMenu(menuId, req)));
    }

    /** 메뉴 삭제 (ADMIN) */
    @DeleteMapping("/{menuId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMenu(@PathVariable Long menuId) {
        menuService.deleteMenu(menuId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 현재 접속자 IP 조회 */
    @GetMapping("/my-ip")
    public ResponseEntity<ApiResponse<Map<String, String>>> getClientIp(HttpServletRequest request) {
        String ip = resolveClientIp(request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ip", ip)));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String h : headers) {
            String v = request.getHeader(h);
            if (v != null && !v.isBlank() && !"unknown".equalsIgnoreCase(v)) {
                return v.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
