package com.company.core.user.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserResponse;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.entity.CoreUser;
import com.company.core.user.entity.CoreUserRole;
import com.company.core.user.profile.UserProfileSnapshot;
import com.company.core.user.profile.UserProfileSyncPort;
import com.company.core.user.repository.CoreUserRepository;
import com.company.core.user.repository.CoreUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final Optional<UserProfileSyncPort> userProfileSyncPort;

    @Transactional
    public CoreUser createCoreUser(UserCreateRequest request) {
        if (coreUserRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.USER_LOGIN_ID_DUPLICATED);
        }
        // 신규 사용자는 항상 ROLE_USER(사용자) 역할로 생성
        // 접근 권한 관리에서 ROLE_USER에 설정된 메뉴 권한이 적용됨
        String role = "ROLE_USER";
        CoreUser user = CoreUser.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .userName(request.getUserName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(role)
                .enabled(true)
                .build();
        CoreUser saved = coreUserRepository.save(user);
        coreUserRoleRepository.save(CoreUserRole.builder().userId(saved.getUserId()).role(role).build());
        log.info("사용자 생성 완료: loginId={}, role={}", saved.getLoginId(), role);
        return saved;
    }

    /**
     * 사용자 생성 (기본 - 하위호환)
     */
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        CoreUser saved = createCoreUser(request);
        UserProfileSnapshot profile = userProfileSyncPort
                .flatMap(port -> port.saveProfile(saved.getUserId(), request))
                .orElse(null);
        return UserResponse.from(saved, profile);
    }

    public UserResponse getUser(Long userId) {
        CoreUser user = findUserById(userId);
        UserProfileSnapshot profile = userProfileSyncPort
                .flatMap(port -> port.findProfile(userId))
                .orElse(null);
        List<String> roles = getRolesByUserId(userId);
        return UserResponse.from(user, profile, roles);
    }

    public Page<UserResponse> getUsers(Pageable pageable) {
        Page<CoreUser> users = coreUserRepository.findAll(pageable);
        List<Long> userIds = users.getContent().stream().map(CoreUser::getUserId).toList();
        Map<Long, UserProfileSnapshot> profileMap = userProfileSyncPort
                .map(port -> port.findProfiles(userIds))
                .orElse(Map.of());
        Map<Long, List<String>> roleMap = coreUserRoleRepository.findRoleCodesByUserIds(userIds);
        return users.map(user -> UserResponse.from(
                user, profileMap.get(user.getUserId()), roleMap.get(user.getUserId())));
    }

    /**
     * 사용자의 전체 역할 목록 조회 (다중 역할)
     * core_user_role에 데이터가 없으면(레거시) core_user.role 단일값으로 대체한다.
     */
    public List<String> getRolesByUserId(Long userId) {
        List<String> roles = coreUserRoleRepository.findByUserId(userId).stream()
                .map(CoreUserRole::getRole)
                .distinct()
                .collect(Collectors.toList());
        if (!roles.isEmpty()) {
            return roles;
        }
        CoreUser user = coreUserRepository.findById(userId).orElse(null);
        return (user != null && user.getRole() != null) ? List.of(user.getRole()) : List.of();
    }

    /**
     * 여러 사용자의 역할 목록을 일괄 조회 (userId → List<role>)
     * core_user_role에 매핑이 없는(레거시) 사용자는 core_user.role 단일값으로 대체한다.
     */
    public Map<Long, List<String>> getRolesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        Map<Long, List<String>> roleMap = new java.util.HashMap<>(
                coreUserRoleRepository.findRoleCodesByUserIds(userIds));
        List<Long> missing = userIds.stream()
                .filter(id -> !roleMap.containsKey(id) || roleMap.get(id).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            coreUserRepository.findAllById(missing).forEach(u -> {
                if (u.getRole() != null) {
                    roleMap.put(u.getUserId(), List.of(u.getRole()));
                }
            });
        }
        return roleMap;
    }

    public Page<CoreUser> getUserEntities(Pageable pageable) {
        return coreUserRepository.findAll(pageable);
    }

    public CoreUser getUserEntity(Long userId) {
        return findUserById(userId);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        CoreUser user = findUserById(userId);
        user.updateProfile(request.getUserName(), request.getEmail(), request.getPhone());
        applyPasswordChangeIfPresent(user, request.getPassword());
        UserProfileSnapshot profile = userProfileSyncPort
                .flatMap(port -> port.saveProfile(userId, request))
                .or(() -> userProfileSyncPort.flatMap(port -> port.findProfile(userId)))
                .orElse(null);
        log.info("사용자 정보 수정 완료: userId={}", userId);
        return UserResponse.from(user, profile);
    }

    @Transactional
    public CoreUser updateCoreUser(Long userId, UserUpdateRequest request) {
        CoreUser user = findUserById(userId);
        user.updateProfile(request.getUserName(), request.getEmail(), request.getPhone());
        applyPasswordChangeIfPresent(user, request.getPassword());
        log.info("사용자 정보 수정 완료: userId={}", userId);
        return user;
    }

    /**
     * 비밀번호 변경 요청이 있는 경우에만 인코딩하여 반영한다.
     * request.getPassword()가 null이면(입력하지 않은 경우) 기존 비밀번호를 그대로 유지한다.
     */
    private void applyPasswordChangeIfPresent(CoreUser user, String rawPassword) {
        if (rawPassword == null) {
            return;
        }
        user.changePassword(passwordEncoder.encode(rawPassword));
        log.info("사용자 비밀번호 변경 완료: userId={}", user.getUserId());
    }

    @Transactional
    public void disableUser(Long userId) {
        findUserById(userId).disable();
        log.info("사용자 비활성화: userId={}", userId);
    }

    @Transactional
    public void disableUserByLoginId(String loginId) {
        CoreUser user = findUserByLoginId(loginId);
        user.disable();
        log.info("사용자 비활성화: loginId={}", loginId);
    }

    @Transactional
    public void enableUser(Long userId) {
        findUserById(userId).enable();
        log.info("사용자 활성화: userId={}", userId);
    }

    @Transactional
    public void enableUserByLoginId(String loginId) {
        CoreUser user = findUserByLoginId(loginId);
        user.enable();
        log.info("사용자 활성화: loginId={}", loginId);
    }

    /**
     * 사용자 역할 변경 (단일 역할 - 하위호환용)
     * 내부적으로 changeRoles(userId, List.of(role))를 호출한다.
     */
    @Transactional
    public UserResponse changeRole(Long userId, String role) {
        return changeRoles(userId, role == null ? List.of() : List.of(role));
    }

    /**
     * 사용자 역할 변경 (다중 역할)
     * core_user_role 매핑 테이블을 전체 교체하고,
     * 하위호환을 위해 core_user.role에는 대표 역할(첫번째 역할)을 동기화한다.
     */
    @Transactional
    public UserResponse changeRoles(Long userId, List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최소 1개 이상의 역할을 선택해야 합니다.");
        }
        CoreUser user = findUserById(userId);

        // 중복 제거, 순서 유지
        Set<String> uniqueRoles = new LinkedHashSet<>(roles);

        coreUserRoleRepository.deleteByUserId(userId);
        coreUserRoleRepository.flush();
        for (String role : uniqueRoles) {
            coreUserRoleRepository.save(CoreUserRole.builder().userId(userId).role(role).build());
        }

        // 레거시 core_user.role 컬럼은 대표 역할(첫번째)로 동기화
        String primaryRole = uniqueRoles.iterator().next();
        user.changeRole(primaryRole);

        log.info("사용자 역할 변경: userId={}, roles={}", userId, uniqueRoles);
        return UserResponse.from(user, null, List.copyOf(uniqueRoles));
    }

    private CoreUser findUserById(Long userId) {
        return coreUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("CoreUser", userId));
    }

    private CoreUser findUserByLoginId(String loginId) {
        String normalizedLoginId = loginId == null ? "" : loginId.trim();
        if (normalizedLoginId.isBlank()) {
            throw new EntityNotFoundException("CoreUser", loginId);
        }
        return coreUserRepository.findByLoginId(normalizedLoginId)
                .orElseThrow(() -> new EntityNotFoundException("CoreUser", normalizedLoginId));
    }
}
