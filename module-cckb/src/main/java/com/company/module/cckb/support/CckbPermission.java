package com.company.module.cckb.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * CCKB 후면 색상검사 모듈 관리자 권한 판정.
 * <p>module-safety 의 {@code SafetyPermission} 과 동일한 패턴: 공통코드 그룹
 * {@code CCKB_PERM} 에 등록된 로그인 ID 목록을 30초 캐시로 조회해 관리자 여부를 판정한다.
 * {@code @Component("cckbPerm")} 으로 등록해 {@code @PreAuthorize("@cckbPerm.isAdmin(authentication)")}
 * 형태로 컨트롤러에서 사용한다.
 *
 * <p>"⚙ 기준값 설정" 탭(원본 V4 HTML 의 isAuthorized() 스텁)의 실제 서버측 권한 검증을
 * 이 컴포넌트가 담당한다 — 기준값 수정(PUT)·기준값 이력 조회 등 관리 기능에 적용.
 */
@Slf4j
@Component("cckbPerm")
public class CckbPermission {

    private static final String GROUP_CODE = "CCKB_PERM";
    private static final long CACHE_MILLIS = 30_000L;

    private static final String SQL = """
            SELECT d.CODE FROM code_detail d
            JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
            WHERE g.GROUP_CODE = ? AND g.IS_ACTIVE = 1 AND d.IS_ACTIVE = 1""";

    private final JdbcTemplate jdbcTemplate;

    private volatile Set<String> cachedIds = Collections.emptySet();
    private volatile long cachedAt = 0L;

    public CckbPermission(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** CCKB_PERM 공통코드 그룹에 등록된 관리자 로그인 ID 목록 (소문자/trim, 30초 캐시) */
    public Set<String> adminLoginIds() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MILLIS && !cachedIds.isEmpty()) {
            return cachedIds;
        }
        try {
            Set<String> ids = new HashSet<>();
            for (String code : jdbcTemplate.queryForList(SQL, String.class, GROUP_CODE)) {
                if (code != null && !code.isBlank()) {
                    ids.add(code.trim().toLowerCase(Locale.ROOT));
                }
            }
            cachedIds = ids;
            cachedAt = now;
            return ids;
        } catch (Exception ex) {
            log.warn("CCKB_PERM 관리자 목록 조회 실패 — 빈 목록으로 처리", ex);
            return Collections.emptySet();
        }
    }

    /** 현재 인증 주체가 CCKB_PERM 그룹에 등록된 관리자인지 */
    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return false;
        }
        return adminLoginIds().contains(name.trim().toLowerCase(Locale.ROOT));
    }
}
