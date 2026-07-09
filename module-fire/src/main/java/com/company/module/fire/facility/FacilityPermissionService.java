package com.company.module.fire.facility;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FacilityPermissionService {

    private static final String OTHER_PERMISSION_GROUP = "OTHER_PERM";
    private static final String OTHER_ADMIN_CODE = "OTHER_ADMIN";

    private final JdbcTemplate jdbcTemplate;

    public boolean hasOtherFacilityAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String loginId = authentication.getName();
        if (loginId == null || loginId.isBlank()) {
            return false;
        }
        String extraValue1 = findExtraValue1(OTHER_PERMISSION_GROUP, OTHER_ADMIN_CODE);
        if (extraValue1 == null || extraValue1.isBlank()) {
            return false;
        }
        return Arrays.stream(extraValue1.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .anyMatch(loginId::equals);
    }

    private String findExtraValue1(String groupCode, String code) {
        List<String> values = jdbcTemplate.query(
                "SELECT d.EXTRA_VALUE1 " +
                        "FROM code_detail d " +
                        "JOIN code_group g ON g.GROUP_ID = d.GROUP_ID " +
                        "WHERE g.GROUP_CODE = ? AND d.CODE = ? " +
                        "AND COALESCE(g.IS_ACTIVE, 1) = 1 AND COALESCE(d.IS_ACTIVE, 1) = 1 " +
                        "ORDER BY d.SORT_ORDER ASC LIMIT 1",
                (rs, rowNum) -> rs.getString(1),
                groupCode,
                code
        );
        return values.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }
}
