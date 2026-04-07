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

import java.util.*;
import java.util.stream.Collectors;

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
        List<Long> menuIds = roleMenuRepository.findByRole(role).stream()
                .map(rm -> rm.getMenuId()).collect(Collectors.toList());
        if (menuIds.isEmpty()) return Collections.emptyList();
        List<CoreMenu> menus = menuRepository.findByMenuIdInAndIsActiveTrueOrderBySortOrder(menuIds);
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

    /** 메뉴 삭제 */
    @Transactional
    public void deleteMenu(Long menuId) {
        if (!menuRepository.existsById(menuId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        menuRepository.deleteById(menuId);
    }

    /**
     * IP 문자열 정규화: 공백 제거, 빈 문자열은 null 반환
     */
    private String normalizeIps(String ips) {
        if (ips == null || ips.isBlank()) return null;
        String normalized = Arrays.stream(ips.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        return normalized.isEmpty() ? null : normalized;
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
