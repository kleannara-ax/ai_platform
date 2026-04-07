package com.company.core.menu.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.menu.dto.MenuRequest;
import com.company.core.menu.dto.MenuResponse;
import com.company.core.menu.service.CoreMenuService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
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
        log.info("[메뉴조회] role={}, clientIp={}", role, clientIp);
        List<MenuResponse> menus = menuService.getMenuTreeByRole(role, clientIp);
        log.info("[메뉴조회] 반환 메뉴 수={}, 메뉴목록={}", menus.size(),
                menus.stream().map(m -> m.getMenuCode() + "(allowedIps=" + m.getAllowedIps() + ")").collect(java.util.stream.Collectors.joining(", ")));
        return ResponseEntity.ok(ApiResponse.success(menus));
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
        log.info("[IP조회] resolvedIp={}", ip);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ip", ip)));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String h : headers) {
            String v = request.getHeader(h);
            if (v != null && !v.isBlank() && !"unknown".equalsIgnoreCase(v)) {
                String resolved = v.split(",")[0].trim();
                log.info("[IP해석] header={}  value={}  resolved={}", h, v, resolved);
                return resolved;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        log.info("[IP해석] 프록시 헤더 없음, remoteAddr={}", remoteAddr);
        return remoteAddr;
    }
}
