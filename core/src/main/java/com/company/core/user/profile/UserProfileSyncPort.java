package com.company.core.user.profile;

import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserUpdateRequest;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Core 사용자 API에서 프로필 정보를 저장/조회하기 위한 포트.
 * core 모듈은 module-common에 직접 의존하지 않고, 구현체는 module-common에서 제공한다.
 */
public interface UserProfileSyncPort {

    Optional<UserProfileSnapshot> findProfile(Long userId);

    Map<Long, UserProfileSnapshot> findProfiles(Collection<Long> userIds);

    Optional<UserProfileSnapshot> saveProfile(Long userId, UserCreateRequest request);

    Optional<UserProfileSnapshot> saveProfile(Long userId, UserUpdateRequest request);
}
