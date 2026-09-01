package com.company.module.kims.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * KIMS 권한 판정.
 *
 * <p>KIMS 관리자는 <b>공통코드 그룹 {@code KIMS_PERM}</b> 에 등록된 로그인 ID 로 정한다.
 * (소방 {@code FIRE_PERM}, PS점검 {@code PS_INSP_AUTH} 과 같은 방식 —
 *  공통코드 관리 화면에서 사람만 추가/제거하면 된다.)
 *
 * <p>추가로 <b>공통코드 그룹 {@code KIMS_PERM_SEOUL}</b> 에 등록된 로그인 ID 는
 * PC 관리(IP 관리)에서 <b>서울</b> 사업장 데이터만 조회/수정할 수 있다(청주는 완전히 차단).
 * KIMS_PERM 관리자·플랫폼 ROLE_MANAGER 는 이 제한과 무관하게 항상 전체(청주+서울)를 다룬다.
 *
 * <p>컨트롤러에서 {@code @PreAuthorize("@kimsPerm.isAdmin(authentication)")} 형태로 쓴다.
 *
 * <ul>
 *   <li>{@link #isAdmin} — KIMS_PERM 에 등록된 로그인 ID 만. 플랫폼 역할은 보지 않는다.</li>
 *   <li>{@link #canWork} — 위 관리자 + 플랫폼 ROLE_MANAGER + KIMS_PERM_SEOUL 서울 전용 사용자
 *       (요청 처리·내역 입력. 실제 대상 사업장 제한은 {@link #allowedSite}/{@link #canWorkOnSite} 로 서비스 계층에서 건다)</li>
 *   <li>{@link #allowedSite} — 이 사용자가 다룰 수 있는 사업장. {@code null} = 전체(제한없음), {@code "서울"} = 서울만</li>
 *   <li>{@link #canWorkOnSite} — 대상 레코드/요청의 사업장에 대해 실제로 작업 가능한지</li>
 * </ul>
 *
 * <p>목록은 짧게 캐시한다(공통코드에서 사람을 바꾸면 최대 {@value #CACHE_MILLIS}ms 뒤 반영).
 */
@Slf4j
@Component("kimsPerm")
public class KimsPermission {

    private static final String GROUP_CODE = "KIMS_PERM";
    private static final String SEOUL_GROUP_CODE = "KIMS_PERM_SEOUL";
    private static final String SEOUL_SITE = "서울";
    private static final long CACHE_MILLIS = 30_000L;

    private static final String SQL = """
            SELECT d.CODE
              FROM code_detail d
              JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
             WHERE g.GROUP_CODE = ?
               AND g.IS_ACTIVE = 1
               AND d.IS_ACTIVE = 1
            """;

    private final JdbcTemplate jdbcTemplate;

    private volatile Set<String> cachedIds = Collections.emptySet();
    private volatile long cachedAt = 0L;

    private volatile Set<String> cachedSeoulIds = Collections.emptySet();
    private volatile long cachedSeoulAt = 0L;

    public KimsPermission(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 공통코드에 등록된 KIMS 관리자 로그인 ID (소문자) */
    public Set<String> adminLoginIds() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MILLIS) return cachedIds;
        try {
            List<String> codes = jdbcTemplate.queryForList(SQL, String.class, GROUP_CODE);
            cachedIds = codes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> code.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            cachedAt = now;
        } catch (Exception exception) {
            // 공통코드 조회 실패 시 권한을 넓히지 않는다(빈 목록 = 관리자 없음).
            log.warn("KIMS_PERM 공통코드 조회 실패 — 관리자 없음으로 처리합니다: {}", exception.getMessage());
            cachedIds = Collections.emptySet();
            cachedAt = now;
        }
        return cachedIds;
    }

    /** 공통코드(KIMS_PERM_SEOUL)에 등록된 서울 전용 로그인 ID (소문자) */
    public Set<String> seoulLoginIds() {
        long now = System.currentTimeMillis();
        if (now - cachedSeoulAt < CACHE_MILLIS) return cachedSeoulIds;
        try {
            List<String> codes = jdbcTemplate.queryForList(SQL, String.class, SEOUL_GROUP_CODE);
            cachedSeoulIds = codes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> code.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            cachedSeoulAt = now;
        } catch (Exception exception) {
            // 공통코드 조회 실패 시 권한을 넓히지 않는다(빈 목록 = 서울 전용 사용자 없음).
            log.warn("KIMS_PERM_SEOUL 공통코드 조회 실패 — 서울 전용 사용자 없음으로 처리합니다: {}", exception.getMessage());
            cachedSeoulIds = Collections.emptySet();
            cachedSeoulAt = now;
        }
        return cachedSeoulIds;
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String name = authentication.getName();
        return name != null && adminLoginIds().contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /** KIMS_PERM_SEOUL 에 등록된 서울 전용 사용자인지 (관리자/매니저 여부와는 무관하게 명단만 본다) */
    public boolean isSeoulUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String name = authentication.getName();
        return name != null && seoulLoginIds().contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /** 요청 처리·내역 입력 권한 (관리자 + 플랫폼 매니저 + 서울 전용 사용자). 대상 사업장 제한은 별도로 건다. */
    public boolean canWork(Authentication authentication) {
        return isAdmin(authentication) || hasRole(authentication, "ROLE_MANAGER") || isSeoulUser(authentication);
    }

    /**
     * 이 사용자가 다룰 수 있는 사업장.
     * @return {@code null} = 전체(제한없음, 관리자/매니저/일반 사용자 — 기존 동작), {@code "서울"} = 서울만(서울 전용 사용자)
     */
    public String allowedSite(Authentication authentication) {
        if (isAdmin(authentication) || hasRole(authentication, "ROLE_MANAGER")) {
            return null;
        }
        if (isSeoulUser(authentication)) {
            return SEOUL_SITE;
        }
        return null;
    }

    /** 대상 사업장(targetSite)에 대해 실제로 작업(등록/조회/수정)이 가능한지. */
    public boolean canWorkOnSite(Authentication authentication, String targetSite) {
        String allowed = allowedSite(authentication);
        return allowed == null || allowed.equals(targetSite);
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) return true;
        }
        return false;
    }
}
