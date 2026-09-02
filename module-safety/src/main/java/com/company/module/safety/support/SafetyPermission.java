package com.company.module.safety.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SAFETY(안전작업 매뉴얼) 모듈 관리자 권한 판정.
 *
 * <p>관리자는 <b>공통코드 그룹 {@code SAFETY_PERM}</b> 에 등록된 로그인 ID <b>만</b> 이다.
 * (소방 {@code FIRE_PERM}, KIMS {@code KIMS_PERM} 과 같은 방식 —
 *  공통코드 관리 화면에서 사람만 추가/제거하면 된다.)
 *
 * <p>컨트롤러에서 {@code @PreAuthorize("@safetyPerm.isAdmin(authentication)")} 형태로 쓴다.
 *
 * <p>목록은 짧게 캐시한다(공통코드에서 사람을 바꾸면 최대 {@value #CACHE_MILLIS}ms 뒤 반영).
 */
@Slf4j
@Component("safetyPerm")
public class SafetyPermission {

    private static final String GROUP_CODE = "SAFETY_PERM";
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

    public SafetyPermission(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 공통코드에 등록된 SAFETY 관리자 로그인 ID (소문자) */
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
            log.warn("SAFETY_PERM 공통코드 조회 실패 — 관리자 없음으로 처리합니다: {}", exception.getMessage());
            cachedIds = Collections.emptySet();
            cachedAt = now;
        }
        return cachedIds;
    }

    /**
     * 공통코드 {@code SAFETY_PERM} 명단에 있는 계정만 관리자로 본다.
     * <p>플랫폼 {@code ROLE_ADMIN} 이라도 이 명단에 없으면 관리자가 아니다 — 관리 권한은
     * 공통코드 한 곳에서만 관리한다.
     */
    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String name = authentication.getName();
        return name != null && adminLoginIds().contains(name.trim().toLowerCase(Locale.ROOT));
    }
}
