package com.company.module.fire.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InspectorNameResolver {

    private final EntityManager entityManager;

    public String resolveDisplayName(String username) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return "";
        }

        try {
            Object result = entityManager.createNativeQuery("""
                            SELECT COALESCE(NULLIF(TRIM(display_name), ''), username)
                            FROM web_user
                            WHERE username = :username
                            """)
                    .setParameter("username", normalized)
                    .getSingleResult();
            return result == null ? normalized : result.toString().trim();
        } catch (NoResultException ex) {
            return normalized;
        }
    }

    public Long resolveUserId(String username) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return null;
        }

        try {
            Object result = entityManager.createNativeQuery("""
                            SELECT user_id
                            FROM web_user
                            WHERE username = :username
                            """)
                    .setParameter("username", normalized)
                    .getSingleResult();
            if (result instanceof Number number) {
                return number.longValue();
            }
            return result == null ? null : Long.parseLong(result.toString());
        } catch (NoResultException ex) {
            return null;
        }
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim();
    }
}
