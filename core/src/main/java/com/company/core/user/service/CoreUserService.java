package com.company.core.user.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserResponse;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.entity.CoreUser;
import com.company.core.user.entity.Role;
import com.company.core.user.repository.CoreUserRepository;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CoreUser createCoreUser(UserCreateRequest request) {
        if (coreUserRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.USER_LOGIN_ID_DUPLICATED);
        }
        CoreUser user = CoreUser.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .userName(request.getUserName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(request.getRole() != null ? request.getRole() : Role.ROLE_USER)
                .enabled(true)
                .build();
        CoreUser saved = coreUserRepository.save(user);
        log.info("사용자 생성 완료: loginId={}", saved.getLoginId());
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
        return coreUserRepository.findAll(pageable).map(UserResponse::from);
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
    public UserResponse changeRole(Long userId, Role role) {
        CoreUser user = findUserById(userId);
        user.changeRole(role);
        log.info("사용자 역할 변경: userId={}, role={}", userId, role);
        return UserResponse.from(user);
    }

    private CoreUser findUserById(Long userId) {
        return coreUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("CoreUser", userId));
    }
}
