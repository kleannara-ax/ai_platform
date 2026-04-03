package com.company.module.user.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.user.dto.UserProfileRequest;
import com.company.module.user.dto.UserProfileResponse;
import com.company.module.user.entity.UserProfile;
import com.company.module.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 사용자 프로필 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    /**
     * 프로필 생성
     */
    @Transactional
    public UserProfileResponse createProfile(UserProfileRequest request) {
        if (userProfileRepository.existsByUserId(request.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "이미 프로필이 존재하는 사용자입니다: userId=" + request.getUserId());
        }

        UserProfile profile = UserProfile.builder()
                .userId(request.getUserId())
                .deptId(request.getDeptId())
                .position(request.getPosition())
                .jobTitle(request.getJobTitle())
                .employeeNo(request.getEmployeeNo())
                .joinDate(request.getJoinDate())
                .officePhone(request.getOfficePhone())
                .internalExt(request.getInternalExt())
                .build();

        UserProfile saved = userProfileRepository.save(profile);
        log.info("사용자 프로필 생성 완료: userId={}", saved.getUserId());

        return UserProfileResponse.from(saved);
    }

    /**
     * 사용자 ID로 프로필 조회
     */
    public UserProfileResponse getProfileByUserId(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("UserProfile", userId));
        return UserProfileResponse.from(profile);
    }

    /**
     * 부서별 프로필 목록 조회
     */
    public List<UserProfileResponse> getProfilesByDeptId(Long deptId) {
        return userProfileRepository.findByDeptId(deptId)
                .stream()
                .map(UserProfileResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 프로필 수정
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("UserProfile", userId));

        profile.updateProfile(
                request.getDeptId(),
                request.getPosition(),
                request.getJobTitle(),
                request.getOfficePhone(),
                request.getInternalExt()
        );

        log.info("사용자 프로필 수정 완료: userId={}", userId);
        return UserProfileResponse.from(profile);
    }
}
