package com.company.core.permission.repository;

import com.company.core.permission.entity.CoreRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CoreRolePermissionRepository extends JpaRepository<CoreRolePermission, Long> {
    List<CoreRolePermission> findByRole(String role);

    @Modifying
    @Query("DELETE FROM CoreRolePermission r WHERE r.role = :role")
    void deleteByRole(String role);

    @Modifying
    @Query("DELETE FROM CoreRolePermission r WHERE r.role = :role AND r.permId = :permId")
    void deleteByRoleAndPermId(String role, Long permId);
}
