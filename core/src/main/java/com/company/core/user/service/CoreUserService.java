package com.company.core.user.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserResponse;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.entity.CoreUser;
import com.company.core.user.entity.CoreUserRole;
import com.company.core.user.repository.CoreUserRepository;
import com.company.core.user.repository.CoreUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 관리 서비스 (Core)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreUserService {

    private final CoreUserRepository coreUserRepository;
    private final CoreUserRoleRepository coreUserRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CoreUser createCoreUser(UserCreateRequest request) {
        if (coreUserRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.USER_LOGIN_ID_DUPLICATED);
        }
        // 다중 역할 처리: roles 우선, 없으면 role 사용
        java.util.List<String> roles = request.getRoles();
        String primaryRole;
        if (roles != null && !roles.isEmpty()) {
            primaryRole = roles.get(0);
        } else {
            primaryRole = request.getRole() != null && !request.getRole().isBlank() ? request.getRole() : "ROLE_USER";
            roles = java.util.List.of(primaryRole);
        }
        CoreUser user = CoreUser.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .userName(request.getUserName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(primaryRole)
                .enabled(true)
                .build();
        CoreUser saved = coreUserRepository.save(user);
        // core_user_role 테이블에 다중 역할 저장
        for (String r : roles) {
            coreUserRoleRepository.save(CoreUserRole.builder().userId(saved.getUserId()).role(r).build());
        }
        saved.setRoles(roles);
        log.info("사용자 생성 완료: loginId={}, roles={}", saved.getLoginId(), roles);
        return saved;
    }

    /**
     * 사용자 생성 (기본 - 하위호환)
     */
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        return UserResponse.from(createCoreUser(request));
    }

    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUserById(userId));
    }

    public Page<UserResponse> getUsers(Pageable pageable) {
        return coreUserRepository.findAll(pageable).map(u -> {
            u.setRoles(coreUserRoleRepository.findRolesByUserId(u.getUserId()));
            return UserResponse.from(u);
        });
    }

    public Page<CoreUser> getUserEntities(Pageable pageable) {
        return coreUserRepository.findAll(pageable).map(u -> {
            u.setRoles(coreUserRoleRepository.findRolesByUserId(u.getUserId()));
            return u;
        });
    }

    public CoreUser getUserEntity(Long userId) {
        CoreUser user = findUserById(userId);
        user.setRoles(coreUserRoleRepository.findRolesByUserId(userId));
        return user;
    }

    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        CoreUser user = findUserById(userId);
        user.updateProfile(request.getUserName(), request.getEmail(), request.getPhone());
        log.info("사용자 정보 수정 완료: userId={}", userId);
        return UserResponse.from(user);
    }

    @Transactional
    public CoreUser updateCoreUser(Long userId, UserUpdateRequest request) {
        CoreUser user = findUserById(userId);
        user.updateProfile(request.getUserName(), request.getEmail(), request.getPhone());
        log.info("사용자 정보 수정 완료: userId={}", userId);
        return user;
    }

    @Transactional
    public void disableUser(Long userId) {
        findUserById(userId).disable();
        log.info("사용자 비활성화: userId={}", userId);
    }

    @Transactional
    public void enableUser(Long userId) {
        findUserById(userId).enable();
        log.info("사용자 활성화: userId={}", userId);
    }

    @Transactional
    public UserResponse changeRole(Long userId, String role) {
        CoreUser user = findUserById(userId);
        user.changeRole(role);
        // core_user_role 테이블도 동기화 (단일 역할)
        coreUserRoleRepository.deleteByUserId(userId);
        coreUserRoleRepository.flush();
        coreUserRoleRepository.save(CoreUserRole.builder().userId(userId).role(role).build());
        user.setRoles(java.util.List.of(role));
        log.info("사용자 역할 변경: userId={}, role={}", userId, role);
        return UserResponse.from(user);
    }

    /**
     * 다중 역할 변경
     */
    @Transactional
    public UserResponse changeRoles(Long userId, java.util.List<String> roles) {
        CoreUser user = findUserById(userId);
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최소 1개 이상의 역할을 선택해야 합니다.");
        }
        // 대표 역할 설정 (core_user.ROLE)
        user.changeRole(roles.get(0));
        // core_user_role 테이블 갱신
        coreUserRoleRepository.deleteByUserId(userId);
        coreUserRoleRepository.flush();
        for (String r : roles) {
            coreUserRoleRepository.save(CoreUserRole.builder().userId(userId).role(r).build());
        }
        user.setRoles(roles);
        log.info("사용자 다중 역할 변경: userId={}, roles={}", userId, roles);
        return UserResponse.from(user);
    }

    private CoreUser findUserById(Long userId) {
        return coreUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("CoreUser", userId));
    }
}
