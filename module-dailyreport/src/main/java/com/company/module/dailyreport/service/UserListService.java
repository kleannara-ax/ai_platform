package com.company.module.dailyreport.service;

import com.company.module.dailyreport.dto.UserSimpleResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 사용자 목록 조회 서비스
 * - Architecture Rule #4: core 모듈 Entity를 직접 import하지 않고
 *   EntityManager native query로 core_user 테이블 조회
 * - MenuPermissionService의 isAdmin() 패턴과 동일한 접근 방식
 */
@Service
@Transactional(readOnly = true)
public class UserListService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 활성 사용자 전체 목록 조회
     * - core_user.enabled = 1 인 사용자만 반환
     * - user_profile LEFT JOIN으로 부서(dept_code)·직위(position) 조회
     * - mod_user_department LEFT JOIN으로 부서명(DEPT_NAME) 조회
     * - user_name 기준 정렬
     *
     * V2.0.0 이후 컬럼명:
     *   core_user        : user_id, login_id, user_name, enabled (lowercase)
     *   user_profile     : user_id, dept_code(VARCHAR), position (lowercase)
     *   mod_user_department : DEPT_CODE, DEPT_NAME (uppercase — V2 미변경)
     */
    @SuppressWarnings("unchecked")
    public List<UserSimpleResponse> getActiveUsers() {
        List<Object[]> rows = entityManager
                .createNativeQuery(
                        "SELECT u.user_id, u.login_id, u.user_name, " +
                        "       COALESCE(d.DEPT_NAME, p.dept_code, '') AS department, " +
                        "       COALESCE(p.position, '') AS position " +
                        "FROM core_user u " +
                        "LEFT JOIN user_profile p ON u.user_id = p.user_id " +
                        "LEFT JOIN mod_user_department d ON p.dept_code = d.DEPT_CODE " +
                        "WHERE u.enabled = 1 " +
                        "ORDER BY u.user_name")
                .getResultList();

        return rows.stream()
                .map(row -> UserSimpleResponse.builder()
                        .userId(((Number) row[0]).longValue())
                        .loginId((String) row[1])
                        .userName((String) row[2])
                        .department(row[3] != null ? (String) row[3] : "")
                        .position(row[4] != null ? (String) row[4] : "")
                        .build())
                .collect(Collectors.toList());
    }
}
