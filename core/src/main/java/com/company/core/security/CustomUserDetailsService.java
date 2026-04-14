package com.company.core.security;

import com.company.core.user.entity.CoreUser;
import com.company.core.user.repository.CoreUserRepository;
import com.company.core.user.repository.CoreUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security UserDetailsService 구현체
 * 로그인 ID 기반 사용자 조회
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final CoreUserRepository coreUserRepository;
    private final CoreUserRoleRepository coreUserRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        CoreUser user = coreUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "사용자를 찾을 수 없습니다: " + loginId));

        // 다중 역할 로드 (core_user_role 테이블)
        java.util.List<String> roles = coreUserRoleRepository.findRolesByUserId(user.getUserId());
        if (roles != null && !roles.isEmpty()) {
            user.setRoles(roles);
        }

        return new CustomUserDetails(user);
    }
}
