package com.company.module.fire.facility;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class FacilityPermissionService {

    private static final String OTHER_PERMISSION_GROUP = "OTHER_PERM";
    private static final String LEGACY_OTHER_ADMIN_CODE = "OTHER_ADMIN";

    private final JdbcTemplate jdbcTemplate;

    public boolean hasOtherFacilityAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String loginId = authentication.getName();
        if (loginId == null || loginId.isBlank()) {
            return false;
        }
        Integer matchedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                        "FROM code_detail d " +
                        "JOIN code_group g ON g.GROUP_ID = d.GROUP_ID " +
                        "WHERE g.GROUP_CODE = ? " +
                        "AND LOWER(d.CODE) = LOWER(?) " +
                        "AND d.CODE <> ? " +
                        "AND COALESCE(g.IS_ACTIVE, 1) = 1 " +
                        "AND COALESCE(d.IS_ACTIVE, 1) = 1",
                Integer.class,
                OTHER_PERMISSION_GROUP,
                loginId.trim(),
                LEGACY_OTHER_ADMIN_CODE
        );
        return matchedCount != null && matchedCount > 0;
    }
}
