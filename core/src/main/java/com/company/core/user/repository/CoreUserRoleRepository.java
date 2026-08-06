package com.company.core.user.repository;

import com.company.core.user.entity.CoreUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자-역할 매핑 Repository (다중 역할 지원)
 */
@Repository
public interface CoreUserRoleRepository extends JpaRepository<CoreUserRole, Long> {

    List<CoreUserRole> findByUserId(Long userId);

    List<CoreUserRole> findByUserIdIn(Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM CoreUserRole r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 사용자별 역할 코드 목록 조회 (userId → List<role>)
     */
    default Map<Long, List<String>> findRoleCodesByUserIds(Collection<Long> userIds) {
        Map<Long, List<String>> result = new java.util.LinkedHashMap<>();
        for (CoreUserRole ur : findByUserIdIn(userIds)) {
            result.computeIfAbsent(ur.getUserId(), k -> new java.util.ArrayList<>()).add(ur.getRole());
        }
        return result;
    }

    /**
     * 특정 사용자가 특정 역할을 보유하는지 확인
     */
    boolean existsByUserIdAndRole(Long userId, String role);

    /**
     * 특정 역할을 가진 사용자 ID 목록
     */
    @Query("SELECT r.userId FROM CoreUserRole r WHERE r.role = :role")
    Set<Long> findUserIdsByRole(@Param("role") String role);
}
