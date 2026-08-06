package com.company.core.security;

import com.company.core.user.entity.CoreUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails 구현체
 * 다중 역할(Multi-Role) 지원: 사용자가 보유한 모든 역할이 GrantedAuthority로 부여된다.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String loginId;
    private final String password;
    private final String userName;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 다중 역할 목록으로 인증 객체 생성 (권장)
     * @param roles 사용자가 보유한 전체 역할 목록 (core_user_role 기준)
     */
    public CustomUserDetails(CoreUser user, List<String> roles) {
        this.userId = user.getUserId();
        this.loginId = user.getLoginId();
        this.password = user.getPassword();
        this.userName = user.getUserName();
        this.enabled = user.getEnabled();
        List<String> effectiveRoles = (roles == null || roles.isEmpty())
                ? List.of(user.getRole())
                : roles;
        this.authorities = effectiveRoles.stream()
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableList());
    }

    /** 단일 역할(core_user.role)만 사용하는 하위호환 생성자 */
    public CustomUserDetails(CoreUser user) {
        this(user, null);
    }

    /** UserDetails 인터페이스 구현 - Spring Security 인증 시 사용 */
    @Override
    public String getUsername() {
        return this.loginId;
    }

    /** 사용자 이름(실명) 반환 - getUsername()과 구분 */
    public String getUserDisplayName() {
        return this.userName;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
