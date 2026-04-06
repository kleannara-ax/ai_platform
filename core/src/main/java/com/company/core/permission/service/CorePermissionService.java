package com.company.core.permission.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.permission.dto.*;
import com.company.core.permission.entity.*;
import com.company.core.permission.repository.*;
import com.company.core.permission.support.RoleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorePermissionService {

    private final CorePermissionRepository permRepo;
    private final CoreRoleMenuRepository roleMenuRepo;
    private final CoreRolePermissionRepository rolePermRepo;
    private final RoleProvider roleProvider;

    // ── 권한 CRUD ──

    public List<PermissionResponse> getAllPermissions() {
        return permRepo.findByIsActiveTrueOrderByPermCode().stream()
                .map(PermissionResponse::from).collect(Collectors.toList());
    }

    public PermissionResponse getPermission(Long permId) {
        return PermissionResponse.from(permRepo.findById(permId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional
    public PermissionResponse createPermission(PermissionRequest req) {
        if (permRepo.existsByPermCode(req.getPermCode())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "이미 존재하는 권한 코드입니다.");
        }
        CorePermission perm = CorePermission.builder()
                .permCode(req.getPermCode()).permName(req.getPermName())
                .description(req.getDescription())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();
        return PermissionResponse.from(permRepo.save(perm));
    }

    @Transactional
    public PermissionResponse updatePermission(Long permId, PermissionRequest req) {
        CorePermission perm = permRepo.findById(permId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        perm.update(req.getPermName(), req.getDescription(),
                req.getIsActive() != null ? req.getIsActive() : perm.getIsActive());
        return PermissionResponse.from(perm);
    }

    // ── 역할-메뉴 매핑 ──

    public RoleMappingResponse getRoleMapping(String role) {
        List<Long> menuIds = roleMenuRepo.findByRole(role).stream()
                .map(CoreRoleMenu::getMenuId).collect(Collectors.toList());
        List<Long> permIds = rolePermRepo.findByRole(role).stream()
                .map(CoreRolePermission::getPermId).collect(Collectors.toList());
        // 공통코드 ROLE 그룹에서 역할 설명을 조회
        Map<String, String> roleNameMap = roleProvider.getRoleNameMap();
        String desc = roleNameMap.getOrDefault(role, role);
        return RoleMappingResponse.builder()
                .role(role).roleDescription(desc)
                .menuIds(menuIds).permissionIds(permIds).build();
    }

    /**
     * 모든 역할별 매핑 조회 – 공통코드 ROLE 그룹에서 활성 역할 목록을 가져온다.
     */
    public List<RoleMappingResponse> getAllRoleMappings() {
        List<String> roles = roleProvider.getActiveRoles();
        return roles.stream()
                .map(this::getRoleMapping)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleMappingResponse updateRoleMenus(String role, List<Long> menuIds) {
        roleMenuRepo.deleteByRole(role);
        roleMenuRepo.flush();
        if (menuIds != null) {
            menuIds.forEach(mid -> roleMenuRepo.save(
                    CoreRoleMenu.builder().role(role).menuId(mid).build()));
        }
        return getRoleMapping(role);
    }

    @Transactional
    public RoleMappingResponse updateRolePermissions(String role, List<Long> permIds) {
        rolePermRepo.deleteByRole(role);
        rolePermRepo.flush();
        if (permIds != null) {
            permIds.forEach(pid -> rolePermRepo.save(
                    CoreRolePermission.builder().role(role).permId(pid).build()));
        }
        return getRoleMapping(role);
    }

    @Transactional
    public RoleMappingResponse updateRoleMapping(RoleMappingRequest req) {
        if (req.getMenuIds() != null) updateRoleMenus(req.getRole(), req.getMenuIds());
        if (req.getPermissionIds() != null) updateRolePermissions(req.getRole(), req.getPermissionIds());
        return getRoleMapping(req.getRole());
    }
}
