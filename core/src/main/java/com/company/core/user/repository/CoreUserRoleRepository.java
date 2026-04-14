package com.company.core.user.repository;

import com.company.core.user.entity.CoreUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 사용자-역할 매핑 Repository (다중 역할 지원)
 */
@Repository
public interface CoreUserRoleRepository extends JpaRepository<CoreUserRole, Long> {

    List<CoreUserRole> findByUserId(Long userId);

    @Query("SELECT ur.role FROM CoreUserRole ur WHERE ur.userId = :userId")
    List<String> findRolesByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM CoreUserRole ur WHERE ur.userId = :userId")
    void deleteByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM CoreUserRole ur WHERE ur.userId = :userId AND ur.role = :role")
    void deleteByUserIdAndRole(Long userId, String role);

    boolean existsByUserIdAndRole(Long userId, String role);
}
