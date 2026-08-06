package com.company.core.security;

import com.company.core.user.entity.CoreUser;
import com.company.core.user.entity.CoreUserRole;
import com.company.core.user.repository.CoreUserRepository;
import com.company.core.user.repository.CoreUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetailsService 구현체
 * 로그인 ID 기반 사용자 조회
 *
 * <p>JWT는 역할 정보를 담지 않으며(loginId만 subject로 저장), 매 요청마다
 * DB에서 사용자의 최신 역할 목록(core_user_role, 다중 역할)을 다시 조회하여
 * GrantedAuthority를 구성한다. 따라서 역할 변경은 재로그인 없이 즉시 반영된다.
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

        List<String> roles = coreUserRoleRepository.findByUserId(user.getUserId()).stream()
                .map(CoreUserRole::getRole)
                .distinct()
                .collect(Collectors.toList());

        return new CustomUserDetails(user, roles);
    }
}
