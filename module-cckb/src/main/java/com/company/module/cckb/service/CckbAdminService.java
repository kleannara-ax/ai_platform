package com.company.module.cckb.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.cckb.dto.request.CckbAdminAddRequest;
import com.company.module.cckb.dto.response.CckbAdminResponse;
import com.company.module.cckb.dto.response.CckbUserOptionResponse;
import com.company.module.cckb.support.CckbPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * CCKB_PERM(공통코드) 관리자 명단을 관리하는 서비스.
 *
 * <p>목록 조회는 누구나(인증된 사용자) 가능하지만, 추가는 반드시
 * {@link CckbPermission#isAdmin(org.springframework.security.core.Authentication)} 검증을
 * 컨트롤러에서 통과한 뒤에만 호출되어야 한다 — 플랫폼 ROLE_ADMIN 이거나 이미 CCKB_PERM 명단에
 * 등록된 사람만 새 관리자를 추가할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CckbAdminService {

    private static final String GROUP_CODE = "CCKB_PERM";
    private static final String GROUP_NAME = "CCKB 색상검사 관리자";
    private static final String GROUP_DESCRIPTION = "CCKB 후면 색상검사(CCKB) 기준값 설정 관리자 권한을 가진 로그인 ID 목록";

    private final JdbcTemplate jdbcTemplate;
    private final CckbPermission cckbPermission;

    /** CCKB_PERM 그룹의 GROUP_ID 를 가져온다. 없으면 생성한다(배포 환경에서 03_perm_code.sql 이 선행되지만 방어적으로 처리). */
    private Long ensureGroupId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT GROUP_ID FROM code_group WHERE GROUP_CODE = ?", Long.class, GROUP_CODE);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbcTemplate.update(
                "INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER) " +
                        "VALUES (?, ?, ?, 1, 140)",
                GROUP_CODE, GROUP_NAME, GROUP_DESCRIPTION);
        return jdbcTemplate.queryForObject(
                "SELECT GROUP_ID FROM code_group WHERE GROUP_CODE = ?", Long.class, GROUP_CODE);
    }

    /** 현재 CCKB_PERM 명단(활성/비활성 모두) 조회 */
    public List<CckbAdminResponse> listAdmins() {
        return jdbcTemplate.query(
                "SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE " +
                        "FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID " +
                        "WHERE g.GROUP_CODE = ? ORDER BY d.SORT_ORDER, d.CODE_ID",
                (rs, rowNum) -> CckbAdminResponse.builder()
                        .loginId(rs.getString("CODE"))
                        .name(rs.getString("CODE_NAME"))
                        .active(rs.getBoolean("IS_ACTIVE"))
                        .build(),
                GROUP_CODE);
    }

    /** 새 관리자 추가 (이미 있으면 활성화 + 이름 갱신) */
    @Transactional
    public List<CckbAdminResponse> addAdmin(CckbAdminAddRequest request) {
        String loginId = request.getLoginId() == null ? "" : request.getLoginId().trim();
        if (loginId.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "로그인 ID를 입력해 주세요.");
        }
        String name = (request.getName() == null || request.getName().isBlank())
                ? loginId : request.getName().trim();

        Long groupId = ensureGroupId();
        int updated = jdbcTemplate.update(
                "UPDATE code_detail SET CODE_NAME = ?, IS_ACTIVE = 1 WHERE GROUP_ID = ? AND CODE = ?",
                name, groupId, loginId);
        if (updated == 0) {
            int nextSort = nextSortOrder(groupId);
            jdbcTemplate.update(
                    "INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, IS_ACTIVE, SORT_ORDER) " +
                            "VALUES (?, ?, ?, 1, ?)",
                    groupId, loginId, name, nextSort);
            log.info("[CCKB] CCKB_PERM 관리자 추가: loginId={}, name={}", loginId, name);
        } else {
            log.info("[CCKB] CCKB_PERM 관리자 재활성화: loginId={}, name={}", loginId, name);
        }
        cckbPermission.invalidateCache();
        return listAdmins();
    }

    /** 관리자 비활성화(명단에서 제외). 최소 1명은 남겨둘 필요는 없음 — 플랫폼 ROLE_ADMIN 이 항상 우회 가능하기 때문. */
    @Transactional
    public List<CckbAdminResponse> removeAdmin(String loginIdRaw) {
        String loginId = loginIdRaw == null ? "" : loginIdRaw.trim();
        if (loginId.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "로그인 ID를 입력해 주세요.");
        }
        Long groupId = ensureGroupId();
        jdbcTemplate.update(
                "UPDATE code_detail SET IS_ACTIVE = 0 WHERE GROUP_ID = ? AND CODE = ?",
                groupId, loginId);
        log.info("[CCKB] CCKB_PERM 관리자 비활성화: loginId={}", loginId);
        cckbPermission.invalidateCache();
        return listAdmins();
    }

    /**
     * 관리자 추가 모달의 "사용자 검색" 드롭다운용 — AI플랫폼 전체 활성 사용자 목록.
     * (module-dailyreport의 UserListService와 동일 패턴: core_user를 직접 조회)
     */
    public List<CckbUserOptionResponse> listPlatformUsers() {
        return jdbcTemplate.query(
                "SELECT USER_ID, LOGIN_ID, USER_NAME FROM core_user WHERE ENABLED = 1 ORDER BY USER_NAME",
                (rs, rowNum) -> CckbUserOptionResponse.builder()
                        .userId(rs.getLong("USER_ID"))
                        .loginId(rs.getString("LOGIN_ID"))
                        .userName(rs.getString("USER_NAME"))
                        .build());
    }

    private int nextSortOrder(Long groupId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(SORT_ORDER), 0) FROM code_detail WHERE GROUP_ID = ?",
                Integer.class, groupId);
        return (max == null ? 0 : max) + 10;
    }
}
