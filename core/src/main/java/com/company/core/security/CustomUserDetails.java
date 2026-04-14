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
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String loginId;
    private final String password;
    private final String userName;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(CoreUser user) {
        this.userId = user.getUserId();
        this.loginId = user.getLoginId();
        this.password = user.getPassword();
        this.userName = user.getUserName();
        this.enabled = user.getEnabled();
        // 다중 역할 지원: core_user_role 테이블의 모든 역할을 GrantedAuthority로 변환
        List<String> roles = user.getRoles();
        if (roles != null && !roles.isEmpty()) {
            this.authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toUnmodifiableList());
        } else {
            this.authorities = List.of(new SimpleGrantedAuthority(user.getRole()));
        }
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
