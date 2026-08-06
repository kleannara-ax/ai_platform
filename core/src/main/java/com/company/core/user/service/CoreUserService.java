package com.company.core.user.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserResponse;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.entity.CoreUser;
import com.company.core.user.profile.UserProfileSnapshot;
import com.company.core.user.profile.UserProfileSyncPort;
import com.company.core.user.repository.CoreUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 사용자 관리 서비스 (Core)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreUserService {

    private final CoreUserRepository coreUserRepository;
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
        return UserResponse.from(user, profile);
    }

    public Page<UserResponse> getUsers(Pageable pageable) {
        Page<CoreUser> users = coreUserRepository.findAll(pageable);
        Map<Long, UserProfileSnapshot> profileMap = userProfileSyncPort
                .map(port -> port.findProfiles(users.getContent().stream().map(CoreUser::getUserId).toList()))
                .orElse(Map.of());
        return users.map(user -> UserResponse.from(user, profileMap.get(user.getUserId())));
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

    @Transactional
    public UserResponse changeRole(Long userId, String role) {
        CoreUser user = findUserById(userId);
        user.changeRole(role);
        log.info("사용자 역할 변경: userId={}, role={}", userId, role);
        return UserResponse.from(user);
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
