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
     * - IS_ACTIVE = true인 사용자만 반환
     * - USER_NAME 기준 정렬
     */
    @SuppressWarnings("unchecked")
    public List<UserSimpleResponse> getActiveUsers() {
        List<Object[]> rows = entityManager
                .createNativeQuery(
                        "SELECT USER_ID, LOGIN_ID, USER_NAME, DEPARTMENT, POSITION " +
                        "FROM core_user " +
                        "WHERE IS_ACTIVE = true " +
                        "ORDER BY USER_NAME")
                .getResultList();

        return rows.stream()
                .map(row -> UserSimpleResponse.builder()
                        .userId(((Number) row[0]).longValue())
                        .loginId((String) row[1])
                        .userName((String) row[2])
                        .department((String) row[3])
                        .position((String) row[4])
                        .build())
                .collect(Collectors.toList());
    }
}
