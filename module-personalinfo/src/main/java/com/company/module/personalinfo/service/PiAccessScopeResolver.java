package com.company.module.personalinfo.service;

import com.company.core.security.CustomUserDetails;
import com.company.core.user.profile.UserProfileSnapshot;
import com.company.core.user.profile.UserProfileSyncPort;
import com.company.module.personalinfo.entity.PiContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 로그인 사용자의 계약 조회 범위({@link PiAccessScope})를 정한다.
 *
 * <p>소속부서는 core 의 {@link UserProfileSyncPort}(구현: module-common 사용자 프로필)에서 읽는다.
 * core·module-common 코드는 바꾸지 않는다. 구현체가 없으면 비관리자는 볼 수 있는 계약이 없다(안전한 기본값).</p>
 *
 * <p>인증 정보가 없는 호출(만료 알림 스케줄러 등 시스템 작업)은 전체 범위로 본다.
 * 화면 API 는 모두 {@code @PreAuthorize} 로 로그인 사용자만 들어온다.</p>
 */
@Slf4j
@Component
public class PiAccessScopeResolver {

    private final ObjectProvider<UserProfileSyncPort> profilePort;

    public PiAccessScopeResolver(ObjectProvider<UserProfileSyncPort> profilePort) {
        this.profilePort = profilePort;
    }

    public PiAccessScope current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return PiAccessScope.ALL;
        }
        boolean admin = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(r -> PiRoles.PLATFORM_ADMIN.equals(r) || PiRoles.PI_ADMIN.equals(r));
        if (admin) {
            return PiAccessScope.ALL;
        }
        if (!(auth.getPrincipal() instanceof CustomUserDetails user) || user.getUserId() == null) {
            return PiAccessScope.dept(null, null);
        }
        UserProfileSyncPort port = profilePort.getIfAvailable();
        if (port == null) {
            log.warn("[personalinfo] 사용자 프로필(부서) 구현체가 없어 비관리자는 계약을 볼 수 없습니다. userId={}", user.getUserId());
            return PiAccessScope.dept(null, null);
        }
        Optional<UserProfileSnapshot> p;
        try {
            p = port.findProfile(user.getUserId());
        } catch (RuntimeException e) {
            log.warn("[personalinfo] 사용자 부서 조회 실패 userId={}: {}", user.getUserId(), e.getMessage());
            return PiAccessScope.dept(null, null);
        }
        return p.map(s -> PiAccessScope.dept(s.deptName(), s.deptCode())).orElseGet(() -> PiAccessScope.dept(null, null));
    }

    /** 현재 사용자가 이 계약을 볼 수 있는지 확인 (아니면 403) */
    public PiContract check(PiContract c) {
        current().check(c);
        return c;
    }
}
