package com.company.module.fire.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 점검자 정보 조회 - core_user 테이블 참조
 *
 * <p>컬럼 매핑:
 * <ul>
 *   <li>LOGIN_ID  → Spring Security principal.getName()</li>
 *   <li>USER_NAME → 화면에 표시할 사용자 이름</li>
 *   <li>USER_ID   → 사용자 PK</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class InspectorNameResolver {

    private final EntityManager entityManager;

    public String resolveDisplayName(String loginId) {
        String normalized = normalize(loginId);
        if (normalized.isEmpty()) {
            return "";
        }

        try {
            Object result = entityManager.createNativeQuery("""
                            SELECT COALESCE(NULLIF(TRIM(USER_NAME), ''), LOGIN_ID)
                            FROM core_user
                            WHERE LOGIN_ID = :loginId
                            """)
                    .setParameter("loginId", normalized)
                    .getSingleResult();
            return result == null ? normalized : result.toString().trim();
        } catch (NoResultException ex) {
            return normalized;
        }
    }

    public Long resolveUserId(String loginId) {
        String normalized = normalize(loginId);
        if (normalized.isEmpty()) {
            return null;
        }

        try {
            Object result = entityManager.createNativeQuery("""
                            SELECT USER_ID
                            FROM core_user
                            WHERE LOGIN_ID = :loginId
                            """)
                    .setParameter("loginId", normalized)
                    .getSingleResult();
            if (result instanceof Number number) {
                return number.longValue();
            }
            return result == null ? null : Long.parseLong(result.toString());
        } catch (NoResultException ex) {
            return null;
        }
    }

    private String normalize(String loginId) {
        return loginId == null ? "" : loginId.trim();
    }
}
