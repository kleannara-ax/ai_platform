package com.company.module.user.service;

import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.profile.UserProfileSnapshot;
import com.company.core.user.profile.UserProfileSyncPort;
import com.company.module.code.entity.CodeDetail;
import com.company.module.code.repository.CodeDetailRepository;
import com.company.module.user.entity.UserProfile;
import com.company.module.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /api/core/users에서도 사용자 프로필 필드를 함께 저장/조회할 수 있도록 연결하는 구현체.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreUserProfileSyncService implements UserProfileSyncPort {

    private final UserProfileRepository userProfileRepository;
    private final CodeDetailRepository codeDetailRepository;

    @Override
    public Optional<UserProfileSnapshot> findProfile(Long userId) {
        return userProfileRepository.findByUserId(userId).map(this::toSnapshot);
    }

    @Override
    public Map<Long, UserProfileSnapshot> findProfiles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<String, String> deptNameMap = getDeptNameMap();
        return userProfileRepository.findAll().stream()
                .filter(profile -> ids.contains(profile.getUserId()))
                .collect(Collectors.toMap(
                        UserProfile::getUserId,
                        profile -> toSnapshot(profile, deptNameMap),
                        (a, b) -> a
                ));
    }

    @Override
    @Transactional
    public Optional<UserProfileSnapshot> saveProfile(Long userId, UserCreateRequest request) {
        if (userId == null || request == null || !hasProfileData(
                request.getDeptCode(), request.getPosition(), request.getJobTitle(),
                request.getEmployeeNo(), request.getJoinDate(), request.getOfficePhone(), request.getInternalExt())) {
            return Optional.empty();
        }

        UserProfile profile = userProfileRepository.findByUserId(userId).orElseGet(() -> UserProfile.builder()
                .userId(userId)
                .build());
        profile.updateProfile(trimToNull(request.getDeptCode()), trimToNull(request.getPosition()),
                trimToNull(request.getJobTitle()), trimToNull(request.getOfficePhone()), trimToNull(request.getInternalExt()));
        profile.updateExtended(trimToNull(request.getEmployeeNo()), request.getJoinDate());

        return Optional.of(toSnapshot(userProfileRepository.save(profile)));
    }

    @Override
    @Transactional
    public Optional<UserProfileSnapshot> saveProfile(Long userId, UserUpdateRequest request) {
        if (userId == null || request == null) return Optional.empty();
        Optional<UserProfile> existing = userProfileRepository.findByUserId(userId);
        if (existing.isEmpty() && !hasProfileData(
                request.getDeptCode(), request.getPosition(), request.getJobTitle(),
                request.getEmployeeNo(), request.getJoinDate(), request.getOfficePhone(), request.getInternalExt())) {
            return Optional.empty();
        }

        UserProfile profile = existing.orElseGet(() -> UserProfile.builder()
                .userId(userId)
                .build());
        profile.updateProfile(trimToNull(request.getDeptCode()), trimToNull(request.getPosition()),
                trimToNull(request.getJobTitle()), trimToNull(request.getOfficePhone()), trimToNull(request.getInternalExt()));
        profile.updateExtended(trimToNull(request.getEmployeeNo()), request.getJoinDate());

        return Optional.of(toSnapshot(userProfileRepository.save(profile)));
    }

    private UserProfileSnapshot toSnapshot(UserProfile profile) {
        return toSnapshot(profile, getDeptNameMap());
    }

    private UserProfileSnapshot toSnapshot(UserProfile profile, Map<String, String> deptNameMap) {
        if (profile == null) return null;
        String deptCode = profile.getDeptCode();
        return new UserProfileSnapshot(
                deptCode,
                deptCode == null || deptCode.isBlank() ? null : deptNameMap.getOrDefault(deptCode, deptCode),
                profile.getPosition(),
                profile.getJobTitle(),
                profile.getEmployeeNo(),
                profile.getJoinDate(),
                profile.getOfficePhone(),
                profile.getInternalExt()
        );
    }

    private Map<String, String> getDeptNameMap() {
        return codeDetailRepository.findByGroup_GroupCodeAndIsActiveTrueOrderBySortOrderAsc("DEPT").stream()
                .collect(Collectors.toMap(CodeDetail::getCode, CodeDetail::getCodeName, (a, b) -> a));
    }

    private boolean hasProfileData(Object... fields) {
        for (Object field : fields) {
            if (field == null) continue;
            if (field instanceof String text && text.isBlank()) continue;
            return true;
        }
        return false;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
