package com.company.core.menu.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.menu.dto.MenuRequest;
import com.company.core.menu.dto.MenuResponse;
import com.company.core.menu.entity.CoreMenu;
import com.company.core.menu.repository.CoreMenuRepository;
import com.company.core.permission.repository.CoreRoleMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreMenuService {

    private final CoreMenuRepository menuRepository;
    private final CoreRoleMenuRepository roleMenuRepository;

    /** 전체 메뉴 목록 (플랫 리스트) */
    public List<MenuResponse> getAllMenus() {
        return menuRepository.findByIsActiveTrueOrderBySortOrder().stream()
                .map(MenuResponse::from).collect(Collectors.toList());
    }

    /** 전체 메뉴 트리 구조 */
    public List<MenuResponse> getMenuTree() {
        List<CoreMenu> all = menuRepository.findByIsActiveTrueOrderBySortOrder();
        return buildTree(all);
    }

    /** 역할별 접근 가능 메뉴 트리 */
    public List<MenuResponse> getMenuTreeByRole(String role) {
        return getMenuTreeByRole(role, null);
    }

    /** 역할별 접근 가능 메뉴 트리 (IP 필터링 포함) */
    public List<MenuResponse> getMenuTreeByRole(String role, String clientIp) {
        List<Long> menuIds = roleMenuRepository.findByRole(role).stream()
                .map(rm -> rm.getMenuId()).collect(Collectors.toList());
        if (menuIds.isEmpty()) return Collections.emptyList();
        List<CoreMenu> menus = menuRepository.findByMenuIdInAndIsActiveTrueOrderBySortOrder(menuIds);
        // IP 필터링: allowedIps가 설정된 메뉴는 clientIp가 허용 목록에 포함되어야 함
        if (clientIp != null && !clientIp.isBlank()) {
            menus = menus.stream()
                    .filter(m -> isIpAllowed(m.getAllowedIps(), clientIp))
                    .collect(Collectors.toList());
        }
        return buildTree(menus);
    }

    /** 메뉴 상세 */
    public MenuResponse getMenu(Long menuId) {
        CoreMenu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return MenuResponse.from(menu);
    }

    /** 메뉴 생성 */
    @Transactional
    public MenuResponse createMenu(MenuRequest req) {
        if (menuRepository.existsByMenuCode(req.getMenuCode())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "이미 존재하는 메뉴 코드입니다.");
        }
        CoreMenu menu = CoreMenu.builder()
                .menuName(req.getMenuName()).menuCode(req.getMenuCode())
                .parentId(req.getParentId()).menuUrl(req.getMenuUrl()).icon(req.getIcon())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .menuType(req.getMenuType() != null ? req.getMenuType() : "MENU")
                .isVisible(req.getIsVisible() != null ? req.getIsVisible() : true)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .description(req.getDescription())
                .allowedIps(normalizeIps(req.getAllowedIps()))
                .build();
        return MenuResponse.from(menuRepository.save(menu));
    }

    /** 메뉴 수정 */
    @Transactional
    public MenuResponse updateMenu(Long menuId, MenuRequest req) {
        CoreMenu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        menu.update(req.getMenuName(), req.getMenuUrl(), req.getIcon(),
                req.getSortOrder() != null ? req.getSortOrder() : menu.getSortOrder(),
                req.getMenuType() != null ? req.getMenuType() : menu.getMenuType(),
                req.getIsVisible() != null ? req.getIsVisible() : menu.getIsVisible(),
                req.getIsActive() != null ? req.getIsActive() : menu.getIsActive(),
                req.getDescription(), req.getParentId(),
                normalizeIps(req.getAllowedIps()));
        return MenuResponse.from(menu);
    }

    /** 메뉴 삭제 (하위 메뉴가 있으면 함께 삭제) */
    @Transactional
    public void deleteMenu(Long menuId) {
        if (!menuRepository.existsById(menuId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // 하위 메뉴 재귀 삭제
        deleteMenuRecursive(menuId);
    }

    /** 하위 메뉴를 재귀적으로 삭제 */
    private void deleteMenuRecursive(Long menuId) {
        List<CoreMenu> children = menuRepository.findByParentIdOrderBySortOrder(menuId);
        for (CoreMenu child : children) {
            deleteMenuRecursive(child.getMenuId());
        }
        // 역할-메뉴 매핑도 삭제
        roleMenuRepository.deleteByMenuId(menuId);
        menuRepository.deleteById(menuId);
    }

    /** 전체 메뉴 목록 (비활성 포함, 관리용) */
    public List<MenuResponse> getAllMenusIncludeInactive() {
        return menuRepository.findAllByOrderBySortOrder().stream()
                .map(MenuResponse::from).collect(Collectors.toList());
    }

    /** 전체 메뉴 트리 (비활성 포함, 관리용) */
    public List<MenuResponse> getMenuTreeAll() {
        List<CoreMenu> all = menuRepository.findAllByOrderBySortOrder();
        return buildTree(all);
    }

    /**
     * IP 문자열 정규화: 공백 제거, 유효성 검증, 빈 문자열은 null 반환
     * 지원 형식: 단일IP(192.168.1.1), CIDR(192.168.1.0/24), 와일드카드(192.168.1.*), 범위(192.168.1.1-192.168.1.254)
     */
    private String normalizeIps(String ips) {
        if (ips == null || ips.isBlank()) return null;
        List<String> entries = Arrays.stream(ips.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        for (String entry : entries) {
            if (!isValidIpEntry(entry)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "유효하지 않은 IP 형식입니다: " + entry +
                        " (허용 형식: 192.168.1.1, 192.168.1.0/24, 192.168.1.*, 192.168.1.1-192.168.1.254)");
            }
        }
        String normalized = String.join(",", entries);
        return normalized.isEmpty() ? null : normalized;
    }

    /** 단일 IP 엔트리 유효성 검증 */
    private boolean isValidIpEntry(String entry) {
        // CIDR: 192.168.1.0/24
        if (entry.contains("/")) {
            String[] parts = entry.split("/", 2);
            if (!isValidIpOrWildcard(parts[0])) return false;
            try {
                int prefix = Integer.parseInt(parts[1]);
                return prefix >= 0 && prefix <= 32;
            } catch (NumberFormatException e) { return false; }
        }
        // 범위: 192.168.1.1-192.168.1.254
        if (entry.contains("-")) {
            String[] parts = entry.split("-", 2);
            return isValidIpOrWildcard(parts[0].trim()) && isValidIpOrWildcard(parts[1].trim());
        }
        // 단일 IP 또는 와일드카드
        return isValidIpOrWildcard(entry);
    }

    /** IP 또는 와일드카드(*) 포함 IP 형식 검증 */
    private boolean isValidIpOrWildcard(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if ("*".equals(octet)) continue;
            try {
                int v = Integer.parseInt(octet);
                if (v < 0 || v > 255) return false;
            } catch (NumberFormatException e) { return false; }
        }
        return true;
    }

    /**
     * 클라이언트 IP가 허용 목록에 포함되는지 확인
     * allowedIps가 null/빈값이면 제한 없음 (true 반환)
     * 지원 형식: 단일IP, CIDR, 와일드카드(*), 범위(-)
     */
    public boolean isIpAllowed(String allowedIps, String clientIp) {
        if (allowedIps == null || allowedIps.isBlank()) {
            log.info("[IP참크] allowedIps=NULL → 제한없음");
            return true;
        }
        String[] rules = allowedIps.split(",");
        for (String rule : rules) {
            String r = rule.trim();
            if (r.isEmpty()) continue;
            boolean matched = matchIpRule(r, clientIp);
            log.info("[IP참크] rule={}  clientIp={}  matched={}", r, clientIp, matched);
            if (matched) return true;
        }
        log.info("[IP참크] 접근거부! clientIp={}  allowedIps={}", clientIp, allowedIps);
        return false;
    }

    private boolean matchIpRule(String rule, String ip) {
        try {
            // CIDR: 192.168.1.0/24
            if (rule.contains("/")) {
                String[] parts = rule.split("/", 2);
                long mask = 0xFFFFFFFFL << (32 - Integer.parseInt(parts[1]));
                return (ipToLong(parts[0]) & mask) == (ipToLong(ip) & mask);
            }
            // 와일드카드: 192.168.*.*
            if (rule.contains("*")) {
                String[] rParts = rule.split("\\.", -1);
                String[] iParts = ip.split("\\.", -1);
                if (rParts.length != 4 || iParts.length != 4) return false;
                for (int i = 0; i < 4; i++) {
                    if (!"*".equals(rParts[i]) && !rParts[i].equals(iParts[i])) return false;
                }
                return true;
            }
            // 범위: 192.168.1.1-192.168.1.254
            if (rule.contains("-")) {
                String[] parts = rule.split("-", 2);
                long v = ipToLong(ip);
                return v >= ipToLong(parts[0].trim()) && v <= ipToLong(parts[1].trim());
            }
            // 정확히 일치
            return rule.equals(ip);
        } catch (Exception e) {
            log.warn("IP 매칭 오류: rule={}, ip={}, error={}", rule, ip, e.getMessage());
            return false;
        }
    }

    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        return ((Long.parseLong(parts[0]) << 24) | (Long.parseLong(parts[1]) << 16)
                | (Long.parseLong(parts[2]) << 8) | Long.parseLong(parts[3]));
    }

    private List<MenuResponse> buildTree(List<CoreMenu> menus) {
        Map<Long, MenuResponse> map = new LinkedHashMap<>();
        menus.forEach(m -> map.put(m.getMenuId(), MenuResponse.from(m)));
        List<MenuResponse> roots = new ArrayList<>();
        map.values().forEach(m -> {
            if (m.getParentId() != null && map.containsKey(m.getParentId())) {
                map.get(m.getParentId()).getChildren().add(m);
            } else {
                roots.add(m);
            }
        });
        return roots;
    }
}
